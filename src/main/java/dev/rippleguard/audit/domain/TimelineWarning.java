package dev.rippleguard.audit.domain;

public enum TimelineWarning {
    EVENT_GAP_DETECTED,
    RETENTION_LIMIT,
    LATE_EVENT_PENDING,
    INVALID_REFERENCE
}
