package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkOrderQueryIntegrationTest {
    private static final Sort FIXED_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    private SoftwareComponent firstComponent;
    private SoftwareComponent secondComponent;
    private List<WorkOrder> orders;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        firstComponent = componentRepository.saveAndFlush(new SoftwareComponent(
                "query-first-" + UUID.randomUUID(), "First query component"));
        secondComponent = componentRepository.saveAndFlush(new SoftwareComponent(
                "query-second-" + UUID.randomUUID(), "Second query component"));
        orders = List.of(
                persist(firstComponent, WorkOrderType.DEPLOYMENT, Priority.CRITICAL,
                        WorkOrderStatus.BLOCKED),
                persist(firstComponent, WorkOrderType.DEPLOYMENT, Priority.HIGH,
                        WorkOrderStatus.CREATED),
                persist(firstComponent, WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.CRITICAL,
                        WorkOrderStatus.BLOCKED),
                persist(secondComponent, WorkOrderType.PREVENTIVE_MAINTENANCE, Priority.MEDIUM,
                        WorkOrderStatus.PLANNED),
                persist(secondComponent, WorkOrderType.OPERATIONAL_SUPPORT, Priority.LOW,
                        WorkOrderStatus.IN_PROGRESS));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void noFilterQueryUsesPostgresqlPagingCountAndFixedOrder() {
        Page<WorkOrder> result = query(null, null, null, null, 0, 2);

        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(expected(orders).subList(0, 2), ids(result));
    }

    @Test
    void componentIdFilterExcludesOtherComponents() {
        Page<WorkOrder> result = query(firstComponent.getId(), null, null, null, 0, 20);

        assertEquals(expected(orders.stream().filter(order -> order.getComponent().getId()
                .equals(firstComponent.getId())).toList()), ids(result));
    }

    @Test
    void statusFilterExcludesOtherStatuses() {
        Page<WorkOrder> result = query(null, WorkOrderStatus.BLOCKED, null, null, 0, 20);

        assertEquals(expected(orders.stream().filter(order -> order.getStatus()
                == WorkOrderStatus.BLOCKED).toList()), ids(result));
    }

    @Test
    void typeFilterExcludesOtherTypes() {
        Page<WorkOrder> result = query(null, null, WorkOrderType.DEPLOYMENT, null, 0, 20);

        assertEquals(expected(orders.stream().filter(order -> order.getType()
                == WorkOrderType.DEPLOYMENT).toList()), ids(result));
    }

    @Test
    void priorityFilterExcludesOtherPriorities() {
        Page<WorkOrder> result = query(null, null, null, Priority.CRITICAL, 0, 20);

        assertEquals(expected(orders.stream().filter(order -> order.getPriority()
                == Priority.CRITICAL).toList()), ids(result));
    }

    @Test
    void representativeFilterCombinationsUseAndSemantics() {
        assertEquals(expected(List.of(orders.get(0), orders.get(2))),
                ids(query(firstComponent.getId(), WorkOrderStatus.BLOCKED, null, null, 0, 20)));
        assertEquals(expected(List.of(orders.get(0), orders.get(2))),
                ids(query(null, WorkOrderStatus.BLOCKED, null, Priority.CRITICAL, 0, 20)));
        assertEquals(expected(List.of(orders.get(0))),
                ids(query(null, null, WorkOrderType.DEPLOYMENT, Priority.CRITICAL, 0, 20)));
        assertEquals(expected(List.of(orders.get(0))),
                ids(query(firstComponent.getId(), WorkOrderStatus.BLOCKED,
                        WorkOrderType.DEPLOYMENT, Priority.CRITICAL, 0, 20)));
        Page<WorkOrder> noMatch = query(secondComponent.getId(), WorkOrderStatus.BLOCKED,
                WorkOrderType.DEPLOYMENT, Priority.CRITICAL, 0, 20);
        assertTrue(noMatch.isEmpty());
        assertEquals(0, noMatch.getTotalElements());
        assertEquals(0, noMatch.getTotalPages());
    }

    @Test
    void paginationReturnsNonOverlappingPagesLastPartialAndEmptyBeyondLastPage() {
        Page<WorkOrder> first = query(null, null, null, null, 0, 2);
        Page<WorkOrder> second = query(null, null, null, null, 1, 2);
        Page<WorkOrder> last = query(null, null, null, null, 2, 2);
        Page<WorkOrder> beyondLast = query(null, null, null, null, 20, 2);

        List<UUID> expected = expected(orders);
        assertEquals(expected.subList(0, 2), ids(first));
        assertEquals(expected.subList(2, 4), ids(second));
        assertEquals(expected.subList(4, 5), ids(last));
        assertTrue(beyondLast.isEmpty());
        assertEquals(5, beyondLast.getTotalElements());
        assertEquals(3, beyondLast.getTotalPages());
    }

    private WorkOrder persist(SoftwareComponent component, WorkOrderType type, Priority priority,
            WorkOrderStatus status) {
        WorkOrder order = new WorkOrder(component, "Query fixture " + UUID.randomUUID(), null,
                type, priority, type == WorkOrderType.DEPLOYMENT ? "1.0.0" : null);
        switch (status) {
            case CREATED -> { }
            case PLANNED -> order.plan();
            case IN_PROGRESS -> {
                order.plan();
                order.start();
            }
            case BLOCKED -> {
                order.plan();
                order.start();
                order.block("Fixture block");
            }
            case COMPLETED -> {
                order.plan();
                order.start();
                order.complete("Fixture completion");
            }
            case CANCELLED -> order.cancel("Fixture cancellation");
        }
        return workOrderRepository.saveAndFlush(order);
    }

    private Page<WorkOrder> query(UUID componentId, WorkOrderStatus status, WorkOrderType type,
            Priority priority, int page, int size) {
        return workOrderRepository.findAllFiltered(componentId, status, type, priority,
                PageRequest.of(page, size, FIXED_SORT));
    }

    private static List<UUID> expected(List<WorkOrder> source) {
        return source.stream().sorted(Comparator.comparing(WorkOrder::getCreatedAt).reversed()
                        .thenComparing(WorkOrder::getId))
                .map(WorkOrder::getId).toList();
    }

    private static List<UUID> ids(Page<WorkOrder> page) {
        return page.getContent().stream().map(WorkOrder::getId).toList();
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }
}
