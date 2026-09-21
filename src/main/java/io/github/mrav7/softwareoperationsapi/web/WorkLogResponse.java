package io.github.mrav7.softwareoperationsapi.web;

import java.time.Instant;
import java.util.UUID;

import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkLogType;

public record WorkLogResponse(
        UUID id,
        WorkLogType type,
        String message,
        Instant createdAt) {
    static WorkLogResponse from(WorkLog workLog) {
        return new WorkLogResponse(
                workLog.getId(), workLog.getType(), workLog.getMessage(), workLog.getCreatedAt());
    }
}
