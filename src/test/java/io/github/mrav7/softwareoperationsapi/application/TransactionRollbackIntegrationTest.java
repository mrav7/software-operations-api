package io.github.mrav7.softwareoperationsapi.application;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class TransactionRollbackIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @BeforeEach
    void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @Test
    void failedLaterMutationRollsBackEarlierManagedEntityChanges() {
        SoftwareComponent originalComponent = componentRepository.saveAndFlush(
                new SoftwareComponent("rollback-original-component", null));
        SoftwareComponent targetComponent = componentRepository.saveAndFlush(
                new SoftwareComponent("rollback-target-component", null));
        WorkOrder created = workOrderService.create(
                originalComponent.getId(),
                "Original title",
                "Rollback acceptance",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null);

        UUID workOrderId = created.getId();
        Instant originalUpdatedAt = workOrderRepository.findById(workOrderId)
                .orElseThrow()
                .getUpdatedAt();
        UpdateWorkOrderCommand reassignThenFail = new UpdateWorkOrderCommand(
                true, targetComponent.getId(),
                false, null,
                true, "   ",
                false, null,
                false, null,
                false, null);

        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.modify(workOrderId, reassignThenFail));

        WorkOrder persisted = workOrderRepository.findById(workOrderId).orElseThrow();
        assertEquals(originalComponent.getId(), persisted.getComponent().getId());
        assertEquals("Original title", persisted.getTitle());
        assertEquals(originalUpdatedAt, persisted.getUpdatedAt());
        assertEquals(WorkOrderStatus.CREATED, persisted.getStatus());
    }
}
