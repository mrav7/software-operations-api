package io.github.mrav7.softwareoperationsapi.web;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;

/** Temporary process-local state; all entries are lost when the application stops. */
@Component
class TemporaryState {
    private final Map<UUID, SoftwareComponent> components = new ConcurrentHashMap<>();
    private final Map<UUID, WorkOrder> workOrders = new ConcurrentHashMap<>();

    void addComponent(SoftwareComponent component) {
        components.put(component.getId(), component);
    }

    Optional<SoftwareComponent> findComponent(UUID id) {
        return Optional.ofNullable(id == null ? null : components.get(id));
    }

    void addWorkOrder(WorkOrder workOrder) {
        workOrders.put(workOrder.getId(), workOrder);
    }

    Optional<WorkOrder> findWorkOrder(UUID id) {
        return Optional.ofNullable(workOrders.get(id));
    }
}
