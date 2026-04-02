package ru.hgd.sdlc.settings.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowApprovalStatus;
import ru.hgd.sdlc.flow.domain.FlowLifecycleStatus;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.rule.domain.RuleApprovalStatus;
import ru.hgd.sdlc.rule.domain.RuleLifecycleStatus;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.skill.application.SkillPackageService;
import ru.hgd.sdlc.skill.domain.SkillApprovalStatus;
import ru.hgd.sdlc.skill.domain.SkillFileEntity;
import ru.hgd.sdlc.skill.domain.SkillFileRole;
import ru.hgd.sdlc.skill.domain.SkillLifecycleStatus;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillFileRepository;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class CatalogUpsertService {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> ALLOWED_PLATFORM_CODES = List.of("FRONT", "BACK", "DATA");
    private static final List<String> ALLOWED_SKILL_KINDS = List.of("analysis", "code", "review", "refactor", "qa", "ops");
    private static final List<String> ALLOWED_RULE_KINDS = List.of("architecture", "coding-style", "security");
    private static final List<String> ALLOWED_FLOW_KINDS = List.of("analysis", "code", "delivery", "full-cycle");

    private final RuleVersionRepository ruleVersionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final SkillPackageService skillPackageService;
    private final CatalogValidationService validationService;

    public CatalogUpsertService(
            RuleVersionRepository ruleVersionRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            FlowVersionRepository flowVersionRepository,
            SkillPackageService skillPackageService,
            CatalogValidationService validationService
    ) {
        this.ruleVersionRepository = ruleVersionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.skillPackageService = skillPackageService;
        this.validationService = validationService;
    }

    @Transactional
    public UpsertOutcome upsertRule(ParsedMetadata metadata, String actorId) {
        validationService.validateRule(metadata);
        RuleProvider provider = RuleProvider.from(metadata.require("coding_agent"));
        Optional<RuleVersion> existingOptional = ruleVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            RuleVersion existing = existingOptional.get();
            if (existing.getStatus() == RuleStatus.PUBLISHED) {
                String incomingChecksum = withShaPrefix(metadata.checksum());
                if (incomingChecksum.equals(existing.getChecksum())) {
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published rule already exists with different checksum: " + metadata.canonicalName());
            }
            applyRuleFields(existing, metadata, provider, actorId);
            ruleVersionRepository.save(existing);
            return UpsertOutcome.updatedOne();
        }
        RuleVersion created = RuleVersion.builder().id(UUID.randomUUID()).build();
        applyRuleFields(created, metadata, provider, actorId);
        ruleVersionRepository.save(created);
        return UpsertOutcome.insertedOne();
    }

    @Transactional
    public UpsertOutcome upsertSkill(ParsedMetadata metadata, String actorId) {
        validationService.validateSkill(metadata);
        SkillProvider provider = SkillProvider.from(metadata.require("coding_agent"));
        Optional<SkillVersion> existingOptional = skillVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            SkillVersion existing = existingOptional.get();
            if (existing.getStatus() == SkillStatus.PUBLISHED) {
                String incomingChecksum = metadata.checksum();
                String existingChecksum = normalizeChecksum(existing.getChecksum());
                if (incomingChecksum.equals(existingChecksum)) {
                    replaceSkillFilesFromCatalog(existing, metadata.skillPackageFiles());
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published skill already exists with different checksum: " + metadata.canonicalName());
            }
            applySkillFields(existing, metadata, provider, actorId);
            SkillVersion saved = skillVersionRepository.save(existing);
            replaceSkillFilesFromCatalog(saved, metadata.skillPackageFiles());
            return UpsertOutcome.updatedOne();
        }
        SkillVersion created = SkillVersion.builder().id(UUID.randomUUID()).build();
        applySkillFields(created, metadata, provider, actorId);
        SkillVersion saved = skillVersionRepository.save(created);
        replaceSkillFilesFromCatalog(saved, metadata.skillPackageFiles());
        return UpsertOutcome.insertedOne();
    }

    @Transactional
    public UpsertOutcome upsertFlow(ParsedMetadata metadata, String actorId) {
        validationService.validateFlow(metadata);
        Map<String, Object> flowDoc = parseYamlMap(metadata.content(), "Flow yaml is not valid");
        String startNodeId = stringValue(flowDoc.get("start_node_id"));
        if (startNodeId == null || startNodeId.isBlank()) {
            throw new ValidationException("Flow yaml missing start_node_id");
        }
        List<String> ruleRefs = parseStringList(flowDoc.get("rule_refs"));
        Optional<FlowVersion> existingOptional = flowVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            FlowVersion existing = existingOptional.get();
            if (existing.getStatus() == FlowStatus.PUBLISHED) {
                String incomingChecksum = withShaPrefix(metadata.checksum());
                if (incomingChecksum.equals(existing.getChecksum())) {
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published flow already exists with different checksum: " + metadata.canonicalName());
            }
            applyFlowFields(existing, metadata, startNodeId.trim(), ruleRefs, actorId);
            flowVersionRepository.save(existing);
            return UpsertOutcome.updatedOne();
        }
        FlowVersion created = FlowVersion.builder().id(UUID.randomUUID()).build();
        applyFlowFields(created, metadata, startNodeId.trim(), ruleRefs, actorId);
        flowVersionRepository.save(created);
        return UpsertOutcome.insertedOne();
    }

    private void applyRuleFields(RuleVersion target, ParsedMetadata metadata, RuleProvider provider, String actorId) {
        target.setRuleId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(RuleStatus.PUBLISHED);
        target.setTitle(metadata.displayName());
        target.setDescription(metadata.optional("description"));
        target.setCodingAgent(provider);
        target.setRuleMarkdown(metadata.content());
        target.setChecksum(withShaPrefix(metadata.checksum()));
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(parsePlatformCode(metadata.require("platform_code")));
        target.setTags(metadata.tags());
        target.setRuleKind(parseRuleKind(metadata.optional("rule_kind")));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setApprovalStatus(RuleApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        target.setLifecycleStatus(parseRuleLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private void applySkillFields(SkillVersion target, ParsedMetadata metadata, SkillProvider provider, String actorId) {
        target.setSkillId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(SkillStatus.PUBLISHED);
        target.setName(metadata.displayName());
        target.setDescription(metadata.optional("description") == null ? "" : metadata.optional("description"));
        target.setCodingAgent(provider);
        target.setSkillMarkdown(metadata.content());
        target.setChecksum(metadata.checksum());
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(parsePlatformCode(metadata.require("platform_code")));
        target.setTags(metadata.tags());
        target.setSkillKind(parseSkillKind(metadata.optional("skill_kind")));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setApprovalStatus(SkillApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        target.setLifecycleStatus(parseSkillLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private void applyFlowFields(FlowVersion target, ParsedMetadata metadata, String startNodeId, List<String> ruleRefs, String actorId) {
        target.setFlowId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(FlowStatus.PUBLISHED);
        target.setTitle(metadata.displayName());
        target.setDescription(metadata.optional("description"));
        target.setStartNodeId(startNodeId);
        target.setRuleRefs(ruleRefs);
        target.setCodingAgent(metadata.require("coding_agent"));
        target.setFlowYaml(metadata.content());
        target.setChecksum(withShaPrefix(metadata.checksum()));
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(parsePlatformCode(metadata.require("platform_code")));
        target.setTags(metadata.tags());
        target.setFlowKind(parseFlowKind(metadata.optional("flow_kind")));
        target.setRiskLevel(metadata.optional("risk_level"));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setApprovalStatus(FlowApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        target.setLifecycleStatus(parseFlowLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private void replaceSkillFilesFromCatalog(SkillVersion skillVersion, List<ParsedSkillPackageFile> files) {
        List<ParsedSkillPackageFile> effectiveFiles = files == null ? List.of() : files;
        if (effectiveFiles.isEmpty()) {
            String markdown = skillPackageService.normalizeText(skillVersion.getSkillMarkdown());
            effectiveFiles = List.of(new ParsedSkillPackageFile(
                    "SKILL.md",
                    SkillFileRole.INSTRUCTION,
                    "text/markdown",
                    false,
                    markdown,
                    markdown.getBytes(StandardCharsets.UTF_8).length
            ));
        }
        skillFileRepository.deleteBySkillVersionId(skillVersion.getId());
        skillFileRepository.flush();
        Instant now = Instant.now();
        List<SkillFileEntity> entities = effectiveFiles.stream()
                .map(file -> SkillFileEntity.builder()
                        .id(UUID.randomUUID())
                        .skillVersionId(skillVersion.getId())
                        .path(file.path())
                        .role(file.role())
                        .mediaType(file.mediaType())
                        .executable(file.executable())
                        .textContent(skillPackageService.normalizeText(file.content()))
                        .sizeBytes(file.sizeBytes())
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        skillFileRepository.saveAll(entities);
    }

    // ---- Field parsers ----

    private SkillLifecycleStatus parseSkillLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return SkillLifecycleStatus.ACTIVE;
        }
        return SkillLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private RuleLifecycleStatus parseRuleLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return RuleLifecycleStatus.ACTIVE;
        }
        return RuleLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private FlowLifecycleStatus parseFlowLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return FlowLifecycleStatus.ACTIVE;
        }
        return FlowLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private String parsePlatformCode(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ValidationException("metadata field is required: platform_code");
        }
        String platformCode = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_PLATFORM_CODES.contains(platformCode)) {
            throw new ValidationException("Unsupported platform_code: " + value);
        }
        return platformCode;
    }

    private String parseSkillKind(String value) {
        return parseKind(value, ALLOWED_SKILL_KINDS, "skill_kind");
    }

    private String parseRuleKind(String value) {
        return parseKind(value, ALLOWED_RULE_KINDS, "rule_kind");
    }

    private String parseFlowKind(String value) {
        return parseKind(value, ALLOWED_FLOW_KINDS, "flow_kind");
    }

    private String parseKind(String value, List<String> allowed, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        String kind = normalized.toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!allowed.contains(kind)) {
            throw new ValidationException("Unsupported " + fieldName + ": " + value);
        }
        return kind;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value.trim());
    }

    private String parseCatalogScope(String rawScope, String entityId) {
        String normalized = normalizeOptional(rawScope);
        if (normalized != null) {
            String value = normalized.toLowerCase(Locale.ROOT);
            if ("team".equals(value) || "organization".equals(value)) {
                return value;
            }
        }
        if (entityId != null && entityId.startsWith("team-")) {
            return "team";
        }
        return "organization";
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String withShaPrefix(String checksumWithoutPrefix) {
        return "sha256:" + checksumWithoutPrefix;
    }

    private String normalizeChecksum(String rawChecksum) {
        if (rawChecksum == null || rawChecksum.isBlank()) {
            return null;
        }
        String value = rawChecksum.trim();
        if (value.startsWith("sha256:")) {
            value = value.substring("sha256:".length());
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parseYamlMap(String yaml, String errorMessage) {
        try {
            return YAML.readValue(yaml, MAP_TYPE);
        } catch (IOException ex) {
            throw new ValidationException(errorMessage + ": " + ex.getMessage());
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private List<String> parseStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String value = stringValue(item);
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
            return out;
        }
        String value = stringValue(rawValue);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
    }
}
