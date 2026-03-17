package ru.hgd.sdlc.runtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "node_execution_id")
    private UUID nodeExecutionId;

    @Column(name = "gate_id")
    private UUID gateId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private ActorType actorType;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
}
