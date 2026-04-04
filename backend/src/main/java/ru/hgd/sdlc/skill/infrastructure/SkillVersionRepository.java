package ru.hgd.sdlc.skill.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, UUID> {
    Optional<SkillVersion> findFirstBySkillIdAndStatusOrderBySavedAtDesc(String skillId, SkillStatus status);

    Optional<SkillVersion> findFirstBySkillIdAndVersionOrderBySavedAtDesc(String skillId, String version);

    Optional<SkillVersion> findFirstByCanonicalNameAndStatus(String canonicalName, SkillStatus status);
    Optional<SkillVersion> findFirstByCanonicalName(String canonicalName);

    List<SkillVersion> findBySkillIdOrderBySavedAtDesc(String skillId);

    List<SkillVersion> findByPublicationStatusOrderBySavedAtDesc(ru.hgd.sdlc.publication.domain.PublicationStatus publicationStatus);

    List<SkillVersion> findAllByOrderBySavedAtDesc();

    @Query(value = """
            WITH ranked AS (
                SELECT s.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY s.skill_id
                           ORDER BY CASE WHEN s.status = 'PUBLISHED' THEN 0 ELSE 1 END, s.saved_at DESC, s.id DESC
                       ) AS rn
                FROM skills s
            ),
            latest AS (
                SELECT *
                FROM ranked
                WHERE rn = 1
            )
            SELECT l.*
            FROM latest l
            WHERE (:search IS NULL OR LOWER(COALESCE(l.name, '') || ' ' || COALESCE(l.skill_id, '') || ' '
                || COALESCE(l.canonical_name, '') || ' ' || COALESCE(l.description, '') || ' '
                || COALESCE(l.coding_agent, '') || ' ' || COALESCE(l.platform_code, '')) LIKE CONCAT('%', LOWER(:search), '%'))
              AND (:codingAgent IS NULL OR l.coding_agent = :codingAgent)
              AND (:status IS NULL OR l.status = :status)
              AND (:teamCode IS NULL OR l.team_code = :teamCode)
              AND (:scope IS NULL OR l.scope = :scope)
              AND (:platformCode IS NULL OR l.platform_code = :platformCode)
              AND (:skillKind IS NULL OR l.skill_kind = :skillKind)
              AND (:version IS NULL OR l.version = :version)
              AND (:tag IS NULL OR LOWER(COALESCE(l.tags_json, '')) LIKE CONCAT('%', LOWER(:tag), '%'))
              AND (
                  :hasDescription IS NULL
                  OR (:hasDescription = TRUE AND l.description IS NOT NULL AND BTRIM(l.description) <> '')
                  OR (:hasDescription = FALSE AND (l.description IS NULL OR BTRIM(l.description) = ''))
              )
              AND (
                  CAST(:cursorSavedAt AS timestamp) IS NULL OR CAST(:cursorId AS uuid) IS NULL
                  OR (l.saved_at, l.id) < (:cursorSavedAt, :cursorId)
              )
            ORDER BY l.saved_at DESC, l.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<SkillVersion> queryLatestForCatalog(
            @Param("search") String search,
            @Param("codingAgent") String codingAgent,
            @Param("status") String status,
            @Param("teamCode") String teamCode,
            @Param("scope") String scope,
            @Param("platformCode") String platformCode,
            @Param("skillKind") String skillKind,
            @Param("version") String version,
            @Param("tag") String tag,
            @Param("hasDescription") Boolean hasDescription,
            @Param("cursorSavedAt") java.time.Instant cursorSavedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit
    );
}
