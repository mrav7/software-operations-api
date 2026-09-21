package io.github.mrav7.softwareoperationsapi.application;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkOrderHistoryRollbackIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestBean(methodName = "failingWorkLogRepository", enforceOverride = true)
    private WorkLogRepository workLogRepository;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void controlledHistoryPersistenceFailureRollsBackPlannedState() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "history-rollback-" + UUID.randomUUID(), "Rollback fixture"));
        WorkOrder created = workOrderService.create(
                component.getId(),
                "Investigate timeout",
                null,
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null);
        UUID workOrderId = created.getId();
        Instant originalUpdatedAt = created.getUpdatedAt();

        assertTrue(AopUtils.isAopProxy(workOrderService));
        assertThrows(IllegalStateException.class, () -> workOrderService.plan(workOrderId));
        FailingWorkLogRepositoryHandler handler = (FailingWorkLogRepositoryHandler)
                Proxy.getInvocationHandler(workLogRepository);
        assertEquals(1, handler.saveCalls());

        assertEquals(WorkOrderStatus.CREATED.name(), jdbcTemplate.queryForObject(
                "SELECT status FROM work_order WHERE id = ?", String.class, workOrderId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT planned_at FROM work_order WHERE id = ?", OffsetDateTime.class, workOrderId));
        assertEquals(originalUpdatedAt, jdbcTemplate.queryForObject(
                "SELECT updated_at FROM work_order WHERE id = ?", OffsetDateTime.class, workOrderId)
                .toInstant());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_log WHERE work_order_id = ?", Long.class, workOrderId));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM work_log");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM software_component");
    }

    private static WorkLogRepository failingWorkLogRepository() {
        return (WorkLogRepository) Proxy.newProxyInstance(
                WorkLogRepository.class.getClassLoader(),
                new Class<?>[] {WorkLogRepository.class},
                new FailingWorkLogRepositoryHandler());
    }

    private static final class FailingWorkLogRepositoryHandler implements InvocationHandler {
        private int saveCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("save")) {
                saveCalls++;
                throw new IllegalStateException("Controlled WorkLog persistence failure");
            }
            return switch (method.getName()) {
                case "toString" -> "Controlled failing WorkLogRepository";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        int saveCalls() {
            return saveCalls;
        }
    }
}
