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
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

@RestController
@RequestMapping("/api/work-orders")
class WorkOrderController {
    private final SoftwareComponentRepository componentRepository;
    private final WorkOrderRepository workOrderRepository;

    WorkOrderController(
            SoftwareComponentRepository componentRepository,
            WorkOrderRepository workOrderRepository) {
        this.componentRepository = componentRepository;
        this.workOrderRepository = workOrderRepository;
    }

    @PostMapping
    ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        SoftwareComponent component = componentRepository.findById(request.componentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Component", request.componentId()));
        if (!component.isActive()) {
            throw new InactiveComponentException(component.getId());
        }

        WorkOrder workOrder;
        try {
            workOrder = new WorkOrder(component, request.title(), request.description(),
                    request.type(), request.priority(), request.targetVersion());
        } catch (IllegalArgumentException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
        WorkOrder persisted = workOrderRepository.saveAndFlush(workOrder);
        return ResponseEntity.created(URI.create("/api/work-orders/" + persisted.getId()))
                .body(WorkOrderResponse.from(persisted));
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(WorkOrderResponse.from(requireWorkOrder(id)));
    }

    @PostMapping("/{id}/transitions")
    ResponseEntity<WorkOrderResponse> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        WorkOrder workOrder = requireWorkOrder(id);
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
        WorkOrder persisted = workOrderRepository.saveAndFlush(workOrder);
        return ResponseEntity.ok(WorkOrderResponse.from(persisted));
    }

    private WorkOrder requireWorkOrder(UUID id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order", id));
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
