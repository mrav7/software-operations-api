package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

@RestController
@RequestMapping("/api/work-orders")
class WorkOrderController {
    private final TemporaryState state;

    WorkOrderController(TemporaryState state) {
        this.state = state;
    }

    @PostMapping
    ResponseEntity<WorkOrderResponse> create(@RequestBody CreateWorkOrderRequest request) {
        SoftwareComponent component = state.findComponent(request.componentId()).orElse(null);
        if (component == null) {
            return ResponseEntity.notFound().build();
        }
        WorkOrder workOrder = new WorkOrder(component, request.title(), request.description(),
                request.type(), request.priority(), request.targetVersion());
        state.addWorkOrder(workOrder);
        return ResponseEntity.created(URI.create("/api/work-orders/" + workOrder.getId()))
                .body(WorkOrderResponse.from(workOrder));
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.of(state.findWorkOrder(id).map(WorkOrderResponse::from));
    }

    public record CreateWorkOrderRequest(
            UUID componentId, String title, String description,
            WorkOrderType type, Priority priority, String targetVersion) {}

    public record WorkOrderResponse(
            UUID id, UUID componentId, String title, String description,
            WorkOrderType type, Priority priority, WorkOrderStatus status,
            String targetVersion, Instant createdAt, Instant updatedAt) {
        static WorkOrderResponse from(WorkOrder workOrder) {
            return new WorkOrderResponse(workOrder.getId(), workOrder.getComponent().getId(),
                    workOrder.getTitle(), workOrder.getDescription(), workOrder.getType(),
                    workOrder.getPriority(), workOrder.getStatus(), workOrder.getTargetVersion(),
                    workOrder.getCreatedAt(), workOrder.getUpdatedAt());
        }
    }
}
