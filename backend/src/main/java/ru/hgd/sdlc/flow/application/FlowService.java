package ru.hgd.sdlc.flow.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.api.FlowSaveRequest;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class FlowService {
    private static final String INITIAL_VERSION = "0.1";
    private static final Pattern FLOW_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]*$");

    private final FlowVersionRepository repository;
    private final RuleVersionRepository ruleRepository;
    private final SkillVersionRepository skillRepository;
    private final FlowYamlParser flowYamlParser;
    private final FlowValidator flowValidator;

    public FlowService(
            FlowVersionRepository repository,
            RuleVersionRepository ruleRepository,
            SkillVersionRepository skillRepository,
            FlowYamlParser flowYamlParser,
            FlowValidator flowValidator
    ) {
        this.repository = repository;
        this.ruleRepository = ruleRepository;
        this.skillRepository = skillRepository;
        this.flowYamlParser = flowYamlParser;
        this.flowValidator = flowValidator;
    }

    @Transactional(readOnly = true)
    public List<FlowVersion> listLatest() {
        List<FlowVersion> all = repository.findAllByOrderBySavedAtDesc();
        Map<String, FlowVersion> latestPublished = new LinkedHashMap<>();
        Map<String, FlowVersion> latestDraft = new LinkedHashMap<>();
        for (FlowVersion version : all) {
            if (version.getStatus() == FlowStatus.PUBLISHED) {
                latestPublished.putIfAbsent(version.getFlowId(), version);
            } else {
                latestDraft.putIfAbsent(version.getFlowId(), version);
            }
        }
        Map<String, FlowVersion> latestByFlow = new LinkedHashMap<>();
        for (FlowVersion version : all) {
            if (latestByFlow.containsKey(version.getFlowId())) {
                continue;
            }
            FlowVersion published = latestPublished.get(version.getFlowId());
            latestByFlow.put(version.getFlowId(), published != null ? published : latestDraft.get(version.getFlowId()));
        }
        return new ArrayList<>(latestByFlow.values());
    }

    @Transactional(readOnly = true)
    public FlowVersion getLatest(String flowId) {
        return repository.findFirstByFlowIdAndStatusOrderBySavedAtDesc(flowId, FlowStatus.PUBLISHED)
                .or(() -> repository.findFirstByFlowIdAndStatusOrderBySavedAtDesc(flowId, FlowStatus.DRAFT))
                .orElseThrow(() -> new NotFoundException("Flow not found: " + flowId));
    }

    @Transactional(readOnly = true)
    public List<FlowVersion> getVersions(String flowId) {
        List<FlowVersion> versions = repository.findByFlowIdOrderBySavedAtDesc(flowId);
        if (versions.isEmpty()) {
            throw new NotFoundException("Flow not found: " + flowId);
        }
        return versions;
    }

    @Transactional(readOnly = true)
    public FlowVersion getVersion(String flowId, String version) {
        return repository.findFirstByFlowIdAndVersionOrderBySavedAtDesc(flowId, version)
                .orElseThrow(() -> new NotFoundException("Flow version not found: " + flowId + "@" + version));
    }

    @Transactional
    public FlowVersion save(String flowId, FlowSaveRequest request, User user) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.flowYaml() == null || request.flowYaml().isBlank()) {
            throw new ValidationException("flow_yaml is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        if (request.flowId() == null || request.flowId().isBlank()) {
            throw new ValidationException("flow_id is required");
        }
        if (!FLOW_ID_PATTERN.matcher(request.flowId()).matches()) {
            throw new ValidationException("flow_id has invalid format");
        }
        if (!flowId.equals(request.flowId())) {
            throw new ValidationException("Path flowId does not match request flow_id");
        }

        FlowModel model = flowYamlParser.parse(request.flowYaml());
        if (model.getId() == null || model.getId().isBlank()) {
            throw new ValidationException("flow id is required in flow_yaml");
        }
        if (!flowId.equals(model.getId())) {
            throw new ValidationException("flow_yaml id does not match path flowId");
        }
        if (model.getTitle() == null || model.getTitle().isBlank()) {
            throw new ValidationException("title is required in flow_yaml");
        }
        if (model.getStartNodeId() == null || model.getStartNodeId().isBlank()) {
            throw new ValidationException("start_node_id is required in flow_yaml");
        }
        String codingAgent = normalizeCodingAgent(request.codingAgent());
        if (codingAgent == null) {
            throw new ValidationException("coding_agent is required");
        }
        model.setCodingAgent(codingAgent);

        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());

        List<String> validationErrors = flowValidator.validate(model);
        if (!validationErrors.isEmpty()) {
            throw new ValidationException(validationErrors.getFirst());
        }
        if (publish) {
            validateReferences(model, codingAgent);
        }

        List<FlowVersion> allVersions = repository.findByFlowIdOrderBySavedAtDesc(flowId);
        List<FlowVersion> publishedVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == FlowStatus.PUBLISHED)
                .toList();
        List<FlowVersion> draftVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == FlowStatus.DRAFT)
                .toList();
        int baseMajor = resolveBaseMajor(request.baseVersion(), publishedVersions);
        FlowVersion existingDraft = findDraftForMajor(draftVersions, baseMajor);
        FlowVersion latestPublishedForMajor = findPublishedForMajor(publishedVersions, baseMajor);
        Integer maxPublishedMajor = findMaxPublishedMajor(publishedVersions);
        Integer maxMinorForMajor = findMaxPublishedMinorForMajor(publishedVersions, baseMajor);
        if (publish) {
            FlowVersion base = existingDraft != null ? existingDraft : latestPublishedForMajor;
            if (base != null && base.getResourceVersion() != request.resourceVersion()) {
                throw new ConflictException("resource_version mismatch for publish");
            }
        } else {
            if (existingDraft != null) {
                if (existingDraft.getResourceVersion() != request.resourceVersion()) {
                    throw new ConflictException("resource_version mismatch for draft");
                }
            } else if (request.resourceVersion() != 0L) {
                throw new ConflictException("resource_version mismatch for draft");
            }
        }

        String version = publish
                ? resolvePublishVersion(existingDraft, maxMinorForMajor, maxPublishedMajor, release, baseMajor)
                : resolveDraftVersion(existingDraft, maxMinorForMajor, baseMajor, publishedVersions.isEmpty());
        String canonicalName = flowId + "@" + version;
        String updatedYaml = request.flowYaml();

        boolean bumpDraftBeforeInsert = publish
                && existingDraft != null
                && existingDraft.getVersion().equals(version);
        if (bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        FlowVersion entity;
        if (!publish && existingDraft != null) {
            entity = existingDraft;
        } else {
            entity = new FlowVersion();
            entity.setId(UUID.randomUUID());
            entity.setResourceVersion(0L);
        }

        entity.setFlowId(flowId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(publish ? FlowStatus.PUBLISHED : FlowStatus.DRAFT);
        entity.setTitle(model.getTitle().trim());
        entity.setDescription(model.getDescription());
        entity.setStartNodeId(model.getStartNodeId().trim());
        entity.setRuleRefs(model.getRuleRefs());
        entity.setCodingAgent(codingAgent);
        entity.setFlowYaml(updatedYaml);
        entity.setChecksum(publish ? ChecksumUtil.sha256(updatedYaml) : null);
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(Instant.now());

        FlowVersion saved = repository.save(entity);

        if (publish && existingDraft != null && !bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        return saved;
    }

    private String normalizeCodingAgent(String codingAgent) {
        if (codingAgent == null || codingAgent.isBlank()) {
            return null;
        }
        return codingAgent.trim().toLowerCase().replace('-', '_');
    }

    private void validateReferences(FlowModel model, String codingAgent) {
        List<String> brokenRules = new ArrayList<>();
        List<String> brokenSkills = new ArrayList<>();
        List<String> mismatchedRules = new ArrayList<>();
        List<String> mismatchedSkills = new ArrayList<>();
        for (String ruleRef : model.getRuleRefs()) {
            if (ruleRef == null || ruleRef.isBlank()) {
                continue;
            }
            var rule = ruleRepository.findFirstByCanonicalNameAndStatus(ruleRef, RuleStatus.PUBLISHED).orElse(null);
            if (rule == null) {
                brokenRules.add(ruleRef);
                continue;
            }
            if (!codingAgent.equals(rule.getCodingAgent().name().toLowerCase())) {
                mismatchedRules.add(ruleRef);
            }
        }
        for (NodeModel node : model.getNodes()) {
            if (node.getSkillRefs() == null || node.getSkillRefs().isEmpty()) {
                continue;
            }
            for (String skillRef : node.getSkillRefs()) {
                if (skillRef == null || skillRef.isBlank()) {
                    continue;
                }
                var skill = skillRepository.findFirstByCanonicalNameAndStatus(skillRef, SkillStatus.PUBLISHED).orElse(null);
                if (skill == null) {
                    brokenSkills.add(skillRef);
                    continue;
                }
                if (!codingAgent.equals(skill.getCodingAgent().name().toLowerCase())) {
                    mismatchedSkills.add(skillRef);
                }
            }
        }
        if (!brokenRules.isEmpty()) {
            throw new ValidationException("Referenced rules not published: " + String.join(", ", brokenRules));
        }
        if (!brokenSkills.isEmpty()) {
            throw new ValidationException("Referenced skills not published: " + String.join(", ", brokenSkills));
        }
        if (!mismatchedRules.isEmpty()) {
            throw new ValidationException("Referenced rules mismatch coding_agent: " + String.join(", ", mismatchedRules));
        }
        if (!mismatchedSkills.isEmpty()) {
            throw new ValidationException("Referenced skills mismatch coding_agent: " + String.join(", ", mismatchedSkills));
        }
    }

    private String resolveDraftVersion(
            FlowVersion existingDraft,
            Integer maxMinorForMajor,
            int baseMajor,
            boolean publishedEmpty
    ) {
        if (existingDraft != null) {
            return existingDraft.getVersion();
        }
        if (maxMinorForMajor != null) {
            return baseMajor + "." + (maxMinorForMajor + 1);
        }
        if (publishedEmpty && baseMajor == parseVersion(INITIAL_VERSION)[0]) {
            return INITIAL_VERSION;
        }
        return baseMajor + ".0";
    }

    private String resolvePublishVersion(
            FlowVersion existingDraft,
            Integer maxMinorForMajor,
            Integer maxPublishedMajor,
            boolean release,
            int baseMajor
    ) {
        if (release) {
            int nextMajor = maxPublishedMajor == null ? 1 : maxPublishedMajor + 1;
            return nextMajor + ".0";
        }
        if (existingDraft != null) {
            return existingDraft.getVersion();
        }
        if (maxMinorForMajor != null) {
            return baseMajor + "." + (maxMinorForMajor + 1);
        }
        if (maxPublishedMajor == null && baseMajor == parseVersion(INITIAL_VERSION)[0]) {
            return INITIAL_VERSION;
        }
        return baseMajor + ".0";
    }

    private void bumpDraftVersion(FlowVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getFlowId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.saveAndFlush(draft);
    }

    private int resolveBaseMajor(String baseVersion, List<FlowVersion> publishedVersions) {
        if (baseVersion != null && !baseVersion.isBlank()) {
            int[] parsed = parseVersionSafe(baseVersion);
            if (parsed != null) {
                return parsed[0];
            }
        }
        Integer maxMajor = findMaxPublishedMajor(publishedVersions);
        if (maxMajor != null) {
            return maxMajor;
        }
        return parseVersion(INITIAL_VERSION)[0];
    }

    private FlowVersion findDraftForMajor(List<FlowVersion> drafts, int major) {
        for (FlowVersion draft : drafts) {
            int[] parsed = parseVersionSafe(draft.getVersion());
            if (parsed != null && parsed[0] == major) {
                return draft;
            }
        }
        return null;
    }

    private FlowVersion findPublishedForMajor(List<FlowVersion> published, int major) {
        for (FlowVersion version : published) {
            int[] parsed = parseVersionSafe(version.getVersion());
            if (parsed != null && parsed[0] == major) {
                return version;
            }
        }
        return null;
    }

    private Integer findMaxPublishedMajor(List<FlowVersion> published) {
        Integer maxMajor = null;
        for (FlowVersion version : published) {
            int[] parsed = parseVersionSafe(version.getVersion());
            if (parsed == null) {
                continue;
            }
            if (maxMajor == null || parsed[0] > maxMajor) {
                maxMajor = parsed[0];
            }
        }
        return maxMajor;
    }

    private Integer findMaxPublishedMinorForMajor(List<FlowVersion> published, int major) {
        Integer maxMinor = null;
        for (FlowVersion version : published) {
            int[] parsed = parseVersionSafe(version.getVersion());
            if (parsed == null || parsed[0] != major) {
                continue;
            }
            if (maxMinor == null || parsed[1] > maxMinor) {
                maxMinor = parsed[1];
            }
        }
        return maxMinor;
    }

    private String resolveSavedBy(User user) {
        if (user == null || user.getUsername() == null) {
            throw new ValidationException("Authenticated user is required");
        }
        return user.getUsername();
    }

    private int[] parseVersion(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            throw new ValidationException("Invalid version: " + version);
        }
        String[] parts = version.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private int[] parseVersionSafe(String version) {
        try {
            return parseVersion(version);
        } catch (ValidationException ex) {
            return null;
        }
    }

    private String nextMinor(String version) {
        int[] parts = parseVersion(version);
        return parts[0] + "." + (parts[1] + 1);
    }

    private String releaseVersion(String version) {
        int[] parts = parseVersion(version);
        return (parts[0] + 1) + ".0";
    }
}
