package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkOrderTest {

    @Nested
    class Construction {

        @Test
        void newWorkOrderStartsWithGeneratedIdentityAndCreatedStatus() {
            SoftwareComponent component = component("configuration-service");

            WorkOrder workOrder = newWorkOrder(component);

            assertAll(
                    () -> assertNotNull(workOrder.getId()),
                    () -> assertSame(component, workOrder.getComponent()),
                    () -> assertEquals("Investigate timeout", workOrder.getTitle()),
                    () -> assertEquals("Connection timeout under load", workOrder.getDescription()),
                    () -> assertEquals(WorkOrderType.CORRECTIVE_MAINTENANCE, workOrder.getType()),
                    () -> assertEquals(Priority.HIGH, workOrder.getPriority()),
                    () -> assertEquals(WorkOrderStatus.CREATED, workOrder.getStatus()),
                    () -> assertNull(workOrder.getTargetVersion()));
        }

        @Test
        void newWorkOrderStartsWithOnlyCreationTimestampsPopulated() {
            WorkOrder workOrder = newWorkOrder();

            assertAll(
                    () -> assertNotNull(workOrder.getCreatedAt()),
                    () -> assertEquals(workOrder.getCreatedAt(), workOrder.getUpdatedAt()),
                    () -> assertNull(workOrder.getPlannedAt()),
                    () -> assertNull(workOrder.getStartedAt()),
                    () -> assertNull(workOrder.getBlockedAt()),
                    () -> assertNull(workOrder.getCompletedAt()),
                    () -> assertNull(workOrder.getBlockingReason()),
                    () -> assertNull(workOrder.getResolutionSummary()),
                    () -> assertNull(workOrder.getCancellationReason()));
        }

        @Test
        void componentIsRequired() {
            assertThrows(NullPointerException.class, () -> new WorkOrder(
                    null,
                    "Investigate timeout",
                    null,
                    WorkOrderType.CORRECTIVE_MAINTENANCE,
                    Priority.HIGH,
                    null));
        }

        @Test
        void titleIsRequired() {
            assertThrows(NullPointerException.class, () -> new WorkOrder(
                    component("configuration-service"),
                    null,
                    null,
                    WorkOrderType.CORRECTIVE_MAINTENANCE,
                    Priority.HIGH,
                    null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void titleMustNotBeBlank(String title) {
            assertThrows(IllegalArgumentException.class, () -> new WorkOrder(
                    component("configuration-service"),
                    title,
                    null,
                    WorkOrderType.CORRECTIVE_MAINTENANCE,
                    Priority.HIGH,
                    null));
        }

        @Test
        void typeIsRequired() {
            assertThrows(NullPointerException.class, () -> new WorkOrder(
                    component("configuration-service"),
                    "Investigate timeout",
                    null,
                    null,
                    Priority.HIGH,
                    null));
        }

        @Test
        void priorityIsRequired() {
            assertThrows(NullPointerException.class, () -> new WorkOrder(
                    component("configuration-service"),
                    "Investigate timeout",
                    null,
                    WorkOrderType.CORRECTIVE_MAINTENANCE,
                    null,
                    null));
        }

        @Test
        void nullableDescriptionAndNonDeploymentTargetVersionAreAccepted() {
            WorkOrder workOrder = new WorkOrder(
                    component("configuration-service"),
                    "Investigate timeout",
                    null,
                    WorkOrderType.OPERATIONAL_SUPPORT,
                    Priority.MEDIUM,
                    null);

            assertNull(workOrder.getDescription());
            assertNull(workOrder.getTargetVersion());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void planningMovesCreatedWorkOrderToPlannedAndRecordsTimestamp() {
            WorkOrder workOrder = newWorkOrder();

            workOrder.plan();

            assertEquals(WorkOrderStatus.PLANNED, workOrder.getStatus());
            assertNotNull(workOrder.getPlannedAt());
            assertEquals(workOrder.getPlannedAt(), workOrder.getUpdatedAt());
        }

        @Test
        void startingMovesPlannedWorkOrderToInProgressAndRecordsTimestamp() {
            WorkOrder workOrder = newWorkOrder();
            workOrder.plan();

            workOrder.start();

            assertEquals(WorkOrderStatus.IN_PROGRESS, workOrder.getStatus());
            assertNotNull(workOrder.getStartedAt());
            assertEquals(workOrder.getStartedAt(), workOrder.getUpdatedAt());
        }

        @Test
        void blockingMovesInProgressWorkOrderToBlockedAndRecordsCurrentBlock() {
            WorkOrder workOrder = inProgressWorkOrder();

            workOrder.block("Waiting for infrastructure access");

            assertAll(
                    () -> assertEquals(WorkOrderStatus.BLOCKED, workOrder.getStatus()),
                    () -> assertEquals("Waiting for infrastructure access", workOrder.getBlockingReason()),
                    () -> assertNotNull(workOrder.getBlockedAt()),
                    () -> assertEquals(workOrder.getBlockedAt(), workOrder.getUpdatedAt()));
        }

        @Test
        void resumingMovesBlockedWorkOrderToInProgressAndClearsCurrentBlock() {
            WorkOrder workOrder = blockedWorkOrder();
            Instant originalStartedAt = workOrder.getStartedAt();

            workOrder.resume();

            assertAll(
                    () -> assertEquals(WorkOrderStatus.IN_PROGRESS, workOrder.getStatus()),
                    () -> assertNull(workOrder.getBlockingReason()),
                    () -> assertNull(workOrder.getBlockedAt()),
                    () -> assertEquals(originalStartedAt, workOrder.getStartedAt()));
        }

        @Test
        void completingMovesInProgressWorkOrderToCompletedAndRecordsResolution() {
            WorkOrder workOrder = inProgressWorkOrder();

            workOrder.complete("Corrected timeout configuration");

            assertAll(
                    () -> assertEquals(WorkOrderStatus.COMPLETED, workOrder.getStatus()),
                    () -> assertEquals("Corrected timeout configuration", workOrder.getResolutionSummary()),
                    () -> assertNotNull(workOrder.getCompletedAt()),
                    () -> assertEquals(workOrder.getCompletedAt(), workOrder.getUpdatedAt()));
        }

        @Test
        void createdWorkOrderCanBeCancelled() {
            WorkOrder workOrder = newWorkOrder();

            workOrder.cancel("No longer required");

            assertCancelledWithReason(workOrder, "No longer required");
        }

        @Test
        void plannedWorkOrderCanBeCancelled() {
            WorkOrder workOrder = plannedWorkOrder();

            workOrder.cancel("No longer required");

            assertCancelledWithReason(workOrder, "No longer required");
        }

        @Test
        void inProgressWorkOrderCanBeCancelled() {
            WorkOrder workOrder = inProgressWorkOrder();

            workOrder.cancel("No longer required");

            assertCancelledWithReason(workOrder, "No longer required");
        }

        @Test
        void blockedWorkOrderCanBeCancelledAndCurrentBlockIsCleared() {
            WorkOrder workOrder = blockedWorkOrder();

            workOrder.cancel("Superseded by another task");

            assertAll(
                    () -> assertCancelledWithReason(workOrder, "Superseded by another task"),
                    () -> assertNull(workOrder.getBlockingReason()),
                    () -> assertNull(workOrder.getBlockedAt()));
        }
    }

    @Nested
    class InvalidLifecycleTransitions {

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "PLANNED", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void planningRejectsStatusesOtherThanCreated(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(InvalidWorkOrderStateException.class, workOrder::plan);
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "CREATED", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void startingRejectsStatusesOtherThanPlanned(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(InvalidWorkOrderStateException.class, workOrder::start);
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "CREATED", "PLANNED", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void blockingRejectsStatusesOtherThanInProgress(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.block("Waiting for access"));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "CREATED", "PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"
        })
        void resumingRejectsStatusesOtherThanBlocked(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(InvalidWorkOrderStateException.class, workOrder::resume);
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "CREATED", "PLANNED", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void completingRejectsStatusesOtherThanInProgress(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.complete("Should not be accepted"));
        }

        @Test
        void completedWorkOrderCannotBeCancelled() {
            WorkOrder workOrder = completedWorkOrder();

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.cancel("Should not be accepted"));
        }

        @Test
        void cancelledWorkOrderCannotBeCancelledAgain() {
            WorkOrder workOrder = cancelledWorkOrder();

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.cancel("Should not be accepted"));
        }
    }

    @Nested
    class BlockingAndCompletionRequirements {

        @Test
        void blockingReasonIsRequired() {
            WorkOrder workOrder = inProgressWorkOrder();

            assertThrows(NullPointerException.class, () -> workOrder.block(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void blockingReasonMustNotBeBlank(String reason) {
            WorkOrder workOrder = inProgressWorkOrder();

            assertThrows(IllegalArgumentException.class, () -> workOrder.block(reason));
        }

        @Test
        void resolutionSummaryIsRequired() {
            WorkOrder workOrder = inProgressWorkOrder();

            assertThrows(NullPointerException.class, () -> workOrder.complete(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void resolutionSummaryMustNotBeBlank(String summary) {
            WorkOrder workOrder = inProgressWorkOrder();

            assertThrows(IllegalArgumentException.class, () -> workOrder.complete(summary));
        }

        @Test
        void cancellationReasonIsRequired() {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(NullPointerException.class, () -> workOrder.cancel(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void cancellationReasonMustNotBeBlank(String reason) {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(IllegalArgumentException.class, () -> workOrder.cancel(reason));
        }
    }

    @Nested
    class TerminalStates {

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED"})
        void terminalWorkOrdersRejectEveryLifecycleOperation(WorkOrderStatus terminalStatus) {
            WorkOrder workOrder = workOrderIn(terminalStatus);

            assertAll(
                    () -> assertThrows(InvalidWorkOrderStateException.class, workOrder::plan),
                    () -> assertThrows(InvalidWorkOrderStateException.class, workOrder::start),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.block("Waiting for access")),
                    () -> assertThrows(InvalidWorkOrderStateException.class, workOrder::resume),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.complete("Resolved")),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.cancel("Cancelled")));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED"})
        void terminalWorkOrdersRejectEveryControlledFieldChange(WorkOrderStatus terminalStatus) {
            WorkOrder workOrder = workOrderIn(terminalStatus);

            assertAll(
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changeComponent(component("replacement"))),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changeType(WorkOrderType.OPERATIONAL_SUPPORT)),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changeTitle("Replacement title")),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changeDescription("Replacement description")),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changePriority(Priority.CRITICAL)),
                    () -> assertThrows(
                            InvalidWorkOrderStateException.class,
                            () -> workOrder.changeTargetVersion("2.0.0")));
        }
    }

    @Nested
    class DeploymentInvariant {

        @Test
        void deploymentRequiresTargetVersion() {
            assertThrows(IllegalArgumentException.class, () -> deploymentWorkOrder(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void deploymentRejectsBlankTargetVersion(String targetVersion) {
            assertThrows(IllegalArgumentException.class, () -> deploymentWorkOrder(targetVersion));
        }

        @Test
        void deploymentAcceptsValidTargetVersion() {
            WorkOrder workOrder = deploymentWorkOrder("2.4.1");

            assertEquals("2.4.1", workOrder.getTargetVersion());
        }

        @Test
        void changingTypeToDeploymentFailsWithoutTargetVersion() {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> workOrder.changeType(WorkOrderType.DEPLOYMENT));
        }

        @Test
        void changingTypeToDeploymentSucceedsWithExistingTargetVersion() {
            WorkOrder workOrder = newWorkOrder();
            workOrder.changeTargetVersion("2.4.1");

            workOrder.changeType(WorkOrderType.DEPLOYMENT);

            assertEquals(WorkOrderType.DEPLOYMENT, workOrder.getType());
            assertEquals("2.4.1", workOrder.getTargetVersion());
        }

        @Test
        void deploymentTargetVersionCannotChangeToNull() {
            WorkOrder workOrder = deploymentWorkOrder("2.4.1");

            assertThrows(IllegalArgumentException.class, () -> workOrder.changeTargetVersion(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void deploymentTargetVersionCannotChangeToBlank(String targetVersion) {
            WorkOrder workOrder = deploymentWorkOrder("2.4.1");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> workOrder.changeTargetVersion(targetVersion));
        }

        @Test
        void deploymentTargetVersionCanChangeToValidValue() {
            WorkOrder workOrder = deploymentWorkOrder("2.4.1");

            workOrder.changeTargetVersion("2.4.2");

            assertEquals("2.4.2", workOrder.getTargetVersion());
        }

        @Test
        void typeAndTargetVersionCanChangeTogetherToValidDeploymentPair() {
            WorkOrder workOrder = newWorkOrder();

            workOrder.changeTypeAndTargetVersion(WorkOrderType.DEPLOYMENT, "2.4.0");

            assertEquals(WorkOrderType.DEPLOYMENT, workOrder.getType());
            assertEquals("2.4.0", workOrder.getTargetVersion());
        }

        @Test
        void typeAndTargetVersionCanChangeTogetherAwayFromDeploymentAndClearTarget() {
            WorkOrder workOrder = deploymentWorkOrder("2.4.0");

            workOrder.changeTypeAndTargetVersion(WorkOrderType.CORRECTIVE_MAINTENANCE, null);

            assertEquals(WorkOrderType.CORRECTIVE_MAINTENANCE, workOrder.getType());
            assertNull(workOrder.getTargetVersion());
        }

        @Test
        void combinedDeploymentChangeRejectsNullTargetWithoutPartialMutation() {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(IllegalArgumentException.class,
                    () -> workOrder.changeTypeAndTargetVersion(WorkOrderType.DEPLOYMENT, null));

            assertEquals(WorkOrderType.CORRECTIVE_MAINTENANCE, workOrder.getType());
            assertNull(workOrder.getTargetVersion());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void combinedDeploymentChangeRejectsBlankTarget(String targetVersion) {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(IllegalArgumentException.class,
                    () -> workOrder.changeTypeAndTargetVersion(
                            WorkOrderType.DEPLOYMENT, targetVersion));
        }

        @Test
        void combinedTypeAndTargetChangeIsCreatedOnly() {
            WorkOrder workOrder = plannedWorkOrder();

            assertThrows(InvalidWorkOrderStateException.class,
                    () -> workOrder.changeTypeAndTargetVersion(
                            WorkOrderType.DEPLOYMENT, "2.4.0"));
        }
    }

    @Nested
    class ProgressiveImmutability {

        @Test
        void componentCanChangeWhileCreated() {
            WorkOrder workOrder = newWorkOrder();
            SoftwareComponent replacement = component("replacement-service");

            workOrder.changeComponent(replacement);

            assertSame(replacement, workOrder.getComponent());
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "PLANNED", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void componentCannotChangeAfterCreated(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeComponent(component("replacement-service")));
        }

        @Test
        void typeCanChangeWhileCreated() {
            WorkOrder workOrder = newWorkOrder();

            workOrder.changeType(WorkOrderType.OPERATIONAL_SUPPORT);

            assertEquals(WorkOrderType.OPERATIONAL_SUPPORT, workOrder.getType());
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "PLANNED", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void typeCannotChangeAfterCreated(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeType(WorkOrderType.OPERATIONAL_SUPPORT));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"CREATED", "PLANNED"})
        void titleCanChangeBeforeWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            workOrder.changeTitle("Updated title");

            assertEquals("Updated title", workOrder.getTitle());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void titleChangeRejectsBlankValue(String title) {
            WorkOrder workOrder = newWorkOrder();

            assertThrows(IllegalArgumentException.class, () -> workOrder.changeTitle(title));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void titleCannotChangeAfterWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeTitle("Updated title"));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"CREATED", "PLANNED"})
        void descriptionCanChangeIncludingToNullBeforeWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            workOrder.changeDescription(null);

            assertNull(workOrder.getDescription());
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void descriptionCannotChangeAfterWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeDescription("Updated description"));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "CREATED", "PLANNED", "IN_PROGRESS", "BLOCKED"
        })
        void priorityCanChangeBeforeTerminalState(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            workOrder.changePriority(Priority.CRITICAL);

            assertEquals(Priority.CRITICAL, workOrder.getPriority());
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"COMPLETED", "CANCELLED"})
        void priorityCannotChangeInTerminalState(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changePriority(Priority.CRITICAL));
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {"CREATED", "PLANNED"})
        void targetVersionCanChangeBeforeWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            workOrder.changeTargetVersion("2.4.1");

            assertEquals("2.4.1", workOrder.getTargetVersion());
        }

        @ParameterizedTest
        @EnumSource(value = WorkOrderStatus.class, names = {
                "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED"
        })
        void targetVersionCannotChangeAfterWorkStarts(WorkOrderStatus status) {
            WorkOrder workOrder = workOrderIn(status);

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeTargetVersion("2.4.1"));
        }
    }

    @Nested
    class RejectedOperationsPreserveState {

        @Test
        void rejectedNullComponentChangePreservesComponentAndTimestamp() {
            WorkOrder workOrder = newWorkOrder();
            SoftwareComponent originalComponent = workOrder.getComponent();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(NullPointerException.class, () -> workOrder.changeComponent(null));

            assertAll(
                    () -> assertSame(originalComponent, workOrder.getComponent()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedNullTypeChangePreservesTypeAndTimestamp() {
            WorkOrder workOrder = newWorkOrder();
            WorkOrderType originalType = workOrder.getType();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(NullPointerException.class, () -> workOrder.changeType(null));

            assertAll(
                    () -> assertEquals(originalType, workOrder.getType()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedNullTitleChangePreservesTitleAndTimestamp() {
            WorkOrder workOrder = newWorkOrder();
            String originalTitle = workOrder.getTitle();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(NullPointerException.class, () -> workOrder.changeTitle(null));

            assertAll(
                    () -> assertEquals(originalTitle, workOrder.getTitle()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedNullPriorityChangePreservesPriorityAndTimestamp() {
            WorkOrder workOrder = newWorkOrder();
            Priority originalPriority = workOrder.getPriority();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(NullPointerException.class, () -> workOrder.changePriority(null));

            assertAll(
                    () -> assertEquals(originalPriority, workOrder.getPriority()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedBlankBlockDoesNotPartiallyModifyWorkOrder() {
            WorkOrder workOrder = inProgressWorkOrder();
            WorkOrderStatus originalStatus = workOrder.getStatus();
            String originalBlockingReason = workOrder.getBlockingReason();
            Instant originalBlockedAt = workOrder.getBlockedAt();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(IllegalArgumentException.class, () -> workOrder.block("   "));

            assertAll(
                    () -> assertEquals(originalStatus, workOrder.getStatus()),
                    () -> assertEquals(originalBlockingReason, workOrder.getBlockingReason()),
                    () -> assertEquals(originalBlockedAt, workOrder.getBlockedAt()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedDeploymentTypeChangeDoesNotPartiallyModifyWorkOrder() {
            WorkOrder workOrder = newWorkOrder();
            WorkOrderType originalType = workOrder.getType();
            String originalTargetVersion = workOrder.getTargetVersion();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> workOrder.changeType(WorkOrderType.DEPLOYMENT));

            assertAll(
                    () -> assertEquals(originalType, workOrder.getType()),
                    () -> assertEquals(originalTargetVersion, workOrder.getTargetVersion()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void rejectedTitleChangeDoesNotModifyTitleOrTimestamp() {
            WorkOrder workOrder = inProgressWorkOrder();
            String originalTitle = workOrder.getTitle();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeTitle("Updated title"));

            assertEquals(originalTitle, workOrder.getTitle());
            assertEquals(originalUpdatedAt, workOrder.getUpdatedAt());
        }
    }

    @Nested
    class ValidationPrecedence {

        @Test
        void completionChecksStateBeforeResolutionArgument() {
            WorkOrder workOrder = newWorkOrder();
            WorkOrderStatus originalStatus = workOrder.getStatus();
            String originalResolutionSummary = workOrder.getResolutionSummary();
            Instant originalCompletedAt = workOrder.getCompletedAt();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.complete(null));

            assertAll(
                    () -> assertEquals(originalStatus, workOrder.getStatus()),
                    () -> assertEquals(originalResolutionSummary, workOrder.getResolutionSummary()),
                    () -> assertEquals(originalCompletedAt, workOrder.getCompletedAt()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }

        @Test
        void titleChangeChecksEditWindowBeforeTitleArgument() {
            WorkOrder workOrder = inProgressWorkOrder();
            String originalTitle = workOrder.getTitle();
            Instant originalUpdatedAt = workOrder.getUpdatedAt();

            assertThrows(
                    InvalidWorkOrderStateException.class,
                    () -> workOrder.changeTitle(null));

            assertAll(
                    () -> assertEquals(originalTitle, workOrder.getTitle()),
                    () -> assertEquals(originalUpdatedAt, workOrder.getUpdatedAt()));
        }
    }

    private static SoftwareComponent component(String name) {
        return new SoftwareComponent(name, null);
    }

    private static WorkOrder newWorkOrder() {
        return newWorkOrder(component("configuration-service"));
    }

    private static WorkOrder newWorkOrder(SoftwareComponent component) {
        return new WorkOrder(
                component,
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null);
    }

    private static WorkOrder deploymentWorkOrder(String targetVersion) {
        return new WorkOrder(
                component("configuration-service"),
                "Deploy release",
                null,
                WorkOrderType.DEPLOYMENT,
                Priority.MEDIUM,
                targetVersion);
    }

    private static WorkOrder plannedWorkOrder() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.plan();
        return workOrder;
    }

    private static WorkOrder inProgressWorkOrder() {
        WorkOrder workOrder = plannedWorkOrder();
        workOrder.start();
        return workOrder;
    }

    private static WorkOrder blockedWorkOrder() {
        WorkOrder workOrder = inProgressWorkOrder();
        workOrder.block("Waiting for infrastructure access");
        return workOrder;
    }

    private static WorkOrder completedWorkOrder() {
        WorkOrder workOrder = inProgressWorkOrder();
        workOrder.complete("Corrected timeout configuration");
        return workOrder;
    }

    private static WorkOrder cancelledWorkOrder() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.cancel("No longer required");
        return workOrder;
    }

    private static WorkOrder workOrderIn(WorkOrderStatus status) {
        return switch (status) {
            case CREATED -> newWorkOrder();
            case PLANNED -> plannedWorkOrder();
            case IN_PROGRESS -> inProgressWorkOrder();
            case BLOCKED -> blockedWorkOrder();
            case COMPLETED -> completedWorkOrder();
            case CANCELLED -> cancelledWorkOrder();
        };
    }

    private static void assertCancelledWithReason(WorkOrder workOrder, String reason) {
        assertEquals(WorkOrderStatus.CANCELLED, workOrder.getStatus());
        assertEquals(reason, workOrder.getCancellationReason());
    }
}
