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
import ru.hgd.sdlc.common.InstantUuidCursor;
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.publication.application.PublicationService;
import ru.hgd.sdlc.publication.domain.PublicationStatus;
import ru.hgd.sdlc.rule.api.RuleSaveRequest;
import ru.hgd.sdlc.rule.domain.RuleApprovalStatus;
import ru.hgd.sdlc.rule.domain.RuleLifecycleStatus;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;

@Service
public class RuleService {
    private static final String INITIAL_VERSION = "0.1";
    private static final Pattern RULE_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]*$");
    private static final String TEAM_SCOPE = "team";
    private static final String ORGANIZATION_SCOPE = "organization";
    private static final String TEAM_ID_PREFIX = "team-";
    private static final List<String> ALLOWED_RULE_KINDS = List.of("architecture", "coding-style", "security");
    private static final List<String> ALLOWED_PLATFORM_CODES = List.of("FRONT", "BACK", "DATA");

    private final RuleVersionRepository repository;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final RuleTemplateService templateService;
    private final PublicationService publicationService;

    public RuleService(
            RuleVersionRepository repository,
            RuleTemplateService templateService,
            PublicationService publicationService
    ) {
        this.repository = repository;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.templateService = templateService;
        this.publicationService = publicationService;
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
    public RuleCatalogPage queryLatestForCatalog(RuleCatalogQuery query) {
        int effectiveLimit = clampLimit(query.limit());
        InstantUuidCursor.Parsed parsedCursor = InstantUuidCursor.decode(query.cursor(), "cursor");
        List<RuleVersion> rows = repository.queryLatestForCatalog(
                normalizeFilter(query.search()),
                normalizeEnumFilter(query.codingAgent()),
                normalizeEnumFilter(query.status()),
                normalizeFilter(query.teamCode()),
                normalizeFilter(query.platformCode()),
                normalizeKindFilter(query.ruleKind()),
                normalizeFilter(query.scope()),
                normalizeEnumFilter(query.approvalStatus()),
                normalizeEnumFilter(query.lifecycleStatus()),
                normalizeFilter(query.tag()),
                normalizeFilter(query.version()),
                query.hasDescription(),
                parsedCursor == null ? null : parsedCursor.savedAt(),
                parsedCursor == null ? null : parsedCursor.id(),
                effectiveLimit + 1
        );
        boolean hasMore = rows.size() > effectiveLimit;
        List<RuleVersion> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            RuleVersion last = page.get(page.size() - 1);
            nextCursor = InstantUuidCursor.encode(last.getSavedAt(), last.getId());
        }
        return new RuleCatalogPage(page, nextCursor, hasMore);
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
        if (request.description() == null || request.description().isBlank()) {
            throw new ValidationException("description is required");
        }
        if (request.platformCode() == null || request.platformCode().isBlank()) {
            throw new ValidationException("platform_code is required");
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
        String scope = parseScope(request.scope());
        validateIdForScope(ruleId, scope, "rule_id");
        RuleProvider codingAgent = parseCodingAgent(request.codingAgent());
        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());
        boolean publishNow = false;
        boolean requestPublish = publish;
        String ruleKind = parseRuleKind(request.ruleKind());
        if (codingAgent == null) {
            throw new ValidationException("coding_agent is required");
        }
        MarkdownFrontmatterParser.ParsedMarkdown parsed = null;
        if (publish) {
            parsed = frontmatterParser.parse(request.ruleMarkdown());
            validateCodingAgentFrontmatter(codingAgent, parsed.frontmatter());
        }

        List<RuleVersion> allVersions = repository.findByRuleIdOrderBySavedAtDesc(ruleId);
        List<RuleVersion> publishedVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == RuleStatus.PUBLISHED)
                .toList();
        List<RuleVersion> draftVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == RuleStatus.DRAFT)
                .toList();
        int baseMajor = resolveBaseMajor(request.baseVersion(), publishedVersions);
        RuleVersion existingDraft = findDraftForMajor(draftVersions, baseMajor);
        Integer maxPublishedMajor = findMaxPublishedMajor(publishedVersions);
        Integer maxMinorForMajor = findMaxPublishedMinorForMajor(publishedVersions, baseMajor);
        if (existingDraft != null) {
            if (existingDraft.getResourceVersion() != request.resourceVersion()) {
                throw new ConflictException("resource_version mismatch for draft");
            }
        } else if (request.resourceVersion() != 0L) {
            throw new ConflictException("resource_version mismatch for draft");
        }

        String version = publish
                ? resolvePublishVersion(existingDraft, maxMinorForMajor, maxPublishedMajor, release, baseMajor)
                : resolveDraftVersion(existingDraft, maxMinorForMajor, baseMajor, publishedVersions.isEmpty());
        String canonicalName = ruleId + "@" + version;
        String updatedMarkdown = request.ruleMarkdown();

        boolean bumpDraftBeforeInsert = publishNow
                && existingDraft != null
                && existingDraft.getVersion().equals(version);
        if (bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        RuleVersion entity;
        boolean useExistingDraft = existingDraft != null && (!publish || !release);
        if (useExistingDraft) {
            entity = existingDraft;
        } else {
            entity = new RuleVersion();
            entity.setId(UUID.randomUUID());
            entity.setResourceVersion(0L);
        }

        entity.setRuleId(ruleId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        RuleStatus persistedStatus;
        RuleApprovalStatus approvalStatus;
        Instant now = Instant.now();
        if (publishNow) {
            persistedStatus = RuleStatus.PUBLISHED;
            approvalStatus = RuleApprovalStatus.PUBLISHED;
            entity.setApprovedBy(resolveSavedBy(user));
            entity.setApprovedAt(now);
            entity.setPublishedAt(now);
        } else if (requestPublish) {
            persistedStatus = RuleStatus.DRAFT;
            approvalStatus = RuleApprovalStatus.PENDING_APPROVAL;
        } else {
            persistedStatus = RuleStatus.DRAFT;
            approvalStatus = RuleApprovalStatus.DRAFT;
        }
        entity.setStatus(persistedStatus);
        entity.setApprovalStatus(approvalStatus);
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description() == null ? null : request.description().trim());
        entity.setCodingAgent(codingAgent);
        entity.setRuleMarkdown(updatedMarkdown);
        entity.setChecksum(publishNow ? ChecksumUtil.sha256(updatedMarkdown) : null);
        entity.setTeamCode(normalizeOptional(request.teamCode()));
        entity.setPlatformCode(normalizePlatformCode(request.platformCode()));
        entity.setTags(normalizeTags(request.tags()));
        entity.setRuleKind(ruleKind);
        entity.setScope(scope);
        if (TEAM_SCOPE.equals(scope)) {
            entity.setForkedFrom(normalizeOptional(request.forkedFrom()));
            entity.setForkedBy(resolveForkedBy(user, request.forkedBy()));
        } else {
            entity.setForkedFrom(null);
            entity.setForkedBy(null);
        }
        entity.setApprovedBy(publishNow ? entity.getApprovedBy() : null);
        entity.setApprovedAt(publishNow ? entity.getApprovedAt() : null);
        entity.setPublishedAt(publishNow ? entity.getPublishedAt() : null);
        entity.setSourceRef(normalizeOptional(request.sourceRef()));
        entity.setSourcePath(normalizeOptional(request.sourcePath()));
        entity.setLifecycleStatus(parseLifecycleStatus(request.lifecycleStatus()));
        if (requestPublish) {
            entity.setPublicationStatus(PublicationStatus.PENDING_APPROVAL);
            entity.setLastPublishError(null);
            entity.setPublishedPrUrl(null);
            entity.setPublishedCommitSha(null);
        } else if (entity.getPublicationStatus() == null) {
            entity.setPublicationStatus(PublicationStatus.DRAFT);
        }
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(now);

        RuleVersion saved = repository.save(entity);
        if (requestPublish) {
            publicationService.upsertRuleRequest(saved, resolveSavedBy(user));
        }

        if (publishNow && existingDraft != null && !bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        return saved;
    }

    private String resolveDraftVersion(
            RuleVersion existingDraft,
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
            RuleVersion existingDraft,
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

    private void bumpDraftVersion(RuleVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getRuleId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.saveAndFlush(draft);
    }

    private int resolveBaseMajor(String baseVersion, List<RuleVersion> publishedVersions) {
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

    private RuleVersion findDraftForMajor(List<RuleVersion> drafts, int major) {
        for (RuleVersion draft : drafts) {
            int[] parsed = parseVersionSafe(draft.getVersion());
            if (parsed != null && parsed[0] == major) {
                return draft;
            }
        }
        return null;
    }

    private RuleVersion findPublishedForMajor(List<RuleVersion> published, int major) {
        for (RuleVersion version : published) {
            int[] parsed = parseVersionSafe(version.getVersion());
            if (parsed != null && parsed[0] == major) {
                return version;
            }
        }
        return null;
    }

    private Integer findMaxPublishedMajor(List<RuleVersion> published) {
        Integer maxMajor = null;
        for (RuleVersion version : published) {
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

    private Integer findMaxPublishedMinorForMajor(List<RuleVersion> published, int major) {
        Integer maxMinor = null;
        for (RuleVersion version : published) {
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

    private int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return 24;
        }
        return Math.min(Math.max(requestedLimit, 1), 100);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeKindFilter(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase().replace(' ', '-');
    }

    private String normalizeEnumFilter(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replace('-', '_').toUpperCase();
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

    private RuleLifecycleStatus parseLifecycleStatus(String lifecycleStatus) {
        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            return RuleLifecycleStatus.ACTIVE;
        }
        try {
            return RuleLifecycleStatus.valueOf(lifecycleStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported lifecycle_status: " + lifecycleStatus);
        }
    }

    private String parseScope(String scope) {
        String normalized = normalizeOptional(scope);
        if (normalized == null) {
            throw new ValidationException("scope is required");
        }
        String value = normalized.toLowerCase();
        if (!TEAM_SCOPE.equals(value) && !ORGANIZATION_SCOPE.equals(value)) {
            throw new ValidationException("Unsupported scope: " + scope);
        }
        return value;
    }

    private void validateIdForScope(String id, String scope, String fieldName) {
        if (TEAM_SCOPE.equals(scope) && !id.startsWith(TEAM_ID_PREFIX)) {
            throw new ValidationException(fieldName + " for team scope must start with '" + TEAM_ID_PREFIX + "'");
        }
        if (ORGANIZATION_SCOPE.equals(scope) && id.startsWith(TEAM_ID_PREFIX)) {
            throw new ValidationException(fieldName + " for organization scope must not start with '" + TEAM_ID_PREFIX + "'");
        }
    }

    private String resolveForkedBy(User user, String requestedForkedBy) {
        String normalized = normalizeOptional(requestedForkedBy);
        if (normalized != null) {
            return normalized;
        }
        return user == null ? null : normalizeOptional(user.getUsername());
    }

    private String normalizePlatformCode(String platformCode) {
        String normalized = normalizeOptional(platformCode);
        if (normalized == null) {
            throw new ValidationException("platform_code is required");
        }
        String value = normalized.toUpperCase();
        if (!ALLOWED_PLATFORM_CODES.contains(value)) {
            throw new ValidationException("Unsupported platform_code: " + platformCode);
        }
        return value;
    }

    private String parseRuleKind(String ruleKind) {
        String normalized = normalizeOptional(ruleKind);
        if (normalized == null) {
            return null;
        }
        String value = normalized.toLowerCase().replace(' ', '-');
        if (!ALLOWED_RULE_KINDS.contains(value)) {
            throw new ValidationException("Unsupported rule_kind: " + ruleKind);
        }
        return value;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            String value = normalizeOptional(tag);
            if (value != null && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void validateCodingAgentFrontmatter(RuleProvider codingAgent, ObjectNode frontmatter) {
        List<String> required = templateService.requiredFrontmatter(codingAgent);
        if (frontmatter == null) {
            if (required.isEmpty()) {
                return;
            }
            throw new ValidationException("Frontmatter is required for publish");
        }
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

    public record RuleCatalogQuery(
            String cursor,
            Integer limit,
            String search,
            String codingAgent,
            String teamCode,
            String platformCode,
            String ruleKind,
            String scope,
            String approvalStatus,
            String lifecycleStatus,
            String tag,
            String status,
            String version,
            Boolean hasDescription
    ) {
    }

    public record RuleCatalogPage(
            List<RuleVersion> items,
            String nextCursor,
            boolean hasMore
    ) {
    }
}
