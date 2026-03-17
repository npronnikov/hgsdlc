package ru.hgd.sdlc.runtime.domain;

public enum RunStatus {
    CREATED,
    RUNNING,
    WAITING_GATE,
    COMPLETED,
    FAILED,
    CANCELLED
}
