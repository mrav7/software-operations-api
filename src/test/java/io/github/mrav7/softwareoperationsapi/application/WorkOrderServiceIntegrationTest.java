package io.github.mrav7.softwareoperationsapi.application;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.mrav7.softwareoperationsapi.domain.InvalidWorkOrderStateException;
import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class WorkOrderServiceIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @Test
    void createWithActiveComponentPersistsCreatedWorkOrderAndGetReturnsIt() {
        SoftwareComponent component = component("configuration-service");

        WorkOrder created = workOrderService.create(component.getId(), "Investigate timeout",
                "Connection timeout", WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH, null);

        WorkOrder persisted = workOrderRepository.findById(created.getId()).orElseThrow();
        assertEquals(WorkOrderStatus.CREATED, persisted.getStatus());
        assertEquals(component.getId(), persisted.getComponent().getId());
        assertEquals(created.getId(), workOrderService.get(created.getId()).getId());
    }

    @Test
    void createWithMissingComponentThrowsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> workOrderService.create(missing, "Investigate", null,
                        WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null));
    }

    @Test
    void createWithInactiveComponentThrowsConflict() {
        SoftwareComponent component = inactiveComponent("inactive-service");

        assertThrows(InactiveComponentException.class,
                () -> workOrderService.create(component.getId(), "Investigate", null,
                        WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null));
    }

    @Test
    void createInvalidDeploymentTargetThrowsInvalidInput() {
        SoftwareComponent component = component("deployment-service");

        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.create(component.getId(), "Deploy", null,
                        WorkOrderType.DEPLOYMENT, Priority.HIGH, null));
    }

    @Test
    void getMissingWorkOrderThrowsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> workOrderService.get(UUID.randomUUID()));
    }

    @Test
    void createdWorkOrderCanBeReassignedToDifferentActiveComponent() {
        WorkOrder workOrder = workOrder();
        SoftwareComponent target = component("target-service");

        workOrderService.modify(workOrder.getId(), componentChange(target.getId()));

        assertEquals(target.getId(), reloaded(workOrder).getComponent().getId());
    }

    @Test
    void reassignmentToMissingComponentThrowsNotFound() {
        WorkOrder workOrder = workOrder();

        assertThrows(ResourceNotFoundException.class,
                () -> workOrderService.modify(
                        workOrder.getId(), componentChange(UUID.randomUUID())));
    }

    @Test
    void reassignmentToInactiveComponentThrowsConflict() {
        WorkOrder workOrder = workOrder();
        SoftwareComponent target = inactiveComponent("inactive-target");

        assertThrows(InactiveComponentException.class,
                () -> workOrderService.modify(workOrder.getId(), componentChange(target.getId())));
    }

    @Test
    void plannedWorkOrderCannotBeReassigned() {
        WorkOrder workOrder = workOrder();
        SoftwareComponent target = component("target-service");
        workOrderService.plan(workOrder.getId());

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(workOrder.getId(), componentChange(target.getId())));
    }

    @Test
    void sameInactiveCurrentComponentIsTreatedAsNoReassignment() {
        WorkOrder workOrder = workOrder();
        UUID componentId = workOrder.getComponent().getId();
        jdbcTemplate.update("UPDATE software_component SET active = FALSE WHERE id = ?", componentId);

        workOrderService.modify(workOrder.getId(), componentChange(componentId));

        assertEquals(componentId, reloaded(workOrder).getComponent().getId());
    }

    @Test
    void typeCanChangeOnlyWhileCreatedThroughService() {
        WorkOrder created = workOrder();
        workOrderService.modify(created.getId(), typeChange(WorkOrderType.OPERATIONAL_SUPPORT));
        assertEquals(WorkOrderType.OPERATIONAL_SUPPORT, reloaded(created).getType());

        WorkOrder planned = workOrder("planned-type-service");
        workOrderService.plan(planned.getId());
        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(
                        planned.getId(), typeChange(WorkOrderType.OPERATIONAL_SUPPORT)));
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"CREATED", "PLANNED"})
    void titleAndDescriptionCanChangeBeforeWorkStarts(WorkOrderStatus status) {
        WorkOrder workOrder = workOrderIn(status);

        workOrderService.modify(workOrder.getId(), titleAndDescription("Updated title", null));

        WorkOrder persisted = reloaded(workOrder);
        assertEquals("Updated title", persisted.getTitle());
        assertNull(persisted.getDescription());
    }

    @Test
    void titleCannotChangeInProgress() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.IN_PROGRESS);

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(
                        workOrder.getId(), titleChange("Updated title")));
    }

    @Test
    void descriptionCannotChangeInProgress() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.IN_PROGRESS);

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(
                        workOrder.getId(), descriptionChange("Updated description")));
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {
            "CREATED", "PLANNED", "IN_PROGRESS", "BLOCKED"
    })
    void priorityCanChangeInEveryNonTerminalState(WorkOrderStatus status) {
        WorkOrder workOrder = workOrderIn(status);

        workOrderService.modify(workOrder.getId(), priorityChange(Priority.CRITICAL));

        assertEquals(Priority.CRITICAL, reloaded(workOrder).getPriority());
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void priorityCannotChangeInTerminalStates(WorkOrderStatus status) {
        WorkOrder workOrder = workOrderIn(status);

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(
                        workOrder.getId(), priorityChange(Priority.CRITICAL)));
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"CREATED", "PLANNED"})
    void targetVersionCanChangeAndClearBeforeWorkStarts(WorkOrderStatus status) {
        WorkOrder workOrder = workOrderIn(status, "existing-target");

        workOrderService.modify(workOrder.getId(), targetVersionChange(null));

        assertNull(reloaded(workOrder).getTargetVersion());
    }

    @Test
    void targetVersionCannotChangeInProgress() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.IN_PROGRESS);

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(
                        workOrder.getId(), targetVersionChange("2.4.0")));
    }

    @Test
    void omittedNullableFieldsRemainUnchanged() {
        WorkOrder workOrder = workOrder();

        workOrderService.modify(workOrder.getId(), titleChange("Only title changed"));

        WorkOrder persisted = reloaded(workOrder);
        assertEquals("Connection timeout", persisted.getDescription());
        assertNull(persisted.getTargetVersion());
    }

    @Test
    void deploymentTargetCannotBeClearedBySingleFieldUpdate() {
        WorkOrder workOrder = deploymentWorkOrder("2.4.0");

        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.modify(workOrder.getId(), targetVersionChange(null)));
    }

    @Test
    void combinedTypeAndTargetChangePersistsValidDeploymentPair() {
        WorkOrder workOrder = workOrder();

        workOrderService.modify(workOrder.getId(),
                typeAndTargetChange(WorkOrderType.DEPLOYMENT, "2.4.0"));

        WorkOrder persisted = reloaded(workOrder);
        assertEquals(WorkOrderType.DEPLOYMENT, persisted.getType());
        assertEquals("2.4.0", persisted.getTargetVersion());
    }

    @Test
    void combinedTypeAndTargetChangeCanLeaveDeploymentAndClearTarget() {
        WorkOrder workOrder = deploymentWorkOrder("2.4.0");

        workOrderService.modify(workOrder.getId(),
                typeAndTargetChange(WorkOrderType.CORRECTIVE_MAINTENANCE, null));

        WorkOrder persisted = reloaded(workOrder);
        assertEquals(WorkOrderType.CORRECTIVE_MAINTENANCE, persisted.getType());
        assertNull(persisted.getTargetVersion());
    }

    @ParameterizedTest
    @MethodSource("invalidDeploymentTargets")
    void combinedDeploymentChangeRejectsInvalidFinalPair(String targetVersion) {
        WorkOrder workOrder = workOrder();

        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.modify(workOrder.getId(),
                        typeAndTargetChange(WorkOrderType.DEPLOYMENT, targetVersion)));
    }

    @Test
    void combinedTypeAndTargetChangeRemainsCreatedOnly() {
        WorkOrder workOrder = workOrderIn(WorkOrderStatus.PLANNED);

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.modify(workOrder.getId(),
                        typeAndTargetChange(WorkOrderType.DEPLOYMENT, "2.4.0")));
    }

    @Test
    void completeLifecyclePersistsEveryTransitionAndContext() {
        WorkOrder workOrder = workOrder();

        workOrderService.plan(workOrder.getId());
        assertEquals(WorkOrderStatus.PLANNED, reloaded(workOrder).getStatus());
        workOrderService.start(workOrder.getId());
        assertEquals(WorkOrderStatus.IN_PROGRESS, reloaded(workOrder).getStatus());
        workOrderService.block(workOrder.getId(), "Waiting for access");
        assertEquals("Waiting for access", reloaded(workOrder).getBlockingReason());
        workOrderService.resume(workOrder.getId());
        assertEquals(WorkOrderStatus.IN_PROGRESS, reloaded(workOrder).getStatus());
        workOrderService.complete(workOrder.getId(), "Issue resolved");

        WorkOrder completed = reloaded(workOrder);
        assertEquals(WorkOrderStatus.COMPLETED, completed.getStatus());
        assertEquals("Issue resolved", completed.getResolutionSummary());
    }

    @Test
    void cancellationPersistsThroughServiceTransaction() {
        WorkOrder workOrder = workOrder();

        workOrderService.cancel(workOrder.getId(), "No longer needed");

        WorkOrder cancelled = reloaded(workOrder);
        assertEquals(WorkOrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("No longer needed", cancelled.getCancellationReason());
    }

    @Test
    void invalidTransitionContextIsTranslatedButStateConflictIsPreserved() {
        WorkOrder workOrder = workOrder();
        workOrderService.plan(workOrder.getId());

        assertThrows(InvalidWorkOrderStateException.class,
                () -> workOrderService.complete(workOrder.getId(), null));
        workOrderService.start(workOrder.getId());
        assertThrows(InvalidDomainInputException.class,
                () -> workOrderService.complete(workOrder.getId(), null));
    }

    private static Stream<String> invalidDeploymentTargets() {
        return Stream.of(null, "   ");
    }

    private SoftwareComponent component(String name) {
        return componentRepository.saveAndFlush(new SoftwareComponent(name, null));
    }

    private SoftwareComponent inactiveComponent(String name) {
        SoftwareComponent component = component(name);
        jdbcTemplate.update("UPDATE software_component SET active = FALSE WHERE id = ?",
                component.getId());
        return componentRepository.findById(component.getId()).orElseThrow();
    }

    private WorkOrder workOrder() {
        return workOrder("configuration-service");
    }

    private WorkOrder workOrder(String componentName) {
        SoftwareComponent component = component(componentName);
        return workOrderService.create(component.getId(), "Investigate timeout",
                "Connection timeout", WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH, null);
    }

    private WorkOrder deploymentWorkOrder(String targetVersion) {
        SoftwareComponent component = component("deployment-service");
        return workOrderService.create(component.getId(), "Deploy release", null,
                WorkOrderType.DEPLOYMENT, Priority.HIGH, targetVersion);
    }

    private WorkOrder workOrderIn(WorkOrderStatus status) {
        return workOrderIn(status, null);
    }

    private WorkOrder workOrderIn(WorkOrderStatus status, String targetVersion) {
        SoftwareComponent component = component("component-" + status + "-" + UUID.randomUUID());
        WorkOrder workOrder = workOrderService.create(component.getId(), "Investigate timeout",
                "Connection timeout", WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH, targetVersion);
        switch (status) {
            case CREATED -> { }
            case PLANNED -> workOrderService.plan(workOrder.getId());
            case IN_PROGRESS -> {
                workOrderService.plan(workOrder.getId());
                workOrderService.start(workOrder.getId());
            }
            case BLOCKED -> {
                workOrderService.plan(workOrder.getId());
                workOrderService.start(workOrder.getId());
                workOrderService.block(workOrder.getId(), "Waiting for access");
            }
            case COMPLETED -> {
                workOrderService.plan(workOrder.getId());
                workOrderService.start(workOrder.getId());
                workOrderService.complete(workOrder.getId(), "Issue resolved");
            }
            case CANCELLED -> workOrderService.cancel(workOrder.getId(), "No longer needed");
        }
        return workOrder;
    }

    private WorkOrder reloaded(WorkOrder workOrder) {
        return workOrderRepository.findById(workOrder.getId()).orElseThrow();
    }

    private static UpdateWorkOrderCommand componentChange(UUID componentId) {
        return command(true, componentId, false, null, false, null,
                false, null, false, null, false, null);
    }

    private static UpdateWorkOrderCommand typeChange(WorkOrderType type) {
        return command(false, null, true, type, false, null,
                false, null, false, null, false, null);
    }

    private static UpdateWorkOrderCommand titleChange(String title) {
        return command(false, null, false, null, true, title,
                false, null, false, null, false, null);
    }

    private static UpdateWorkOrderCommand titleAndDescription(String title, String description) {
        return command(false, null, false, null, true, title,
                true, description, false, null, false, null);
    }

    private static UpdateWorkOrderCommand descriptionChange(String description) {
        return command(false, null, false, null, false, null,
                true, description, false, null, false, null);
    }

    private static UpdateWorkOrderCommand priorityChange(Priority priority) {
        return command(false, null, false, null, false, null,
                false, null, true, priority, false, null);
    }

    private static UpdateWorkOrderCommand targetVersionChange(String targetVersion) {
        return command(false, null, false, null, false, null,
                false, null, false, null, true, targetVersion);
    }

    private static UpdateWorkOrderCommand typeAndTargetChange(
            WorkOrderType type, String targetVersion) {
        return command(false, null, true, type, false, null,
                false, null, false, null, true, targetVersion);
    }

    private static UpdateWorkOrderCommand command(
            boolean componentIdPresent, UUID componentId,
            boolean typePresent, WorkOrderType type,
            boolean titlePresent, String title,
            boolean descriptionPresent, String description,
            boolean priorityPresent, Priority priority,
            boolean targetVersionPresent, String targetVersion) {
        return new UpdateWorkOrderCommand(
                componentIdPresent, componentId, typePresent, type,
                titlePresent, title, descriptionPresent, description,
                priorityPresent, priority, targetVersionPresent, targetVersion);
    }
}
