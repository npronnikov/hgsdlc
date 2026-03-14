package ru.hgd.sdlc.skill.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skills")
public class SkillVersion {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "skill_id", nullable = false, length = 255)
    private String skillId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "canonical_name", nullable = false, length = 255, unique = true)
    private String canonicalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillStatus status;

    @Column(name = "skill_markdown", nullable = false, columnDefinition = "TEXT")
    private String skillMarkdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_model_json", columnDefinition = "jsonb")
    private JsonNode skillModelJson;

    @Column(name = "skill_checksum", length = 128)
    private String skillChecksum;

    @Column(name = "saved_by", nullable = false, length = 128)
    private String savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}
