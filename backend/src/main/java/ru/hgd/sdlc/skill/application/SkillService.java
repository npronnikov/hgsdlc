package ru.hgd.sdlc.skill.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.skill.api.SkillSaveRequest;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class SkillService {
    private static final String INITIAL_VERSION = "0.1";
    private static final Pattern SKILL_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]*$");

    private final SkillVersionRepository repository;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final SkillTemplateService templateService;

    public SkillService(
            SkillVersionRepository repository,
            SkillTemplateService templateService
    ) {
        this.repository = repository;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.templateService = templateService;
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listLatest() {
        List<SkillVersion> all = repository.findAllByOrderBySavedAtDesc();
        Map<String, SkillVersion> latestPublished = new LinkedHashMap<>();
        Map<String, SkillVersion> latestDraft = new LinkedHashMap<>();
        for (SkillVersion version : all) {
            if (version.getStatus() == SkillStatus.PUBLISHED) {
                latestPublished.putIfAbsent(version.getSkillId(), version);
            } else {
                latestDraft.putIfAbsent(version.getSkillId(), version);
            }
        }
        Map<String, SkillVersion> latestBySkill = new LinkedHashMap<>();
        for (SkillVersion version : all) {
            if (latestBySkill.containsKey(version.getSkillId())) {
                continue;
            }
            SkillVersion published = latestPublished.get(version.getSkillId());
            latestBySkill.put(version.getSkillId(), published != null ? published : latestDraft.get(version.getSkillId()));
        }
        return new ArrayList<>(latestBySkill.values());
    }

    @Transactional(readOnly = true)
    public SkillVersion getLatest(String skillId) {
        return repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED)
                .or(() -> repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.DRAFT))
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

    @Transactional(readOnly = true)
    public SkillVersion getVersion(String skillId, String version) {
        return repository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));
    }

    @Transactional
    public SkillVersion save(String skillId, SkillSaveRequest request, User user) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.skillMarkdown() == null || request.skillMarkdown().isBlank()) {
            throw new ValidationException("skill_markdown is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("name is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ValidationException("description is required");
        }
        if (request.skillId() == null || request.skillId().isBlank()) {
            throw new ValidationException("skill_id is required");
        }
        if (!SKILL_ID_PATTERN.matcher(request.skillId()).matches()) {
            throw new ValidationException("skill_id has invalid format");
        }
        if (!skillId.equals(request.skillId())) {
            throw new ValidationException("Path skillId does not match request skill_id");
        }
        SkillProvider codingAgent = parseCodingAgent(request.codingAgent());
        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());
        if (codingAgent == null) {
            throw new ValidationException("coding_agent is required");
        }
        MarkdownFrontmatterParser.ParsedMarkdown parsed = null;
        if (publish) {
            parsed = frontmatterParser.parse(request.skillMarkdown());
            validateCodingAgentFrontmatter(codingAgent, parsed.frontmatter());
        }

        SkillVersion existingDraft = repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.DRAFT)
                .orElse(null);
        SkillVersion latestPublished = repository
                .findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED)
                .orElse(null);
        if (publish) {
            SkillVersion base = existingDraft != null ? existingDraft : latestPublished;
            if (base != null) {
                if (base.getStatus() == SkillStatus.PUBLISHED
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
        String canonicalName = skillId + "@" + version;
        String updatedMarkdown = request.skillMarkdown();

        boolean bumpDraftBeforeInsert = publish
                && existingDraft != null
                && existingDraft.getVersion().equals(version);
        if (bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

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
        entity.setName(request.name().trim());
        entity.setDescription(request.description().trim());
        entity.setCodingAgent(codingAgent);
        entity.setSkillMarkdown(updatedMarkdown);
        entity.setChecksum(publish ? ChecksumUtil.sha256(updatedMarkdown) : null);
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(Instant.now());

        SkillVersion saved = repository.save(entity);

        if (publish && existingDraft != null && !bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        return saved;
    }

    private String resolveDraftVersion(SkillVersion existingDraft, SkillVersion latestPublished) {
        if (existingDraft != null) {
            return existingDraft.getVersion();
        }
        if (latestPublished == null) {
            return INITIAL_VERSION;
        }
        return nextMinor(latestPublished.getVersion());
    }

    private String resolvePublishVersion(SkillVersion existingDraft, SkillVersion latestPublished, boolean release) {
        String baseVersion = existingDraft != null ? existingDraft.getVersion()
                : (latestPublished == null ? INITIAL_VERSION : nextMinor(latestPublished.getVersion()));
        return release ? releaseVersion(baseVersion) : baseVersion;
    }

    private void bumpDraftVersion(SkillVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getSkillId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.save(draft);
    }

    private String resolveSavedBy(User user) {
        if (user == null || user.getUsername() == null) {
            throw new ValidationException("Authenticated user is required");
        }
        return user.getUsername();
    }

    private SkillProvider parseCodingAgent(String codingAgent) {
        if (codingAgent == null || codingAgent.isBlank()) {
            return null;
        }
        try {
            return SkillProvider.from(codingAgent);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported coding_agent: " + codingAgent);
        }
    }

    private void validateCodingAgentFrontmatter(SkillProvider codingAgent, ObjectNode frontmatter) {
        if (frontmatter == null) {
            throw new ValidationException("Frontmatter is required for publish");
        }
        List<String> required = templateService.requiredFrontmatter(codingAgent);
        for (String field : required) {
            if (!frontmatter.hasNonNull(field)) {
                throw new ValidationException("Frontmatter field '" + field + "' is required for coding_agent " + codingAgent.name().toLowerCase());
            }
            JsonNode value = frontmatter.get(field);
            if (value.isTextual() && value.asText().isBlank()) {
                throw new ValidationException("Frontmatter field '" + field + "' must not be blank");
            }
            if (value.isArray() && value.size() == 0) {
                throw new ValidationException("Frontmatter field '" + field + "' must not be empty");
            }
        }
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
