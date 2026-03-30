package ru.hgd.sdlc.rule.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {
    Optional<RuleVersion> findFirstByRuleIdAndStatusOrderBySavedAtDesc(String ruleId, RuleStatus status);

    Optional<RuleVersion> findFirstByRuleIdAndVersionOrderBySavedAtDesc(String ruleId, String version);

    Optional<RuleVersion> findFirstByCanonicalNameAndStatus(String canonicalName, RuleStatus status);
    Optional<RuleVersion> findFirstByCanonicalName(String canonicalName);

    List<RuleVersion> findByRuleIdOrderBySavedAtDesc(String ruleId);
    List<RuleVersion> findByApprovalStatusOrderBySavedAtDesc(ru.hgd.sdlc.rule.domain.RuleApprovalStatus approvalStatus);

    List<RuleVersion> findAllByOrderBySavedAtDesc();

    @Query(value = """
            WITH ranked AS (
                SELECT r.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.rule_id
                           ORDER BY CASE WHEN r.status = 'PUBLISHED' THEN 0 ELSE 1 END, r.saved_at DESC, r.id DESC
                       ) AS rn
                FROM rules r
            ),
            latest AS (
                SELECT *
                FROM ranked
                WHERE rn = 1
            )
            SELECT l.*
            FROM latest l
            WHERE (:search IS NULL OR LOWER(COALESCE(l.title, '') || ' ' || COALESCE(l.rule_id, '') || ' '
                || COALESCE(l.canonical_name, '') || ' ' || COALESCE(l.description, '') || ' '
                || COALESCE(l.coding_agent, '') || ' ' || COALESCE(l.platform_code, '') || ' '
                || COALESCE(l.team_code, '')) LIKE CONCAT('%', LOWER(:search), '%'))
              AND (:codingAgent IS NULL OR l.coding_agent = :codingAgent)
              AND (:status IS NULL OR l.status = :status)
              AND (:teamCode IS NULL OR l.team_code = :teamCode)
              AND (:platformCode IS NULL OR l.platform_code = :platformCode)
              AND (:ruleKind IS NULL OR l.rule_kind = :ruleKind)
              AND (:scope IS NULL OR l.scope = :scope)
              AND (:approvalStatus IS NULL OR l.approval_status = :approvalStatus)
              AND (:lifecycleStatus IS NULL OR l.lifecycle_status = :lifecycleStatus)
              AND (:version IS NULL OR l.version = :version)
              AND (:tag IS NULL OR LOWER(COALESCE(l.tags_json, '')) LIKE CONCAT('%', LOWER(:tag), '%'))
              AND (
                  :hasDescription IS NULL
                  OR (:hasDescription = TRUE AND l.description IS NOT NULL AND BTRIM(l.description) <> '')
                  OR (:hasDescription = FALSE AND (l.description IS NULL OR BTRIM(l.description) = ''))
              )
              AND (
                  :cursorSavedAt IS NULL OR :cursorId IS NULL
                  OR (l.saved_at, l.id) < (:cursorSavedAt, :cursorId)
              )
            ORDER BY l.saved_at DESC, l.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RuleVersion> queryLatestForCatalog(
            @Param("search") String search,
            @Param("codingAgent") String codingAgent,
            @Param("status") String status,
            @Param("teamCode") String teamCode,
            @Param("platformCode") String platformCode,
            @Param("ruleKind") String ruleKind,
            @Param("scope") String scope,
            @Param("approvalStatus") String approvalStatus,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("tag") String tag,
            @Param("version") String version,
            @Param("hasDescription") Boolean hasDescription,
            @Param("cursorSavedAt") java.time.Instant cursorSavedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit
    );
}
