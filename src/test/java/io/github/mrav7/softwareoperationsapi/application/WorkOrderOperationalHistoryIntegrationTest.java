package io.github.mrav7.softwareoperationsapi.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.mrav7.softwareoperationsapi.domain.InvalidWorkOrderStateException;
import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkLogType;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkOrderOperationalHistoryIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void successfulLifecyclePersistsExactHistoryAndPreservesSnapshotSemantics() {
        WorkOrder workOrder = createWorkOrder();

        workOrderService.plan(workOrder.getId());
        assertEquals(WorkOrderStatus.PLANNED, reload(workOrder).getStatus());

        workOrderService.start(workOrder.getId());
        WorkOrder started = reload(workOrder);
        assertEquals(WorkOrderStatus.IN_PROGRESS, started.getStatus());
        Instant originalStartedAt = started.getStartedAt();
        assertNotNull(originalStartedAt);

        workOrderService.block(workOrder.getId(), "Waiting for access");
        WorkOrder blocked = reload(workOrder);
        assertEquals(WorkOrderStatus.BLOCKED, blocked.getStatus());
        assertEquals("Waiting for access", blocked.getBlockingReason());
        assertNotNull(blocked.getBlockedAt());

        workOrderService.resume(workOrder.getId());
        WorkOrder resumed = reload(workOrder);
        assertEquals(WorkOrderStatus.IN_PROGRESS, resumed.getStatus());
        assertNull(resumed.getBlockingReason());
        assertNull(resumed.getBlockedAt());
        assertEquals(originalStartedAt, resumed.getStartedAt());
        assertEquals(List.of(
                        "Work order planned.",
                        "Work order started.",
                        "Work order blocked: Waiting for access",
                        "Work order resumed."),
                messages(workOrder.getId()));

        workOrderService.complete(workOrder.getId(), "Issue resolved");
        WorkOrder completed = reload(workOrder);
        assertEquals(WorkOrderStatus.COMPLETED, completed.getStatus());
        assertEquals("Issue resolved", completed.getResolutionSummary());
        assertNotNull(completed.getCompletedAt());

        List<WorkLog> logs = logs(workOrder.getId());
        assertEquals(5, logs.size());
        assertTrue(logs.stream().allMatch(log -> log.getType() == WorkLogType.STATUS_CHANGE));
        assertTrue(logs.stream()
                .allMatch(log -> log.getWorkOrder().getId().equals(workOrder.getId())));
        assertEquals(List.of(
                        "Work order planned.",
                        "Work order started.",
                        "Work order blocked: Waiting for access",
                        "Work order resumed.",
                        "Work order completed: Issue resolved"),
                logs.stream().map(WorkLog::getMessage).toList());
        assertCanonicalOrder(logs);
    }

    @Test
    void cancellationFromCreatedPersistsExactStatusChange() {
        WorkOrder workOrder = createWorkOrder();

        workOrderService.cancel(workOrder.getId(), "No longer needed");

        WorkOrder cancelled = reload(workOrder);
        assertEquals(WorkOrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("No longer needed", cancelled.getCancellationReason());
        List<WorkLog> logs = logs(workOrder.getId());
        assertEquals(1, logs.size());
        assertEquals(WorkLogType.STATUS_CHANGE, logs.getFirst().getType());
        assertEquals("Work order cancelled: No longer needed", logs.getFirst().getMessage());
    }

    @Test
    void cancellationFromBlockedPreservesBlockHistoryAndClearsCurrentSnapshot() {
        WorkOrder workOrder = createWorkOrder();
        workOrderService.plan(workOrder.getId());
        workOrderService.start(workOrder.getId());
        workOrderService.block(workOrder.getId(), "Waiting for maintenance window");

        workOrderService.cancel(workOrder.getId(), "Maintenance no longer required");

        WorkOrder cancelled = reload(workOrder);
        assertEquals(WorkOrderStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getBlockingReason());
        assertNull(cancelled.getBlockedAt());
        assertEquals("Maintenance no longer required", cancelled.getCancellationReason());
        assertEquals(List.of(
                        "Work order planned.",
                        "Work order started.",
                        "Work order blocked: Waiting for maintenance window",
                        "Work order cancelled: Maintenance no longer required"),
                messages(workOrder.getId()));
    }

    @Test
    void invalidStateCreatesNoHistory() {
        WorkOrder workOrder = createWorkOrder();

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.complete(workOrder.getId(), "Invalid"));

        assertEquals(WorkOrderStatus.CREATED, reload(workOrder).getStatus());
        assertTrue(logs(workOrder.getId()).isEmpty());
    }

    @Test
    void invalidTransitionInputCreatesNoAdditionalHistory() {
        WorkOrder workOrder = createWorkOrder();
        workOrderService.plan(workOrder.getId());
        workOrderService.start(workOrder.getId());

        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.block(workOrder.getId(), "   "));

        assertEquals(WorkOrderStatus.IN_PROGRESS, reload(workOrder).getStatus());
        assertEquals(List.of("Work order planned.", "Work order started."),
                messages(workOrder.getId()));
    }

    @Test
    void createAndModifyDoNotProduceAutomaticHistory() {
        WorkOrder workOrder = createWorkOrder();
        assertTrue(logs(workOrder.getId()).isEmpty());

        workOrderService.modify(workOrder.getId(), new UpdateWorkOrderCommand(
                false, null,
                false, null,
                true, "Updated investigation",
                false, null,
                true, Priority.CRITICAL,
                false, null));

        WorkOrder modified = reload(workOrder);
        assertEquals("Updated investigation", modified.getTitle());
        assertEquals(Priority.CRITICAL, modified.getPriority());
        assertTrue(logs(workOrder.getId()).isEmpty());
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    private WorkOrder createWorkOrder() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "history-component-" + UUID.randomUUID(), "Operational history fixture"));
        return workOrderService.create(
                component.getId(),
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null);
    }

    private WorkOrder reload(WorkOrder workOrder) {
        return workOrderRepository.findById(workOrder.getId()).orElseThrow();
    }

    private List<WorkLog> logs(UUID workOrderId) {
        return workLogRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrderId);
    }

    private List<String> messages(UUID workOrderId) {
        return logs(workOrderId).stream().map(WorkLog::getMessage).toList();
    }

    private static void assertCanonicalOrder(List<WorkLog> logs) {
        for (int index = 0; index < logs.size() - 1; index++) {
            WorkLog current = logs.get(index);
            WorkLog next = logs.get(index + 1);
            int timestampComparison = current.getCreatedAt().compareTo(next.getCreatedAt());

            assertFalse(timestampComparison > 0);
            if (timestampComparison == 0) {
                assertTrue(current.getId().compareTo(next.getId()) <= 0);
            }
        }
    }
}
