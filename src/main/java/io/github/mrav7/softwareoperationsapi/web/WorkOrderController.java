package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        SoftwareComponent component = state.requireComponent(request.componentId());
        WorkOrder workOrder;
        try {
            workOrder = new WorkOrder(component, request.title(), request.description(),
                    request.type(), request.priority(), request.targetVersion());
        } catch (IllegalArgumentException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
        state.addWorkOrder(workOrder);
        return ResponseEntity.created(URI.create("/api/work-orders/" + workOrder.getId()))
                .body(WorkOrderResponse.from(workOrder));
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(WorkOrderResponse.from(state.requireWorkOrder(id)));
    }

    @PostMapping("/{id}/transitions")
    ResponseEntity<WorkOrderResponse> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        WorkOrder workOrder = state.requireWorkOrder(id);
        switch (request.action()) {
            case PLAN -> workOrder.plan();
            case START -> workOrder.start();
            case BLOCK -> translateTransitionInput(() -> workOrder.block(request.blockingReason()));
            case RESUME -> workOrder.resume();
            case COMPLETE -> translateTransitionInput(
                    () -> workOrder.complete(request.resolutionSummary()));
            case CANCEL -> translateTransitionInput(
                    () -> workOrder.cancel(request.cancellationReason()));
        }
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    private static void translateTransitionInput(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException("Transition input is invalid");
        }
    }

    public record CreateWorkOrderRequest(
            @NotNull UUID componentId, @NotBlank String title, String description,
            @NotNull WorkOrderType type, @NotNull Priority priority, String targetVersion) {}

    public record TransitionRequest(
            @NotNull TransitionAction action, String blockingReason,
            String resolutionSummary, String cancellationReason) {}

    public enum TransitionAction {
        PLAN, START, BLOCK, RESUME, COMPLETE, CANCEL
    }

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
