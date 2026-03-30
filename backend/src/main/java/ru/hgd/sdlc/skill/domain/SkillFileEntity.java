package ru.hgd.sdlc.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skill_files")
public class SkillFileEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "skill_version_id", nullable = false)
    private UUID skillVersionId;

    @Column(name = "path", nullable = false, length = 1024)
    private String path;

    @Convert(converter = SkillFileRoleConverter.class)
    @Column(name = "role", nullable = false, length = 32)
    private SkillFileRole role;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "is_executable", nullable = false)
    private boolean executable;

    @Column(name = "text_content", nullable = false, columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
