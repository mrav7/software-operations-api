package io.github.mrav7.softwareoperationsapi.application;

import java.util.UUID;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

/** Query intent for the paginated WorkOrder collection. */
public record WorkOrderQuery(
        UUID componentId,
        WorkOrderStatus status,
        WorkOrderType type,
        Priority priority,
        int page,
        int size) {
}
