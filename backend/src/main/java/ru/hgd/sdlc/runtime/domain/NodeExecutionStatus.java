package ru.hgd.sdlc.runtime.domain;

public enum NodeExecutionStatus {
    CREATED,
    RUNNING,
    WAITING_GATE,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
