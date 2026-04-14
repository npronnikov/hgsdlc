package ru.hgd.sdlc.runtime.domain;

import jakarta.persistence.Column;
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
@Table(name = "gate_chat_messages")
public class GateChatMessageEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gate_id", nullable = false, updatable = false)
    private UUID gateId;

    @Column(nullable = false, length = 8)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
