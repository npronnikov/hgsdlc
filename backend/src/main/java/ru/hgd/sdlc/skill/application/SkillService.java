package ru.hgd.sdlc.skill.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.InstantUuidCursor;
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.publication.application.PublicationService;
import ru.hgd.sdlc.publication.domain.PublicationStatus;
import ru.hgd.sdlc.skill.api.SkillSaveRequest;
import ru.hgd.sdlc.skill.domain.SkillApprovalStatus;
import ru.hgd.sdlc.skill.domain.SkillLifecycleStatus;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.domain.SkillFileEntity;
import ru.hgd.sdlc.skill.domain.SkillFileRole;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;
import ru.hgd.sdlc.skill.domain.TagEntity;
import ru.hgd.sdlc.skill.infrastructure.SkillFileRepository;
import ru.hgd.sdlc.skill.infrastructure.TagRepository;

@Service
public class SkillService {
    private static final String INITIAL_VERSION = "0.1";
    private static final Pattern SKILL_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]*$");
    private static final String TEAM_SCOPE = "team";
    private static final String ORGANIZATION_SCOPE = "organization";
    private static final String TEAM_ID_PREFIX = "team-";
    private static final List<String> ALLOWED_SKILL_KINDS = List.of("analysis", "code", "review", "refactor", "qa", "ops");

    private final SkillVersionRepository repository;
    private final SkillFileRepository skillFileRepository;
    private final TagRepository tagRepository;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final SkillTemplateService templateService;
    private final PublicationService publicationService;
    private final SkillPackageService skillPackageService;
    private final SkillEmbeddingService skillEmbeddingService;

    public SkillService(
            SkillVersionRepository repository,
            SkillFileRepository skillFileRepository,
            TagRepository tagRepository,
            SkillTemplateService templateService,
            PublicationService publicationService,
            SkillPackageService skillPackageService,
            SkillEmbeddingService skillEmbeddingService
    ) {
        this.repository = repository;
        this.skillFileRepository = skillFileRepository;
        this.tagRepository = tagRepository;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.templateService = templateService;
        this.publicationService = publicationService;
        this.skillPackageService = skillPackageService;
        this.skillEmbeddingService = skillEmbeddingService;
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
    public SkillCatalogPage queryLatestForCatalog(SkillCatalogQuery query) {
        int effectiveLimit = clampLimit(query.limit());
        InstantUuidCursor.Parsed parsedCursor = InstantUuidCursor.decode(query.cursor(), "cursor");
        List<SkillVersion> rows = repository.queryLatestForCatalog(
                normalizeFilter(query.search()),
                normalizeEnumFilter(query.codingAgent()),
                normalizeEnumFilter(query.status()),
                normalizeFilter(query.teamCode()),
                normalizeFilter(query.scope()),
                normalizeFilter(query.platformCode()),
                normalizeKindFilter(query.skillKind()),
                normalizeEnumFilter(query.lifecycleStatus()),
                normalizeFilter(query.version()),
                normalizeFilter(query.tag()),
                query.hasDescription(),
                parsedCursor == null ? null : parsedCursor.savedAt(),
                parsedCursor == null ? null : parsedCursor.id(),
                effectiveLimit + 1
        );
        boolean hasMore = rows.size() > effectiveLimit;
        List<SkillVersion> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            SkillVersion last = page.get(page.size() - 1);
            nextCursor = InstantUuidCursor.encode(last.getSavedAt(), last.getId());
        }
        return new SkillCatalogPage(page, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public List<String> listTags() {
        return tagRepository.findAll().stream()
                .map(TagEntity::getCode)
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listPendingPublication(User user) {
        requireApprover(user);
        return repository.findByPublicationStatusOrderBySavedAtDesc(PublicationStatus.PENDING_APPROVAL);
    }

    @Transactional
    public SkillVersion approvePublication(String skillId, String version, User user) {
        requireApprover(user);
        SkillVersion entity = repository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));
        if (entity.getPublicationStatus() != PublicationStatus.PENDING_APPROVAL) {
            throw new ValidationException("Skill is not pending approval");
        }
        Instant now = Instant.now();
        entity.setApprovalStatus(SkillApprovalStatus.PUBLISHED);
        entity.setStatus(SkillStatus.PUBLISHED);
        entity.setPublicationStatus(PublicationStatus.PUBLISHED);
        entity.setApprovedBy(resolveSavedBy(user));
        entity.setApprovedAt(now);
        entity.setPublishedAt(now);
        String packageChecksum = computePackageChecksumForVersion(entity);
        entity.setChecksum(packageChecksum);
        entity.setSavedAt(now);
        entity.setSavedBy(resolveSavedBy(user));
        repository.save(entity);
        return publicationService.approveSkillPublication(skillId, version, user);
    }

    @Transactional
    public SkillVersion rejectPublication(String skillId, String version, User user) {
        requireApprover(user);
        SkillVersion entity = repository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));
        if (entity.getPublicationStatus() != PublicationStatus.PENDING_APPROVAL) {
            throw new ValidationException("Skill is not pending approval");
        }
        entity.setApprovalStatus(SkillApprovalStatus.REJECTED);
        entity.setStatus(SkillStatus.DRAFT);
        entity.setSavedAt(Instant.now());
        entity.setSavedBy(resolveSavedBy(user));
        entity.setPublicationStatus(PublicationStatus.REJECTED);
        repository.save(entity);
        return publicationService.rejectSkillPublication(skillId, version, user, "Rejected by approver");
    }

    @Transactional
    public void deleteDraft(String skillId, String version, User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ValidationException("Authenticated user is required");
        }
        SkillVersion entity = repository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));
        if (entity.getStatus() != SkillStatus.DRAFT) {
            throw new ValidationException("Only draft skill version can be deleted");
        }
        ensureDraftIsEditable(entity);
        repository.delete(entity);
    }

    @Transactional
    public SkillVersion requestDeprecation(String skillId, User user) {
        resolveSavedBy(user);
        SkillVersion published = repository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(skillId, SkillStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Published skill not found: " + skillId));
        if (!isActiveLifecycle(published.getLifecycleStatus())) {
            throw new ValidationException("Only active published skill can be deprecated");
        }
        int[] parsedVersion = parseVersionSafe(published.getVersion());
        if (parsedVersion == null) {
            throw new ValidationException("Invalid version: " + published.getVersion());
        }
        List<SkillVersion> allVersions = repository.findBySkillIdOrderBySavedAtDesc(skillId);
        List<SkillVersion> draftVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == SkillStatus.DRAFT)
                .toList();
        SkillVersion existingDraft = findDraftForMajor(draftVersions, parsedVersion[0]);
        long resourceVersion = existingDraft == null ? 0L : existingDraft.getResourceVersion();
        List<SkillSaveRequest.SkillFileSaveRequest> files = listFiles(skillId, published.getVersion()).stream()
                .map((file) -> new SkillSaveRequest.SkillFileSaveRequest(
                        file.getPath(),
                        file.getTextContent(),
                        file.isExecutable()
                ))
                .toList();
        SkillSaveRequest request = new SkillSaveRequest(
                published.getName(),
                published.getDescription(),
                published.getSkillId(),
                toApiCodingAgent(published.getCodingAgent()),
                published.getTeamCode(),
                published.getPlatformCode(),
                published.getTags(),
                published.getSkillKind(),
                published.getScope() == null ? ORGANIZATION_SCOPE : published.getScope(),
                "deprecated",
                published.getForkedFrom(),
                published.getForkedBy(),
                published.getSourceRef(),
                published.getSourcePath(),
                files,
                true,
                false,
                published.getVersion(),
                resourceVersion
        );
        return save(skillId, request, user);
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

    @Transactional(readOnly = true)
    public List<SkillFileEntity> listFiles(String skillId, String version) {
        SkillVersion skillVersion = getVersion(skillId, version);
        List<SkillFileEntity> files = skillFileRepository.findBySkillVersionIdOrderByPathAsc(skillVersion.getId());
        if (!files.isEmpty()) {
            return files;
        }
        return List.of(legacySkillMarkdownFile(skillVersion));
    }

    @Transactional(readOnly = true)
    public String getFileContent(String skillId, String version, String path) {
        String normalizedPath = skillPackageService.normalizePath(path);
        return listFiles(skillId, version).stream()
                .filter(file -> normalizedPath.equals(file.getPath()))
                .findFirst()
                .map(SkillFileEntity::getTextContent)
                .orElseThrow(() -> new NotFoundException("Skill file not found: " + normalizedPath));
    }

    @Transactional
    public SkillVersion save(String skillId, SkillSaveRequest request, User user) {
        if (request == null) {
            throw new ValidationException("Request body is required");
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
        if (request.teamCode() == null || request.teamCode().isBlank()) {
            throw new ValidationException("team_code is required");
        }
        if (request.platformCode() == null || request.platformCode().isBlank()) {
            throw new ValidationException("platform_code is required");
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
        String scope = parseScope(request.scope());
        validateIdForScope(skillId, scope, "skill_id");
        SkillProvider codingAgent = parseCodingAgent(request.codingAgent());
        boolean publish = Boolean.TRUE.equals(request.publish());
        boolean release = Boolean.TRUE.equals(request.release());
        boolean publishNow = false;
        boolean requestPublish = publish;
        SkillLifecycleStatus lifecycleStatus = parseLifecycleStatus(request.lifecycleStatus());
        List<String> normalizedTags = normalizeTags(request.tags());
        String skillKind = parseSkillKind(request.skillKind());
        if (codingAgent == null) {
            throw new ValidationException("coding_agent is required");
        }
        SkillPackageService.PreparedPackage preparedPackage = skillPackageService.prepareForSave(request.files());
        String skillMarkdown = extractSkillMarkdown(preparedPackage.files());
        MarkdownFrontmatterParser.ParsedMarkdown parsed = null;
        if (publish) {
            parsed = frontmatterParser.parse(skillMarkdown);
            validateCodingAgentFrontmatter(codingAgent, parsed.frontmatter());
        }

        List<SkillVersion> allVersions = repository.findBySkillIdOrderBySavedAtDesc(skillId);
        List<SkillVersion> publishedVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == SkillStatus.PUBLISHED)
                .toList();
        List<SkillVersion> draftVersions = allVersions.stream()
                .filter((version) -> version.getStatus() == SkillStatus.DRAFT)
                .toList();
        int baseMajor = resolveBaseMajor(request.baseVersion(), publishedVersions);
        SkillVersion existingDraft = findDraftForMajor(draftVersions, baseMajor);
        Integer maxPublishedMajor = findMaxPublishedMajor(publishedVersions);
        Integer maxMinorForMajor = findMaxPublishedMinorForMajor(publishedVersions, baseMajor);
        if (existingDraft != null) {
            ensureDraftIsEditable(existingDraft);
            if (existingDraft.getResourceVersion() != request.resourceVersion()) {
                throw new ConflictException("resource_version mismatch for draft");
            }
        } else if (request.resourceVersion() != 0L) {
            throw new ConflictException("resource_version mismatch for draft");
        }

        String version = publish
                ? resolvePublishVersion(existingDraft, maxMinorForMajor, maxPublishedMajor, release, baseMajor)
                : resolveDraftVersion(existingDraft, maxMinorForMajor, baseMajor, publishedVersions.isEmpty());
        String canonicalName = skillId + "@" + version;

        boolean bumpDraftBeforeInsert = publishNow
                && existingDraft != null
                && existingDraft.getVersion().equals(version);
        if (bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        SkillVersion entity;
        boolean useExistingDraft = existingDraft != null && (!publish || !release);
        if (useExistingDraft) {
            entity = existingDraft;
        } else {
            entity = new SkillVersion();
            entity.setId(UUID.randomUUID());
            entity.setResourceVersion(0L);
        }

        entity.setSkillId(skillId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        SkillStatus persistedStatus;
        SkillApprovalStatus approvalStatus;
        Instant now = Instant.now();
        if (publishNow) {
            persistedStatus = SkillStatus.PUBLISHED;
            approvalStatus = SkillApprovalStatus.PUBLISHED;
            entity.setApprovedBy(resolveSavedBy(user));
            entity.setApprovedAt(now);
            entity.setPublishedAt(now);
        } else if (requestPublish) {
            persistedStatus = SkillStatus.DRAFT;
            approvalStatus = SkillApprovalStatus.PENDING_APPROVAL;
        } else {
            persistedStatus = SkillStatus.DRAFT;
            approvalStatus = SkillApprovalStatus.DRAFT;
        }
        entity.setStatus(persistedStatus);
        entity.setApprovalStatus(approvalStatus);
        entity.setName(request.name().trim());
        entity.setDescription(request.description().trim());
        entity.setCodingAgent(codingAgent);
        entity.setTeamCode(request.teamCode().trim());
        entity.setPlatformCode(normalizePlatformCode(request.platformCode()));
        entity.setTags(normalizedTags);
        entity.setSkillKind(skillKind);
        entity.setScope(scope);
        entity.setLifecycleStatus(lifecycleStatus);
        if (TEAM_SCOPE.equals(scope)) {
            entity.setForkedFrom(normalizeOptional(request.forkedFrom()));
            entity.setForkedBy(resolveForkedBy(user, request.forkedBy()));
        } else {
            entity.setForkedFrom(null);
            entity.setForkedBy(null);
        }
        entity.setSourceRef(normalizeOptional(request.sourceRef()));
        entity.setSourcePath(normalizeOptional(request.sourcePath()));
        entity.setSkillMarkdown(skillMarkdown);
        entity.setChecksum(preparedPackage.packageChecksum());
        entity.setSavedBy(resolveSavedBy(user));
        entity.setSavedAt(now);
        if (requestPublish) {
            entity.setPublicationStatus(PublicationStatus.PENDING_APPROVAL);
            entity.setLastPublishError(null);
            entity.setPublishedPrUrl(null);
            entity.setPublishedCommitSha(null);
        } else if (entity.getPublicationStatus() == null) {
            entity.setPublicationStatus(PublicationStatus.DRAFT);
        }

        SkillVersion saved = repository.save(entity);
        replaceSkillFiles(saved, preparedPackage.files());
        ensureTagsExist(normalizedTags);
        if (requestPublish) {
            publicationService.upsertSkillRequest(saved, resolveSavedBy(user));
        }

        skillEmbeddingService.generateEmbedding(saved.getId());

        if (publishNow && existingDraft != null && !bumpDraftBeforeInsert) {
            bumpDraftVersion(existingDraft, version);
        }

        return saved;
    }

    private String extractSkillMarkdown(List<SkillPackageService.PreparedSkillFile> files) {
        return files.stream()
                .filter(file -> "SKILL.md".equals(file.path()))
                .findFirst()
                .map(SkillPackageService.PreparedSkillFile::textContent)
                .orElseThrow(() -> new ValidationException("package must contain SKILL.md"));
    }

    private void replaceSkillFiles(SkillVersion saved, List<SkillPackageService.PreparedSkillFile> files) {
        skillFileRepository.deleteBySkillVersionId(saved.getId());
        skillFileRepository.flush();
        Instant now = Instant.now();
        List<SkillFileEntity> entities = files.stream()
                .sorted(Comparator.comparing(SkillPackageService.PreparedSkillFile::path))
                .map(file -> SkillFileEntity.builder()
                        .id(UUID.randomUUID())
                        .skillVersionId(saved.getId())
                        .path(file.path())
                        .role(file.role())
                        .mediaType(file.mediaType())
                        .executable(file.executable())
                        .textContent(file.textContent())
                        .sizeBytes(file.sizeBytes())
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        skillFileRepository.saveAll(entities);
    }

    private SkillFileEntity legacySkillMarkdownFile(SkillVersion version) {
        String text = skillPackageService.normalizeText(version.getSkillMarkdown());
        return SkillFileEntity.builder()
                .id(version.getId())
                .skillVersionId(version.getId())
                .path("SKILL.md")
                .role(SkillFileRole.INSTRUCTION)
                .mediaType("text/markdown")
                .executable(false)
                .textContent(text)
                .sizeBytes(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .createdAt(version.getSavedAt())
                .updatedAt(version.getSavedAt())
                .build();
    }

    private void ensureDraftIsEditable(SkillVersion draft) {
        PublicationStatus publicationStatus = draft.getPublicationStatus();
        if (publicationStatus == PublicationStatus.PENDING_APPROVAL
                || publicationStatus == PublicationStatus.APPROVED
                || publicationStatus == PublicationStatus.PUBLISHING
                || publicationStatus == PublicationStatus.PUBLISHED) {
            throw new ValidationException("Skill draft is locked after publication request");
        }
    }

    private String resolveDraftVersion(
            SkillVersion existingDraft,
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
            SkillVersion existingDraft,
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

    private void bumpDraftVersion(SkillVersion draft, String publishedVersion) {
        String nextDraftVersion = nextMinor(publishedVersion);
        draft.setVersion(nextDraftVersion);
        draft.setCanonicalName(draft.getSkillId() + "@" + nextDraftVersion);
        draft.setChecksum(null);
        repository.saveAndFlush(draft);
    }

    private String computePackageChecksumForVersion(SkillVersion version) {
        List<SkillFileEntity> files = skillFileRepository.findBySkillVersionIdOrderByPathAsc(version.getId());
        if (files.isEmpty()) {
            List<SkillPackageService.PreparedSkillFile> fallbackFiles = List.of(
                    new SkillPackageService.PreparedSkillFile(
                            "SKILL.md",
                            SkillFileRole.INSTRUCTION,
                            "text/markdown",
                            false,
                            skillPackageService.normalizeText(version.getSkillMarkdown()),
                            version.getSkillMarkdown() == null ? 0L : version.getSkillMarkdown().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    )
            );
            return skillPackageService.computePackageChecksum(fallbackFiles);
        }
        List<SkillPackageService.PreparedSkillFile> preparedFiles = files.stream()
                .map(file -> new SkillPackageService.PreparedSkillFile(
                        file.getPath(),
                        file.getRole(),
                        file.getMediaType(),
                        file.isExecutable(),
                        skillPackageService.normalizeText(file.getTextContent()),
                        file.getSizeBytes()
                ))
                .toList();
        return skillPackageService.computePackageChecksum(preparedFiles);
    }

    private int resolveBaseMajor(String baseVersion, List<SkillVersion> publishedVersions) {
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

    private SkillVersion findDraftForMajor(List<SkillVersion> drafts, int major) {
        for (SkillVersion draft : drafts) {
            int[] parsed = parseVersionSafe(draft.getVersion());
            if (parsed != null && parsed[0] == major) {
                return draft;
            }
        }
        return null;
    }

    private SkillVersion findPublishedForMajor(List<SkillVersion> published, int major) {
        for (SkillVersion version : published) {
            int[] parsed = parseVersionSafe(version.getVersion());
            if (parsed != null && parsed[0] == major) {
                return version;
            }
        }
        return null;
    }

    private Integer findMaxPublishedMajor(List<SkillVersion> published) {
        Integer maxMajor = null;
        for (SkillVersion version : published) {
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

    private Integer findMaxPublishedMinorForMajor(List<SkillVersion> published, int major) {
        Integer maxMinor = null;
        for (SkillVersion version : published) {
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

    private SkillLifecycleStatus parseLifecycleStatus(String lifecycleStatus) {
        try {
            return SkillLifecycleStatus.from(lifecycleStatus == null ? "active" : lifecycleStatus);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported lifecycle_status: " + lifecycleStatus);
        }
    }

    private boolean isActiveLifecycle(SkillLifecycleStatus lifecycleStatus) {
        return lifecycleStatus == null || lifecycleStatus == SkillLifecycleStatus.ACTIVE;
    }

    private String toApiCodingAgent(SkillProvider codingAgent) {
        if (codingAgent == null) {
            return null;
        }
        return codingAgent.name().toLowerCase().replace('_', '-');
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

    private String parseSkillKind(String skillKind) {
        String normalized = normalizeOptional(skillKind);
        if (normalized == null) {
            return null;
        }
        String value = normalized.toLowerCase().replace(' ', '-');
        if (!ALLOWED_SKILL_KINDS.contains(value)) {
            throw new ValidationException("Unsupported skill_kind: " + skillKind);
        }
        return value;
    }

    private String normalizePlatformCode(String platformCode) {
        String normalized = platformCode.trim().toUpperCase();
        if (!List.of("FRONT", "BACK", "DATA").contains(normalized)) {
            throw new ValidationException("Unsupported platform_code: " + platformCode);
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            String value = normalizeOptional(tag);
            if (value == null) {
                continue;
            }
            String code = value.toLowerCase().replace(' ', '-');
            if (!normalized.contains(code)) {
                normalized.add(code);
            }
        }
        return normalized;
    }

    private void ensureTagsExist(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<TagEntity> existing = tagRepository.findByCodeIn(tags);
        List<String> existingCodes = existing.stream().map(TagEntity::getCode).toList();
        Instant now = Instant.now();
        List<TagEntity> toCreate = tags.stream()
                .filter(code -> !existingCodes.contains(code))
                .map(code -> TagEntity.builder()
                        .code(code)
                        .name(code)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        if (!toCreate.isEmpty()) {
            tagRepository.saveAll(toCreate);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isApprover(User user) {
        return user != null && user.hasAnyRole(Role.TECH_APPROVER, Role.ADMIN);
    }

    private void requireApprover(User user) {
        if (!isApprover(user)) {
            throw new ValidationException("Approver role is required");
        }
    }

    private void validateCodingAgentFrontmatter(SkillProvider codingAgent, ObjectNode frontmatter) {
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
        if (version == null || !version.matches("\\d+\\.\\d+")) {
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

    public record SkillCatalogQuery(
            String cursor,
            Integer limit,
            String search,
            String codingAgent,
            String status,
            String teamCode,
            String scope,
            String platformCode,
            String skillKind,
            String lifecycleStatus,
            String version,
            String tag,
            Boolean hasDescription
    ) {
    }

    public record SkillCatalogPage(
            List<SkillVersion> items,
            String nextCursor,
            boolean hasMore
    ) {
    }

}
