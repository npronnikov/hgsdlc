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
        if (model.getCodingAgent() == null || model.getCodingAgent().isBlank()) {
            throw new ValidationException("coding_agent is required in flow_yaml");
        }

        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());

        List<String> validationErrors = flowValidator.validate(model);
        if (!validationErrors.isEmpty()) {
            throw new ValidationException(validationErrors.getFirst());
        }
        if (publish) {
            validateReferences(model);
        }

        FlowVersion existingDraft = repository.findFirstByFlowIdAndStatusOrderBySavedAtDesc(flowId, FlowStatus.DRAFT)
                .orElse(null);
        FlowVersion latestPublished = repository
                .findFirstByFlowIdAndStatusOrderBySavedAtDesc(flowId, FlowStatus.PUBLISHED)
                .orElse(null);
        if (publish) {
            FlowVersion base = existingDraft != null ? existingDraft : latestPublished;
            if (base != null) {
                if (base.getStatus() == FlowStatus.PUBLISHED
                        && base.getResourceVersion() != request.resourceVersion()) {
                    throw new ConflictException("resource_version mismatch for publish");
                }
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
                ? resolvePublishVersion(existingDraft, latestPublished, release)
                : resolveDraftVersion(existingDraft, latestPublished);
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

    private void validateReferences(FlowModel model) {
        List<String> brokenRules = new ArrayList<>();
        List<String> brokenSkills = new ArrayList<>();
        for (String ruleRef : model.getRuleRefs()) {
            if (ruleRef == null || ruleRef.isBlank()) {
                continue;
            }
            boolean exists = ruleRepository.findFirstByCanonicalNameAndStatus(ruleRef, RuleStatus.PUBLISHED).isPresent();
            if (!exists) {
                brokenRules.add(ruleRef);
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
                boolean exists = skillRepository.findFirstByCanonicalNameAndStatus(skillRef, SkillStatus.PUBLISHED).isPresent();
                if (!exists) {
                    brokenSkills.add(skillRef);
                }
            }
        }
        if (!brokenRules.isEmpty()) {
            throw new ValidationException("Referenced rules not published: " + String.join(", ", brokenRules));
        }
        if (!brokenSkills.isEmpty()) {
            throw new ValidationException("Referenced skills not published: " + String.join(", ", brokenSkills));
        }
    }

    private String resolveDraftVersion(FlowVersion existingDraft, FlowVersion latestPublished) {
        if (existingDraft != null) {
            return existingDraft.getVersion();
        }
        if (latestPublished == null) {
            return INITIAL_VERSION;
        }
        return nextMinor(latestPublished.getVersion());
    }

    private String resolvePublishVersion(FlowVersion existingDraft, FlowVersion latestPublished, boolean release) {
        String baseVersion = existingDraft != null ? existingDraft.getVersion()
                : (latestPublished == null ? INITIAL_VERSION : nextMinor(latestPublished.getVersion()));
        return release ? releaseVersion(baseVersion) : baseVersion;
    }

    private void bumpDraftVersion(FlowVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getFlowId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.saveAndFlush(draft);
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

    private String nextMinor(String version) {
        int[] parts = parseVersion(version);
        return parts[0] + "." + (parts[1] + 1);
    }

    private String releaseVersion(String version) {
        int[] parts = parseVersion(version);
        return (parts[0] + 1) + ".0";
    }
}
