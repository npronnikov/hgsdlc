package ru.hgd.sdlc.rule.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.JsonSchemaValidator;
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.SemverUtil;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.rule.api.RuleSaveRequest;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;

@Service
public class RuleService {
    private static final String SCHEMA_PATH = "schemas/rule.schema.json";
    private static final String DRAFT_VERSION = "0.0.0";

    private final RuleVersionRepository repository;
    private final JsonSchemaValidator schemaValidator;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final ObjectMapper objectMapper;

    public RuleService(
            RuleVersionRepository repository,
            JsonSchemaValidator schemaValidator,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.schemaValidator = schemaValidator;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RuleVersion> listLatest() {
        List<RuleVersion> all = repository.findAllByOrderBySavedAtDesc();
        Map<String, RuleVersion> latestByRule = new LinkedHashMap<>();
        for (RuleVersion version : all) {
            latestByRule.putIfAbsent(version.getRuleId(), version);
        }
        return new ArrayList<>(latestByRule.values());
    }

    @Transactional(readOnly = true)
    public RuleVersion getLatest(String ruleId) {
        return repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.DRAFT)
                .or(() -> repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.PUBLISHED))
                .orElseThrow(() -> new NotFoundException("Rule not found: " + ruleId));
    }

    @Transactional(readOnly = true)
    public List<RuleVersion> getVersions(String ruleId) {
        List<RuleVersion> versions = repository.findByRuleIdOrderBySavedAtDesc(ruleId);
        if (versions.isEmpty()) {
            throw new NotFoundException("Rule not found: " + ruleId);
        }
        return versions;
    }

    @Transactional
    public RuleVersion save(String ruleId, RuleSaveRequest request, User user) {
        if (request == null || request.ruleMarkdown() == null) {
            throw new ValidationException("rule_markdown is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        boolean publish = Boolean.TRUE.equals(request.publish());
        MarkdownFrontmatterParser.ParsedMarkdown parsed = frontmatterParser.parse(request.ruleMarkdown());
        ObjectNode frontmatter = parsed.frontmatter();
        String id = textValue(frontmatter, "id");
        if (id == null || id.isBlank()) {
            throw new ValidationException("Frontmatter id is required");
        }
        if (!ruleId.equals(id)) {
            throw new ValidationException("Path ruleId does not match frontmatter id");
        }

        RuleVersion existingDraft = repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.DRAFT)
                .orElse(null);
        RuleVersion latestPublished = repository
                .findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.PUBLISHED)
                .orElse(null);
        if (publish) {
            RuleVersion base = existingDraft != null ? existingDraft : latestPublished;
            if (base != null) {
                if (base.getResourceVersion() != request.resourceVersion()) {
                    throw new ConflictException("resource_version mismatch for publish");
                }
            } else if (request.resourceVersion() != 0L) {
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

        String version = publish ? nextPublishedVersion(ruleId) : DRAFT_VERSION;
        String canonicalName = ruleId + "@" + version;
        frontmatter.put("id", ruleId);
        frontmatter.put("version", version);
        frontmatter.put("canonical_name", canonicalName);

        schemaValidator.validate(frontmatter, SCHEMA_PATH);

        String updatedMarkdown = frontmatterParser.render(frontmatter, parsed.body());
        ObjectNode modelJson = objectMapper.createObjectNode();
        modelJson.set("frontmatter", frontmatter);
        modelJson.put("body", parsed.body() == null ? "" : parsed.body());

        RuleVersion entity;
        if (!publish && existingDraft != null) {
            entity = existingDraft;
        } else {
            entity = new RuleVersion();
            entity.setId(UUID.randomUUID());
            entity.setResourceVersion(0L);
        }

        entity.setRuleId(ruleId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(publish ? RuleStatus.PUBLISHED : RuleStatus.DRAFT);
        entity.setRuleMarkdown(updatedMarkdown);
        entity.setRuleModelJson(modelJson);
        entity.setRuleChecksum(ChecksumUtil.sha256(updatedMarkdown));
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(Instant.now());

        return repository.save(entity);
    }

    private String nextPublishedVersion(String ruleId) {
        RuleVersion latestPublished = repository
                .findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.PUBLISHED)
                .orElse(null);
        if (latestPublished == null) {
            return SemverUtil.initial();
        }
        return SemverUtil.incrementPatch(latestPublished.getVersion());
    }

    private String resolveSavedBy(User user) {
        if (user == null || user.getUsername() == null) {
            throw new ValidationException("Authenticated user is required");
        }
        return user.getUsername();
    }

    private String textValue(ObjectNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
