package io.github.mrav7.softwareoperationsapi.application;

import java.util.UUID;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

public record UpdateWorkOrderCommand(
        boolean componentIdPresent,
        UUID componentId,
        boolean typePresent,
        WorkOrderType type,
        boolean titlePresent,
        String title,
        boolean descriptionPresent,
        String description,
        boolean priorityPresent,
        Priority priority,
        boolean targetVersionPresent,
        String targetVersion) {

    public UpdateWorkOrderCommand {
        if (!componentIdPresent && !typePresent && !titlePresent
                && !descriptionPresent && !priorityPresent && !targetVersionPresent) {
            throw new InvalidDomainInputException("At least one work order field must be provided");
        }
    }
}
