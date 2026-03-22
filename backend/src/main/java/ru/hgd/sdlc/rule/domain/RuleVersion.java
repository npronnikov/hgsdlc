package ru.hgd.sdlc.rule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Convert;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.hgd.sdlc.common.StringListJsonConverter;

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

    @Column(length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "coding_agent", nullable = false, length = 64)
    private RuleProvider codingAgent;

    @Column(name = "rule_markdown", nullable = false, columnDefinition = "TEXT")
    private String ruleMarkdown;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "team_code", length = 128)
    private String teamCode;

    @Column(name = "platform_code", length = 32)
    private String platformCode;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private List<String> tags;

    @Column(name = "rule_kind", length = 64)
    private String ruleKind;

    @Column(name = "scope", length = 32)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 16)
    private RuleEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 32)
    private RuleApprovalStatus approvalStatus;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "source_path", length = 512)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", length = 16)
    private RuleContentSource contentSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 32)
    private RuleVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 32)
    private RuleLifecycleStatus lifecycleStatus;

    @Column(name = "saved_by", nullable = false, length = 128)
    private String savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}
