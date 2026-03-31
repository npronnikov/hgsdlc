package ru.hgd.sdlc.flow.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;

public interface FlowVersionRepository extends JpaRepository<FlowVersion, UUID> {
    Optional<FlowVersion> findFirstByCanonicalNameAndStatus(String canonicalName, FlowStatus status);
    Optional<FlowVersion> findFirstByCanonicalName(String canonicalName);

    Optional<FlowVersion> findFirstByFlowIdAndStatusOrderBySavedAtDesc(String flowId, FlowStatus status);

    Optional<FlowVersion> findFirstByFlowIdAndVersionOrderBySavedAtDesc(String flowId, String version);

    List<FlowVersion> findByFlowIdOrderBySavedAtDesc(String flowId);

    List<FlowVersion> findAllByOrderBySavedAtDesc();

    @Query(value = """
            WITH ranked AS (
                SELECT f.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY f.flow_id
                           ORDER BY CASE WHEN f.status = 'PUBLISHED' THEN 0 ELSE 1 END, f.saved_at DESC, f.id DESC
                       ) AS rn
                FROM flows f
            ),
            latest AS (
                SELECT *
                FROM ranked
                WHERE rn = 1
            )
            SELECT l.*
            FROM latest l
            WHERE (:search IS NULL OR LOWER(COALESCE(l.title, '') || ' ' || COALESCE(l.flow_id, '') || ' '
                || COALESCE(l.canonical_name, '') || ' ' || COALESCE(l.description, '') || ' '
                || COALESCE(l.coding_agent, '') || ' ' || COALESCE(l.platform_code, '') || ' '
                || COALESCE(l.team_code, '')) LIKE CONCAT('%', LOWER(:search), '%'))
              AND (:codingAgent IS NULL OR l.coding_agent = :codingAgent)
              AND (:teamCode IS NULL OR l.team_code = :teamCode)
              AND (:scope IS NULL OR l.scope = :scope)
              AND (:platformCode IS NULL OR l.platform_code = :platformCode)
              AND (:flowKind IS NULL OR l.flow_kind = :flowKind)
              AND (:riskLevel IS NULL OR l.risk_level = :riskLevel)
              AND (:lifecycleStatus IS NULL OR l.lifecycle_status = :lifecycleStatus)
              AND (:status IS NULL OR l.status = :status)
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
    List<FlowVersion> queryLatestForCatalog(
            @Param("search") String search,
            @Param("codingAgent") String codingAgent,
            @Param("teamCode") String teamCode,
            @Param("scope") String scope,
            @Param("platformCode") String platformCode,
            @Param("flowKind") String flowKind,
            @Param("riskLevel") String riskLevel,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("tag") String tag,
            @Param("status") String status,
            @Param("version") String version,
            @Param("hasDescription") Boolean hasDescription,
            @Param("cursorSavedAt") java.time.Instant cursorSavedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit
    );
}
