package io.github.mrav7.softwareoperationsapi.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkLogTest {

    @Test
    void noteCreatesAppendOnlyEntryAndSynchronizesInverseRelationship() {
        WorkOrder workOrder = workOrder();

        WorkLog log = WorkLog.note(workOrder, "Reproduced connection failure.");

        assertAll(
                () -> assertNotNull(log.getId()),
                () -> assertNotNull(log.getCreatedAt()),
                () -> assertSame(workOrder, log.getWorkOrder()),
                () -> assertEquals(WorkLogType.NOTE, log.getType()),
                () -> assertEquals("Reproduced connection failure.", log.getMessage()),
                () -> assertEquals(1, workOrder.getLogs().size()),
                () -> assertSame(log, workOrder.getLogs().getFirst()));
    }

    @Test
    void statusChangeCreatesStatusChangeEntry() {
        WorkLog log = WorkLog.statusChange(workOrder(), "Work order planned.");

        assertEquals(WorkLogType.STATUS_CHANGE, log.getType());
    }

    @Test
    void workOrderIsRequired() {
        assertThrows(NullPointerException.class,
                () -> WorkLog.note(null, "Operational note"));
    }

    @Test
    void messageIsRequired() {
        assertThrows(NullPointerException.class,
                () -> WorkLog.note(workOrder(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void messageMustNotBeBlank(String message) {
        assertThrows(IllegalArgumentException.class,
                () -> WorkLog.note(workOrder(), message));
    }

    @Test
    void messageIsNotNormalized() {
        WorkLog log = WorkLog.note(workOrder(), "  Kept exactly as entered.  ");

        assertEquals("  Kept exactly as entered.  ", log.getMessage());
    }

    @Test
    void exposedLogsCannotMutateRelationship() {
        WorkOrder workOrder = workOrder();
        WorkLog.note(workOrder, "Operational note");

        assertThrows(UnsupportedOperationException.class, () -> workOrder.getLogs().clear());
        assertEquals(1, workOrder.getLogs().size());
    }

    private static WorkOrder workOrder() {
        return new WorkOrder(
                new SoftwareComponent("configuration-service", "Configuration API"),
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null);
    }
}
