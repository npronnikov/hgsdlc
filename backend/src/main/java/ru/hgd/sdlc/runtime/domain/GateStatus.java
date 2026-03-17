package ru.hgd.sdlc.runtime.domain;

public enum GateStatus {
    AWAITING_INPUT,
    SUBMITTED,
    AWAITING_DECISION,
    APPROVED,
    REWORK_REQUESTED,
    FAILED_VALIDATION,
    CANCELLED
}
