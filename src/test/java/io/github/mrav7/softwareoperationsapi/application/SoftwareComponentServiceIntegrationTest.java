package io.github.mrav7.softwareoperationsapi.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SoftwareComponentServiceIntegrationTest {
    @Autowired
    private SoftwareComponentService componentService;

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
    void registerPersistsAndGetReturnsComponent() {
        SoftwareComponent registered = componentService.register("configuration-service", "Config");

        SoftwareComponent found = componentService.get(registered.getId());

        assertEquals(registered.getId(), found.getId());
        assertEquals("configuration-service", found.getName());
        assertEquals("Config", found.getDescription());
        assertTrue(found.isActive());
    }

    @Test
    void getMissingComponentThrowsResourceNotFound() {
        UUID missingId = UUID.randomUUID();

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> componentService.get(missingId));

        assertEquals("Component " + missingId + " was not found", exception.getMessage());
    }

    @Test
    void listReturnsAllPersistedComponentsWithoutDependingOnOrder() {
        SoftwareComponent first = componentService.register("alpha-service", null);
        SoftwareComponent second = componentService.register("beta-service", null);

        List<UUID> ids = componentService.list().stream().map(SoftwareComponent::getId).toList();

        assertEquals(2, ids.size());
        assertTrue(ids.contains(first.getId()));
        assertTrue(ids.contains(second.getId()));
    }

    @Test
    void updateNamePersistsThroughDirtyChecking() {
        SoftwareComponent component = componentService.register("old-name", null);

        componentService.update(component.getId(),
                new UpdateSoftwareComponentCommand(true, "new-name", false, null));

        assertEquals("new-name", componentRepository.findById(component.getId()).orElseThrow().getName());
    }

    @Test
    void updateDescriptionPersistsAndExplicitNullClearsIt() {
        SoftwareComponent component = componentService.register("configuration-service", "Initial");

        componentService.update(component.getId(),
                new UpdateSoftwareComponentCommand(false, null, true, "Changed"));
        assertEquals("Changed", componentRepository.findById(component.getId())
                .orElseThrow().getDescription());

        componentService.update(component.getId(),
                new UpdateSoftwareComponentCommand(false, null, true, null));
        assertNull(componentRepository.findById(component.getId()).orElseThrow().getDescription());
    }

    @Test
    void sameValueUpdateDoesNotChangeUpdatedTimestamp() {
        SoftwareComponent component = componentService.register("configuration-service", "Same");
        Instant originalUpdatedAt = component.getUpdatedAt();

        componentService.update(component.getId(),
                new UpdateSoftwareComponentCommand(true, "configuration-service", true, "Same"));

        assertEquals(originalUpdatedAt, componentRepository.findById(component.getId())
                .orElseThrow().getUpdatedAt());
    }

    @Test
    void duplicateRenameProducesComponentNameConflict() {
        componentService.register("existing-name", null);
        SoftwareComponent component = componentService.register("other-name", null);

        assertThrows(ComponentNameConflictException.class,
                () -> componentService.update(component.getId(),
                        new UpdateSoftwareComponentCommand(true, "existing-name", false, null)));

        assertEquals("other-name", componentRepository.findById(component.getId())
                .orElseThrow().getName());
    }

    @Test
    void deactivateWithoutWorkOrdersPersistsInactiveState() {
        SoftwareComponent component = componentService.register("configuration-service", null);

        componentService.deactivate(component.getId());

        assertFalse(componentRepository.findById(component.getId()).orElseThrow().isActive());
    }

    @Test
    void repeatedDeactivationIsIdempotent() {
        SoftwareComponent component = componentService.register("configuration-service", null);
        SoftwareComponent deactivated = componentService.deactivate(component.getId());
        Instant firstDeactivation = deactivated.getUpdatedAt();

        componentService.deactivate(component.getId());

        SoftwareComponent reloaded = componentRepository.findById(component.getId()).orElseThrow();
        assertFalse(reloaded.isActive());
        assertEquals(firstDeactivation, reloaded.getUpdatedAt());
    }

    @ParameterizedTest
    @MethodSource("activeStatuses")
    void activeWorkOrderPreventsDeactivation(WorkOrderStatus status) {
        SoftwareComponent component = componentService.register("component-" + status, null);
        workOrderRepository.saveAndFlush(workOrderInStatus(component, status));

        assertThrows(ComponentHasActiveWorkException.class,
                () -> componentService.deactivate(component.getId()));

        assertTrue(componentRepository.findById(component.getId()).orElseThrow().isActive());
    }

    @ParameterizedTest
    @MethodSource("terminalStatuses")
    void terminalWorkOrderDoesNotPreventDeactivation(WorkOrderStatus status) {
        SoftwareComponent component = componentService.register("component-" + status, null);
        workOrderRepository.saveAndFlush(workOrderInStatus(component, status));

        componentService.deactivate(component.getId());

        assertFalse(componentRepository.findById(component.getId()).orElseThrow().isActive());
    }

    private static Stream<WorkOrderStatus> activeStatuses() {
        return Stream.of(WorkOrderStatus.CREATED, WorkOrderStatus.PLANNED,
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.BLOCKED);
    }

    private static Stream<WorkOrderStatus> terminalStatuses() {
        return Stream.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED);
    }

    private static WorkOrder workOrderInStatus(
            SoftwareComponent component, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder(component, "Investigate issue", null,
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.MEDIUM, null);
        switch (status) {
            case CREATED -> { }
            case PLANNED -> workOrder.plan();
            case IN_PROGRESS -> {
                workOrder.plan();
                workOrder.start();
            }
            case BLOCKED -> {
                workOrder.plan();
                workOrder.start();
                workOrder.block("Waiting for access");
            }
            case COMPLETED -> {
                workOrder.plan();
                workOrder.start();
                workOrder.complete("Issue resolved");
            }
            case CANCELLED -> workOrder.cancel("No longer needed");
        }
        return workOrder;
    }
}
