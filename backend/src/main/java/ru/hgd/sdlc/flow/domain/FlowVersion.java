package ru.hgd.sdlc.flow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
@Table(name = "flows")
public class FlowVersion {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "flow_id", nullable = false, length = 255)
    private String flowId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "canonical_name", nullable = false, length = 255, unique = true)
    private String canonicalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FlowStatus status;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1024)
    private String description;

    @Column(name = "start_role", nullable = false, length = 255)
    private String startRole;

    @Column(name = "approver_role", nullable = false, length = 255)
    private String approverRole;

    @Column(name = "start_node_id", nullable = false, length = 255)
    private String startNodeId;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "rule_refs", columnDefinition = "TEXT")
    private List<String> ruleRefs;

    @Column(name = "flow_yaml", nullable = false, columnDefinition = "TEXT")
    private String flowYaml;

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
