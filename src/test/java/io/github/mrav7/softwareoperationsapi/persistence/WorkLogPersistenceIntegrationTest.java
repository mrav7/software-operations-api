package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
import io.github.mrav7.softwareoperationsapi.domain.WorkLogType;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkLogPersistenceIntegrationTest {
    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void workLogRoundTripsThroughPostgresqlWithOwningRelationship() {
        WorkOrder workOrder = persistWorkOrder();
        WorkLog original = WorkLog.note(workOrder, "Reproduced connection failure.");
        UUID logId = original.getId();

        workLogRepository.saveAndFlush(original);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.clear();
            WorkLog reloaded = workLogRepository.findById(logId).orElseThrow();

            assertNotSame(original, reloaded);
            assertEquals(logId, reloaded.getId());
            assertEquals(workOrder.getId(), reloaded.getWorkOrder().getId());
            assertEquals(WorkLogType.NOTE, reloaded.getType());
            assertEquals("Reproduced connection failure.", reloaded.getMessage());
            assertEquals(original.getCreatedAt(), reloaded.getCreatedAt());
        });
    }

    @Test
    void oneWorkOrderPersistsManyLogsInCanonicalOrderAndLoadsInverseSide() {
        WorkOrder workOrder = persistWorkOrder();
        WorkLog first = WorkLog.note(workOrder, "Reproduced connection failure.");
        WorkLog second = WorkLog.statusChange(workOrder, "Work order blocked.");
        WorkLog third = WorkLog.note(workOrder, "Database timeout identified.");

        workLogRepository.saveAndFlush(third);
        workLogRepository.saveAndFlush(first);
        workLogRepository.saveAndFlush(second);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.clear();
            List<WorkLog> logs = workLogRepository
                    .findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrder.getId());

            assertEquals(3, logs.size());
            assertTrue(logs.stream()
                    .allMatch(log -> log.getWorkOrder().getId().equals(workOrder.getId())));
            assertTrue(logs.stream().anyMatch(log -> log.getType() == WorkLogType.NOTE));
            assertTrue(logs.stream()
                    .anyMatch(log -> log.getType() == WorkLogType.STATUS_CHANGE));
            assertCanonicalOrder(logs);
        });

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.clear();
            WorkOrder reloaded = workOrderRepository.findById(workOrder.getId()).orElseThrow();

            assertNotSame(workOrder, reloaded);
            assertEquals(3, reloaded.getLogs().size());
            assertTrue(reloaded.getLogs().stream()
                    .allMatch(log -> log.getWorkOrder() == reloaded));
        });
    }

    @Test
    void latestFlywayVersionIsAppliedAndRecorded() {
        Boolean historyTableExists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class);
        String version = jdbcTemplate.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class);

        assertTrue(historyTableExists);
        assertEquals("3", version);
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    private WorkOrder persistWorkOrder() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "work-log-component-" + UUID.randomUUID(), "WorkLog persistence fixture"));
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
