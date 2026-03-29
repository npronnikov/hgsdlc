package ru.hgd.sdlc.runtime.domain;

public enum RunStatus {
    CREATED,
    RUNNING,
    WAITING_GATE,
    WAITING_PUBLISH,
    PUBLISH_FAILED,
    COMPLETED,
    FAILED,
    CANCELLED
}
