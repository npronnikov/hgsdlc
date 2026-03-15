package ru.hgd.sdlc.rule.application;

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
import ru.hgd.sdlc.rule.api.RuleSaveRequest;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;

@Service
public class RuleService {
    private static final String INITIAL_VERSION = "0.1";
    private static final Pattern RULE_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]*$");

    private final RuleVersionRepository repository;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final RuleTemplateService templateService;

    public RuleService(
            RuleVersionRepository repository,
            RuleTemplateService templateService
    ) {
        this.repository = repository;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.templateService = templateService;
    }

    @Transactional(readOnly = true)
    public List<RuleVersion> listLatest() {
        List<RuleVersion> all = repository.findAllByOrderBySavedAtDesc();
        Map<String, RuleVersion> latestPublished = new LinkedHashMap<>();
        Map<String, RuleVersion> latestDraft = new LinkedHashMap<>();
        for (RuleVersion version : all) {
            if (version.getStatus() == RuleStatus.PUBLISHED) {
                latestPublished.putIfAbsent(version.getRuleId(), version);
            } else {
                latestDraft.putIfAbsent(version.getRuleId(), version);
            }
        }
        Map<String, RuleVersion> latestByRule = new LinkedHashMap<>();
        for (RuleVersion version : all) {
            if (latestByRule.containsKey(version.getRuleId())) {
                continue;
            }
            RuleVersion published = latestPublished.get(version.getRuleId());
            latestByRule.put(version.getRuleId(), published != null ? published : latestDraft.get(version.getRuleId()));
        }
        return new ArrayList<>(latestByRule.values());
    }

    @Transactional(readOnly = true)
    public RuleVersion getLatest(String ruleId) {
        return repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.PUBLISHED)
                .or(() -> repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.DRAFT))
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

    @Transactional(readOnly = true)
    public RuleVersion getVersion(String ruleId, String version) {
        return repository.findFirstByRuleIdAndVersionOrderBySavedAtDesc(ruleId, version)
                .orElseThrow(() -> new NotFoundException("Rule version not found: " + ruleId + "@" + version));
    }

    @Transactional
    public RuleVersion save(String ruleId, RuleSaveRequest request, User user) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.ruleMarkdown() == null || request.ruleMarkdown().isBlank()) {
            throw new ValidationException("rule_markdown is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ValidationException("title is required");
        }
        if (request.ruleId() == null || request.ruleId().isBlank()) {
            throw new ValidationException("rule_id is required");
        }
        if (!RULE_ID_PATTERN.matcher(request.ruleId()).matches()) {
            throw new ValidationException("rule_id has invalid format");
        }
        if (!ruleId.equals(request.ruleId())) {
            throw new ValidationException("Path ruleId does not match request rule_id");
        }
        RuleProvider codingAgent = parseCodingAgent(request.codingAgent());
        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());
        if (codingAgent == null) {
            throw new ValidationException("coding_agent is required");
        }
        MarkdownFrontmatterParser.ParsedMarkdown parsed = null;
        if (publish) {
            parsed = frontmatterParser.parse(request.ruleMarkdown());
            validateCodingAgentFrontmatter(codingAgent, parsed.frontmatter());
        }

        RuleVersion existingDraft = repository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.DRAFT)
                .orElse(null);
        RuleVersion latestPublished = repository
                .findFirstByRuleIdAndStatusOrderBySavedAtDesc(ruleId, RuleStatus.PUBLISHED)
                .orElse(null);
        if (publish) {
            RuleVersion base = existingDraft != null ? existingDraft : latestPublished;
            if (base != null) {
                if (base.getStatus() == RuleStatus.PUBLISHED
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
        String canonicalName = ruleId + "@" + version;
        String updatedMarkdown = request.ruleMarkdown();

        boolean bumpDraftBeforeInsert = publish
                && existingDraft != null
                && existingDraft.getVersion().equals(version);
        if (bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

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
        entity.setTitle(request.title().trim());
        entity.setCodingAgent(codingAgent);
        entity.setRuleMarkdown(updatedMarkdown);
        entity.setChecksum(publish ? ChecksumUtil.sha256(updatedMarkdown) : null);
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(Instant.now());

        RuleVersion saved = repository.save(entity);

        if (publish && existingDraft != null && !bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        return saved;
    }

    private String resolveDraftVersion(RuleVersion existingDraft, RuleVersion latestPublished) {
        if (existingDraft != null) {
            return existingDraft.getVersion();
        }
        if (latestPublished == null) {
            return INITIAL_VERSION;
        }
        return nextMinor(latestPublished.getVersion());
    }

    private String resolvePublishVersion(RuleVersion existingDraft, RuleVersion latestPublished, boolean release) {
        String baseVersion = existingDraft != null ? existingDraft.getVersion()
                : (latestPublished == null ? INITIAL_VERSION : nextMinor(latestPublished.getVersion()));
        return release ? releaseVersion(baseVersion) : baseVersion;
    }

    private void bumpDraftVersion(RuleVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getRuleId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.save(draft);
    }

    private String resolveSavedBy(User user) {
        if (user == null || user.getUsername() == null) {
            throw new ValidationException("Authenticated user is required");
        }
        return user.getUsername();
    }

    private RuleProvider parseCodingAgent(String codingAgent) {
        if (codingAgent == null || codingAgent.isBlank()) {
            return null;
        }
        try {
            return RuleProvider.from(codingAgent);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported coding_agent: " + codingAgent);
        }
    }

    private void validateCodingAgentFrontmatter(RuleProvider codingAgent, ObjectNode frontmatter) {
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
