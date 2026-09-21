package io.github.mrav7.softwareoperationsapi.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

@Service
public class WorkLogService {
    private final WorkOrderRepository workOrderRepository;
    private final WorkLogRepository workLogRepository;

    public WorkLogService(
            WorkOrderRepository workOrderRepository,
            WorkLogRepository workLogRepository) {
        this.workOrderRepository = workOrderRepository;
        this.workLogRepository = workLogRepository;
    }

    @Transactional
    public WorkLog addNote(UUID workOrderId, String message) {
        WorkOrder workOrder = requireWorkOrder(workOrderId);

        WorkLog workLog;
        try {
            workLog = WorkLog.note(workOrder, message);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
        return workLogRepository.save(workLog);
    }

    public List<WorkLog> list(UUID workOrderId) {
        requireWorkOrder(workOrderId);
        return workLogRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrderId);
    }

    private WorkOrder requireWorkOrder(UUID id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order", id));
    }
}
