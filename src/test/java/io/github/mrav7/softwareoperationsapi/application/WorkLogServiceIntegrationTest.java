package io.github.mrav7.softwareoperationsapi.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkLogServiceIntegrationTest {
    @Autowired
    private WorkLogService workLogService;

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
    void addNotePersistsExactMessageWithoutChangingWorkOrder() {
        WorkOrder workOrder = persistWorkOrder();
        Instant originalUpdatedAt = workOrder.getUpdatedAt();
        WorkOrderStatus originalStatus = workOrder.getStatus();

        WorkLog created = workLogService.addNote(workOrder.getId(), "  Diagnostic note  ");

        WorkLog persisted = workLogRepository.findById(created.getId()).orElseThrow();
        assertEquals(workOrder.getId(), persisted.getWorkOrder().getId());
        assertEquals(WorkLogType.NOTE, persisted.getType());
        assertEquals("  Diagnostic note  ", persisted.getMessage());
        assertNotNull(persisted.getCreatedAt());

        WorkOrder reloaded = workOrderRepository.findById(workOrder.getId()).orElseThrow();
        assertEquals(originalStatus, reloaded.getStatus());
        assertEquals(originalUpdatedAt, reloaded.getUpdatedAt());
    }

    @Test
    void addNoteForMissingWorkOrderThrowsNotFoundWithoutPersistence() {
        assertThrows(ResourceNotFoundException.class,
                () -> workLogService.addNote(UUID.randomUUID(), "Diagnostic note"));

        assertEquals(0, workLogRepository.count());
    }

    @Test
    void addNoteTranslatesBlankMessageAndDoesNotPersist() {
        WorkOrder workOrder = persistWorkOrder();

        assertThrows(InvalidDomainInputException.class,
                () -> workLogService.addNote(workOrder.getId(), "   "));

        assertEquals(0, workLogRepository.count());
    }

    @Test
    void listReturnsEmptyForExistingWorkOrder() {
        WorkOrder workOrder = persistWorkOrder();

        assertTrue(workLogService.list(workOrder.getId()).isEmpty());
    }

    @Test
    void listForMissingWorkOrderThrowsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> workLogService.list(UUID.randomUUID()));
    }

    @Test
    void listUsesCanonicalCreatedAtAndIdOrder() {
        WorkOrder workOrder = persistWorkOrder();
        workLogRepository.saveAndFlush(WorkLog.note(workOrder, "First note"));
        workLogRepository.saveAndFlush(WorkLog.statusChange(workOrder, "Future status change"));
        workLogRepository.saveAndFlush(WorkLog.note(workOrder, "Last note"));

        List<WorkLog> logs = workLogService.list(workOrder.getId());

        assertEquals(3, logs.size());
        assertCanonicalOrder(logs);
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void addNoteSupportsTerminalWorkOrders(WorkOrderStatus terminalStatus) {
        WorkOrder workOrder = persistWorkOrder();
        if (terminalStatus == WorkOrderStatus.COMPLETED) {
            workOrderService.plan(workOrder.getId());
            workOrderService.start(workOrder.getId());
            workOrderService.complete(workOrder.getId(), "Issue resolved");
        } else {
            workOrderService.cancel(workOrder.getId(), "No longer needed");
        }

        WorkLog created = workLogService.addNote(workOrder.getId(), "Terminal follow-up note");

        assertTrue(workLogRepository.existsById(created.getId()));
        assertEquals(terminalStatus,
                workOrderRepository.findById(workOrder.getId()).orElseThrow().getStatus());
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    private WorkOrder persistWorkOrder() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "work-log-service-" + UUID.randomUUID(), "WorkLog service fixture"));
        return workOrderRepository.saveAndFlush(new WorkOrder(
                component,
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null));
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
