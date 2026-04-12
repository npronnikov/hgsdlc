--liquibase formatted sql

--changeset hgd:047-gate-chat-messages
CREATE TABLE gate_chat_messages (
    id          UUID        NOT NULL,
    gate_id     UUID        NOT NULL REFERENCES gate_instances(id),
    role        VARCHAR(8)  NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_gate_chat_messages PRIMARY KEY (id)
);

CREATE INDEX idx_gate_chat_messages_gate ON gate_chat_messages (gate_id, created_at);
