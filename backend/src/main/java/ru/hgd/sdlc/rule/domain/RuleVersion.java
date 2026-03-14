package ru.hgd.sdlc.rule.domain;

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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rules")
public class RuleVersion {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_id", nullable = false, length = 255)
    private String ruleId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "canonical_name", nullable = false, length = 255, unique = true)
    private String canonicalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleStatus status;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private RuleProvider provider;

    @Column(name = "rule_markdown", nullable = false, columnDefinition = "TEXT")
    private String ruleMarkdown;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "saved_by", nullable = false, length = 128)
    private String savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}
