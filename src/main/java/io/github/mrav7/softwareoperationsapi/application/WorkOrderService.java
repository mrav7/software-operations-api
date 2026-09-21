package io.github.mrav7.softwareoperationsapi.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

@Service
public class WorkOrderService {
    private final SoftwareComponentRepository componentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkLogRepository workLogRepository;

    public WorkOrderService(
            SoftwareComponentRepository componentRepository,
            WorkOrderRepository workOrderRepository,
            WorkLogRepository workLogRepository) {
        this.componentRepository = componentRepository;
        this.workOrderRepository = workOrderRepository;
        this.workLogRepository = workLogRepository;
    }

    @Transactional
    public WorkOrder create(
            UUID componentId,
            String title,
            String description,
            WorkOrderType type,
            Priority priority,
            String targetVersion) {
        SoftwareComponent component = requireComponent(componentId);
        requireActive(component);

        WorkOrder workOrder;
        try {
            workOrder = new WorkOrder(
                    component, title, description, type, priority, targetVersion);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
        return workOrderRepository.save(workOrder);
    }

    public WorkOrder get(UUID id) {
        return requireWorkOrder(id);
    }

    public Page<WorkOrder> list(WorkOrderQuery query) {
        validatePage(query.page(), query.size());
        PageRequest pageable = PageRequest.of(query.page(), query.size(), Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return workOrderRepository.findAllFiltered(
                query.componentId(), query.status(), query.type(), query.priority(), pageable);
    }

    @Transactional
    public WorkOrder modify(UUID id, UpdateWorkOrderCommand command) {
        WorkOrder workOrder = requireWorkOrder(id);

        if (command.componentIdPresent()) {
            reassignComponent(workOrder, command.componentId());
        }
        if (command.titlePresent()) {
            applyInput(() -> workOrder.changeTitle(command.title()));
        }
        if (command.descriptionPresent()) {
            workOrder.changeDescription(command.description());
        }
        if (command.priorityPresent()) {
            applyInput(() -> workOrder.changePriority(command.priority()));
        }
        if (command.typePresent() && command.targetVersionPresent()) {
            applyInput(() -> workOrder.changeTypeAndTargetVersion(
                    command.type(), command.targetVersion()));
        } else if (command.typePresent()) {
            applyInput(() -> workOrder.changeType(command.type()));
        } else if (command.targetVersionPresent()) {
            applyInput(() -> workOrder.changeTargetVersion(command.targetVersion()));
        }

        return workOrder;
    }

    @Transactional
    public WorkOrder plan(UUID id) {
        WorkOrder workOrder = requireWorkOrder(id);
        workOrder.plan();
        recordStatusChange(workOrder, "Work order planned.");
        return workOrder;
    }

    @Transactional
    public WorkOrder start(UUID id) {
        WorkOrder workOrder = requireWorkOrder(id);
        workOrder.start();
        recordStatusChange(workOrder, "Work order started.");
        return workOrder;
    }

    @Transactional
    public WorkOrder block(UUID id, String blockingReason) {
        WorkOrder workOrder = requireWorkOrder(id);
        translateTransitionInput(() -> workOrder.block(blockingReason));
        recordStatusChange(workOrder,
                "Work order blocked: " + workOrder.getBlockingReason());
        return workOrder;
    }

    @Transactional
    public WorkOrder resume(UUID id) {
        WorkOrder workOrder = requireWorkOrder(id);
        workOrder.resume();
        recordStatusChange(workOrder, "Work order resumed.");
        return workOrder;
    }

    @Transactional
    public WorkOrder complete(UUID id, String resolutionSummary) {
        WorkOrder workOrder = requireWorkOrder(id);
        translateTransitionInput(() -> workOrder.complete(resolutionSummary));
        recordStatusChange(workOrder,
                "Work order completed: " + workOrder.getResolutionSummary());
        return workOrder;
    }

    @Transactional
    public WorkOrder cancel(UUID id, String cancellationReason) {
        WorkOrder workOrder = requireWorkOrder(id);
        translateTransitionInput(() -> workOrder.cancel(cancellationReason));
        recordStatusChange(workOrder,
                "Work order cancelled: " + workOrder.getCancellationReason());
        return workOrder;
    }

    private void recordStatusChange(WorkOrder workOrder, String message) {
        workLogRepository.save(WorkLog.statusChange(workOrder, message));
    }

    private void reassignComponent(WorkOrder workOrder, UUID componentId) {
        if (componentId == null) {
            throw new InvalidDomainInputException("componentId must not be null");
        }
        if (Objects.equals(workOrder.getComponent().getId(), componentId)) {
            return;
        }

        SoftwareComponent target = requireComponent(componentId);
        requireActive(target);
        workOrder.changeComponent(target);
    }

    private SoftwareComponent requireComponent(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component", id));
    }

    private WorkOrder requireWorkOrder(UUID id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order", id));
    }

    private static void requireActive(SoftwareComponent component) {
        if (!component.isActive()) {
            throw new InactiveComponentException(component.getId());
        }
    }

    private static void applyInput(Runnable mutation) {
        try {
            mutation.run();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
    }

    private static void translateTransitionInput(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException("Transition input is invalid");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidDomainInputException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidDomainInputException("size must be between 1 and 100");
        }
    }
}
