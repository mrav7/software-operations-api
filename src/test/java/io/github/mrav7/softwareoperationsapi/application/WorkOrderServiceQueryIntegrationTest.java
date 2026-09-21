package io.github.mrav7.softwareoperationsapi.application;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

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
class WorkOrderServiceQueryIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    private SoftwareComponent component;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        component = componentRepository.saveAndFlush(new SoftwareComponent(
                "service-query-" + UUID.randomUUID(), "Service query fixture"));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void listPreservesFilteredPageMetadataAndFixedOrder() {
        WorkOrder older = persist(Priority.HIGH, WorkOrderStatus.CREATED);
        WorkOrder newer = persist(Priority.CRITICAL, WorkOrderStatus.BLOCKED);

        Page<WorkOrder> result = workOrderService.list(new WorkOrderQuery(
                component.getId(), WorkOrderStatus.BLOCKED, null, Priority.CRITICAL, 0, 2));

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(List.of(newer.getId()), result.getContent().stream()
                .map(WorkOrder::getId).toList());
        assertEquals(WorkOrderStatus.CREATED,
                workOrderRepository.findById(older.getId()).orElseThrow().getStatus());
    }

    @Test
    void listRejectsNegativePage() {
        assertThrows(InvalidDomainInputException.class, () -> workOrderService.list(
                new WorkOrderQuery(null, null, null, null, -1, 20)));
    }

    @Test
    void listRejectsSizesOutsideTheFrozenBounds() {
        assertThrows(InvalidDomainInputException.class, () -> workOrderService.list(
                new WorkOrderQuery(null, null, null, null, 0, 0)));
        assertThrows(InvalidDomainInputException.class, () -> workOrderService.list(
                new WorkOrderQuery(null, null, null, null, 0, 101)));
    }

    private WorkOrder persist(Priority priority, WorkOrderStatus status) {
        WorkOrder order = new WorkOrder(component, "Service query " + UUID.randomUUID(), null,
                WorkOrderType.CORRECTIVE_MAINTENANCE, priority, null);
        if (status == WorkOrderStatus.BLOCKED) {
            order.plan();
            order.start();
            order.block("Fixture block");
        }
        return workOrderRepository.saveAndFlush(order);
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }
}
