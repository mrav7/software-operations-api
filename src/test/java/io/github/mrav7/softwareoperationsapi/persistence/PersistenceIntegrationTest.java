package io.github.mrav7.softwareoperationsapi.persistence;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PersistenceIntegrationTest {
    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void softwareComponentWithAssignedUuidRoundTripsAfterPersistenceContextClear() {
        SoftwareComponent original = new SoftwareComponent(
                "configuration-service-" + UUID.randomUUID(), "Configuration API");
        UUID assignedId = original.getId();
        assertNotNull(assignedId);

        componentRepository.save(original);
        entityManager.flush();
        entityManager.clear();

        SoftwareComponent reloaded = componentRepository.findById(assignedId).orElseThrow();
        assertNotSame(original, reloaded);
        assertEquals(assignedId, reloaded.getId());
        assertEquals(original.getName(), reloaded.getName());
        assertEquals(original.getDescription(), reloaded.getDescription());
        assertTrue(reloaded.isActive());
        assertEquals(original.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(original.getUpdatedAt(), reloaded.getUpdatedAt());
    }

    @Test
    void richWorkOrderRoundTripsAfterPersistenceContextClear() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "operations-service-" + UUID.randomUUID(), "Operations API"));
        WorkOrder original = new WorkOrder(component, "Investigate infrastructure access",
                "Access required for the maintenance window",
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.CRITICAL, null);
        original.plan();
        original.start();
        original.block("Waiting for infrastructure access");
        UUID assignedId = original.getId();

        workOrderRepository.save(original);
        entityManager.flush();
        entityManager.clear();

        WorkOrder reloaded = workOrderRepository.findById(assignedId).orElseThrow();
        assertNotSame(original, reloaded);
        assertEquals(assignedId, reloaded.getId());
        assertEquals(component.getId(), reloaded.getComponent().getId());
        assertEquals(original.getTitle(), reloaded.getTitle());
        assertEquals(original.getDescription(), reloaded.getDescription());
        assertEquals(WorkOrderType.CORRECTIVE_MAINTENANCE, reloaded.getType());
        assertEquals(Priority.CRITICAL, reloaded.getPriority());
        assertEquals(WorkOrderStatus.BLOCKED, reloaded.getStatus());
        assertNull(reloaded.getTargetVersion());
        assertEquals("Waiting for infrastructure access", reloaded.getBlockingReason());
        assertEquals(original.getBlockedAt(), reloaded.getBlockedAt());
        assertEquals(original.getStartedAt(), reloaded.getStartedAt());
        assertEquals(original.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(original.getUpdatedAt(), reloaded.getUpdatedAt());
        assertNull(reloaded.getResolutionSummary());
        assertNull(reloaded.getCancellationReason());
        assertNull(reloaded.getCompletedAt());
    }

    @Test
    void duplicateComponentNameIsRejectedByPostgresqlUniqueConstraint() {
        String name = "duplicate-component-" + UUID.randomUUID();
        componentRepository.saveAndFlush(new SoftwareComponent(name, "First"));

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> componentRepository.saveAndFlush(new SoftwareComponent(name, "Second")));
        assertEquals("23505", sqlState(exception));
    }

    @Test
    void nonexistentComponentIsRejectedByPostgresqlForeignKey() {
        UUID missingComponentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO work_order (
                            id, component_id, title, type, priority, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, workOrderId, missingComponentId, "Invalid reference",
                        "OPERATIONAL_SUPPORT", "MEDIUM", "CREATED"));
        assertEquals("23503", sqlState(exception));
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
        assertEquals("2", version);
    }

    private static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
