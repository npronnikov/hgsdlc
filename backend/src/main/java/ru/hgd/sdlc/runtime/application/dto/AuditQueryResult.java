package ru.hgd.sdlc.runtime.application.dto;

import java.util.List;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;

public record AuditQueryResult(
        List<AuditEventEntity> events,
        Long nextCursor,
        boolean hasMore
) {}

