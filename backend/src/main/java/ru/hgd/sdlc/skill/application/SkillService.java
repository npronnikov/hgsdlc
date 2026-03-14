package ru.hgd.sdlc.skill.application;

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
import ru.hgd.sdlc.skill.api.SkillSaveRequest;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class SkillService {
    private static final String SCHEMA_PATH = "schemas/skill.schema.json";
    private static final String DRAFT_VERSION = "0.0.0";

    private final SkillVersionRepository repository;
    private final JsonSchemaValidator schemaValidator;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final ObjectMapper objectMapper;

    public SkillService(
            SkillVersionRepository repository,
            JsonSchemaValidator schemaValidator,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.schemaValidator = schemaValidator;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listLatest() {
        List<SkillVersion> all = repository.findAllByOrderBySavedAtDesc();
        Map<String, SkillVersion> latestBySkill = new LinkedHashMap<>();
        for (SkillVersion version : all) {
            latestBySkill.putIfAbsent(version.getSkillId(), version);
        }
        return new ArrayList<>(latestBySkill.values());
    }

    @Transactional(readOnly = true)
    public SkillVersion getLatest(String skillId) {
        return repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.DRAFT)
                .or(() -> repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED))
                .orElseThrow(() -> new NotFoundException("Skill not found: " + skillId));
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> getVersions(String skillId) {
        List<SkillVersion> versions = repository.findBySkillIdOrderBySavedAtDesc(skillId);
        if (versions.isEmpty()) {
            throw new NotFoundException("Skill not found: " + skillId);
        }
        return versions;
    }

    @Transactional
    public SkillVersion save(String skillId, SkillSaveRequest request, User user) {
        if (request == null || request.skillMarkdown() == null) {
            throw new ValidationException("skill_markdown is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        boolean publish = Boolean.TRUE.equals(request.publish());
        MarkdownFrontmatterParser.ParsedMarkdown parsed = frontmatterParser.parse(request.skillMarkdown());
        ObjectNode frontmatter = parsed.frontmatter();
        String id = textValue(frontmatter, "id");
        if (id == null || id.isBlank()) {
            throw new ValidationException("Frontmatter id is required");
        }
        if (!skillId.equals(id)) {
            throw new ValidationException("Path skillId does not match frontmatter id");
        }

        SkillVersion existingDraft = repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.DRAFT)
                .orElse(null);
        SkillVersion latestPublished = repository
                .findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED)
                .orElse(null);
        if (publish) {
            SkillVersion base = existingDraft != null ? existingDraft : latestPublished;
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

        String version = publish ? nextPublishedVersion(skillId) : DRAFT_VERSION;
        String canonicalName = skillId + "@" + version;
        frontmatter.put("id", skillId);
        frontmatter.put("version", version);
        frontmatter.put("canonical_name", canonicalName);

        schemaValidator.validate(frontmatter, SCHEMA_PATH);

        String updatedMarkdown = frontmatterParser.render(frontmatter, parsed.body());
        ObjectNode modelJson = objectMapper.createObjectNode();
        modelJson.set("frontmatter", frontmatter);
        modelJson.put("body", parsed.body() == null ? "" : parsed.body());

        SkillVersion entity;
        if (!publish && existingDraft != null) {
            entity = existingDraft;
        } else {
            entity = new SkillVersion();
            entity.setId(UUID.randomUUID());
            entity.setResourceVersion(0L);
        }

        entity.setSkillId(skillId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(publish ? SkillStatus.PUBLISHED : SkillStatus.DRAFT);
        entity.setSkillMarkdown(updatedMarkdown);
        entity.setSkillModelJson(modelJson);
        entity.setSkillChecksum(ChecksumUtil.sha256(updatedMarkdown));
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(Instant.now());

        return repository.save(entity);
    }

    private String nextPublishedVersion(String skillId) {
        SkillVersion latestPublished = repository
                .findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED)
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
