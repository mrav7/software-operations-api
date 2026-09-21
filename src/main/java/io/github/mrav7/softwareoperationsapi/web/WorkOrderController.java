package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.application.InvalidDomainInputException;
import io.github.mrav7.softwareoperationsapi.application.UpdateWorkOrderCommand;
import io.github.mrav7.softwareoperationsapi.application.WorkOrderQuery;
import io.github.mrav7.softwareoperationsapi.application.WorkOrderService;
import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

@RestController
@RequestMapping("/api/work-orders")
class WorkOrderController {
    private final WorkOrderService workOrderService;

    WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    ResponseEntity<WorkOrderResponse> create(
            @Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrder workOrder = workOrderService.create(
                request.componentId(), request.title(), request.description(),
                request.type(), request.priority(), request.targetVersion());
        return ResponseEntity.created(URI.create("/api/work-orders/" + workOrder.getId()))
                .body(WorkOrderResponse.from(workOrder));
    }

    @GetMapping("/{id}")
    ResponseEntity<WorkOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.get(id)));
    }

    @GetMapping
    ResponseEntity<PageResponse<WorkOrderResponse>> list(
            @RequestParam(required = false) UUID componentId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) WorkOrderType type,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WorkOrder> workOrders = workOrderService.list(
                new WorkOrderQuery(componentId, status, type, priority, page, size));
        List<WorkOrderResponse> items = workOrders.getContent().stream()
                .map(WorkOrderResponse::from)
                .toList();
        return ResponseEntity.ok(PageResponse.from(workOrders, items));
    }

    @PatchMapping("/{id}")
    ResponseEntity<WorkOrderResponse> update(
            @PathVariable UUID id, @RequestBody UpdateWorkOrderRequest request) {
        if (!request.unknownFields().isEmpty()) {
            throw new InvalidDomainInputException(
                    "Work order update contains unsupported fields: "
                            + String.join(", ", request.unknownFields()));
        }

        UpdateWorkOrderCommand command = new UpdateWorkOrderCommand(
                request.componentIdPresent(), request.componentId(),
                request.typePresent(), request.type(),
                request.titlePresent(), request.title(),
                request.descriptionPresent(), request.description(),
                request.priorityPresent(), request.priority(),
                request.targetVersionPresent(), request.targetVersion());
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.modify(id, command)));
    }

    @PostMapping("/{id}/transitions")
    ResponseEntity<WorkOrderResponse> transition(
            @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        WorkOrder workOrder = switch (request.action()) {
            case PLAN -> workOrderService.plan(id);
            case START -> workOrderService.start(id);
            case BLOCK -> workOrderService.block(id, request.blockingReason());
            case RESUME -> workOrderService.resume(id);
            case COMPLETE -> workOrderService.complete(id, request.resolutionSummary());
            case CANCEL -> workOrderService.cancel(id, request.cancellationReason());
        };
        return ResponseEntity.ok(WorkOrderResponse.from(workOrder));
    }

    public record CreateWorkOrderRequest(
            @NotNull UUID componentId, @NotBlank String title, String description,
            @NotNull WorkOrderType type, @NotNull Priority priority, String targetVersion) {}

    public static final class UpdateWorkOrderRequest {
        private UUID componentId;
        private boolean componentIdPresent;
        private WorkOrderType type;
        private boolean typePresent;
        private String title;
        private boolean titlePresent;
        private String description;
        private boolean descriptionPresent;
        private Priority priority;
        private boolean priorityPresent;
        private String targetVersion;
        private boolean targetVersionPresent;
        private final Set<String> unknownFields = new LinkedHashSet<>();

        @JsonSetter("componentId")
        public void readComponentId(UUID componentId) {
            this.componentId = componentId;
            this.componentIdPresent = true;
        }

        @JsonSetter("type")
        public void readType(WorkOrderType type) {
            this.type = type;
            this.typePresent = true;
        }

        @JsonSetter("title")
        public void readTitle(String title) {
            this.title = title;
            this.titlePresent = true;
        }

        @JsonSetter("description")
        public void readDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }

        @JsonSetter("priority")
        public void readPriority(Priority priority) {
            this.priority = priority;
            this.priorityPresent = true;
        }

        @JsonSetter("targetVersion")
        public void readTargetVersion(String targetVersion) {
            this.targetVersion = targetVersion;
            this.targetVersionPresent = true;
        }

        @JsonAnySetter
        public void readUnknown(String field, Object ignoredValue) {
            unknownFields.add(field);
        }

        UUID componentId() {
            return componentId;
        }

        boolean componentIdPresent() {
            return componentIdPresent;
        }

        WorkOrderType type() {
            return type;
        }

        boolean typePresent() {
            return typePresent;
        }

        String title() {
            return title;
        }

        boolean titlePresent() {
            return titlePresent;
        }

        String description() {
            return description;
        }

        boolean descriptionPresent() {
            return descriptionPresent;
        }

        Priority priority() {
            return priority;
        }

        boolean priorityPresent() {
            return priorityPresent;
        }

        String targetVersion() {
            return targetVersion;
        }

        boolean targetVersionPresent() {
            return targetVersionPresent;
        }

        Set<String> unknownFields() {
            return Set.copyOf(unknownFields);
        }
    }

    public record TransitionRequest(
            @NotNull TransitionAction action, String blockingReason,
            String resolutionSummary, String cancellationReason) {}

    public enum TransitionAction {
        PLAN, START, BLOCK, RESUME, COMPLETE, CANCEL
    }

    public record WorkOrderResponse(
            UUID id, UUID componentId, String title, String description,
            WorkOrderType type, Priority priority, WorkOrderStatus status,
            String targetVersion, String blockingReason, Instant blockedAt,
            String resolutionSummary, String cancellationReason,
            Instant createdAt, Instant plannedAt, Instant startedAt,
            Instant completedAt, Instant updatedAt) {
        static WorkOrderResponse from(WorkOrder workOrder) {
            return new WorkOrderResponse(workOrder.getId(), workOrder.getComponent().getId(),
                    workOrder.getTitle(), workOrder.getDescription(), workOrder.getType(),
                    workOrder.getPriority(), workOrder.getStatus(), workOrder.getTargetVersion(),
                    workOrder.getBlockingReason(), workOrder.getBlockedAt(),
                    workOrder.getResolutionSummary(), workOrder.getCancellationReason(),
                    workOrder.getCreatedAt(), workOrder.getPlannedAt(), workOrder.getStartedAt(),
                    workOrder.getCompletedAt(), workOrder.getUpdatedAt());
        }
    }
}
