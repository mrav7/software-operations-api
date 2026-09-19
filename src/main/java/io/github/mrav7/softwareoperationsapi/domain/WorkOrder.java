package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class WorkOrder {
    private final UUID id;
    private SoftwareComponent component;
    private String title;
    private String description;
    private WorkOrderType type;
    private Priority priority;
    private WorkOrderStatus status;
    private String targetVersion;
    private String blockingReason;
    private Instant blockedAt;
    private String resolutionSummary;
    private String cancellationReason;
    private final Instant createdAt;
    private Instant plannedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    /**
     * Creates a work order in {@link WorkOrderStatus#CREATED} with internally generated
     * identity and timestamps. Lifecycle-specific state begins empty.
     *
     * @param component the associated component; must not be null
     * @param title the work order title; must not be null
     * @param description the optional description; may be null
     * @param type the work order type; must not be null
     * @param priority the work order priority; must not be null
     * @param targetVersion the target version; may be null unless {@code type} is
     *     {@link WorkOrderType#DEPLOYMENT}, which requires a non-null, non-blank value
     * @throws IllegalArgumentException if a deployment has no target version
     */
    public WorkOrder(
            SoftwareComponent component,
            String title,
            String description,
            WorkOrderType type,
            Priority priority,
            String targetVersion) {
        this.id = UUID.randomUUID();
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        WorkOrderType requiredType = Objects.requireNonNull(type, "type must not be null");
        validateDeploymentTarget(requiredType, targetVersion);
        this.type = requiredType;
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.status = WorkOrderStatus.CREATED;
        this.targetVersion = targetVersion;
        this.blockingReason = null;
        this.blockedAt = null;
        this.resolutionSummary = null;
        this.cancellationReason = null;
        this.plannedAt = null;
        this.startedAt = null;
        this.completedAt = null;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Moves this work order from {@code CREATED} to {@code PLANNED} and records its planning time.
     *
     * @throws InvalidWorkOrderStateException if the current status is not {@code CREATED}
     */
    public void plan() {
        requireStatus(WorkOrderStatus.CREATED, "plan");

        Instant now = Instant.now();
        this.status = WorkOrderStatus.PLANNED;
        this.plannedAt = now;
        this.updatedAt = now;
    }

    /**
     * Moves this work order from {@code PLANNED} to {@code IN_PROGRESS} and records its first start.
     *
     * @throws InvalidWorkOrderStateException if the current status is not {@code PLANNED}
     */
    public void start() {
        requireStatus(WorkOrderStatus.PLANNED, "start");

        Instant now = Instant.now();
        this.status = WorkOrderStatus.IN_PROGRESS;
        this.startedAt = now;
        this.updatedAt = now;
    }

    /**
     * Moves this work order from {@code IN_PROGRESS} to {@code BLOCKED} with its current block.
     *
     * @param blockingReason the required non-blank reason work cannot continue
     * @throws InvalidWorkOrderStateException if the current status is not {@code IN_PROGRESS}
     * @throws NullPointerException if {@code blockingReason} is null
     * @throws IllegalArgumentException if {@code blockingReason} is blank
     */
    public void block(String blockingReason) {
        requireStatus(WorkOrderStatus.IN_PROGRESS, "block");
        requireNonBlank(blockingReason, "blockingReason");

        Instant now = Instant.now();
        this.status = WorkOrderStatus.BLOCKED;
        this.blockingReason = blockingReason;
        this.blockedAt = now;
        this.updatedAt = now;
    }

    /**
     * Resumes a {@code BLOCKED} work order and clears its current blocking snapshot.
     *
     * @throws InvalidWorkOrderStateException if the current status is not {@code BLOCKED}
     */
    public void resume() {
        requireStatus(WorkOrderStatus.BLOCKED, "resume");

        Instant now = Instant.now();
        this.status = WorkOrderStatus.IN_PROGRESS;
        this.blockingReason = null;
        this.blockedAt = null;
        this.updatedAt = now;
    }

    /**
     * Completes an {@code IN_PROGRESS} work order with its final resolution.
     *
     * @param resolutionSummary the required non-blank result of the work
     * @throws InvalidWorkOrderStateException if the current status is not {@code IN_PROGRESS}
     * @throws NullPointerException if {@code resolutionSummary} is null
     * @throws IllegalArgumentException if {@code resolutionSummary} is blank
     */
    public void complete(String resolutionSummary) {
        requireStatus(WorkOrderStatus.IN_PROGRESS, "complete");
        requireNonBlank(resolutionSummary, "resolutionSummary");

        Instant now = Instant.now();
        this.resolutionSummary = resolutionSummary;
        this.completedAt = now;
        this.status = WorkOrderStatus.COMPLETED;
        this.updatedAt = now;
    }

    /**
     * Cancels a non-terminal work order with a reason, clearing any current blocking snapshot.
     *
     * @param cancellationReason the required non-blank reason for cancellation
     * @throws InvalidWorkOrderStateException if the current status is terminal
     * @throws NullPointerException if {@code cancellationReason} is null
     * @throws IllegalArgumentException if {@code cancellationReason} is blank
     */
    public void cancel(String cancellationReason) {
        if (status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CANCELLED) {
            throw invalidState("cancel");
        }
        requireNonBlank(cancellationReason, "cancellationReason");

        Instant now = Instant.now();
        this.cancellationReason = cancellationReason;
        if (status == WorkOrderStatus.BLOCKED) {
            this.blockingReason = null;
            this.blockedAt = null;
        }
        this.status = WorkOrderStatus.CANCELLED;
        this.updatedAt = now;
    }

    /**
     * Changes the associated component while this work order is {@code CREATED}.
     *
     * @param component the new component; must not be null
     * @throws InvalidWorkOrderStateException if the current status is not {@code CREATED}
     */
    public void changeComponent(SoftwareComponent component) {
        requireStatus(WorkOrderStatus.CREATED, "change component");
        Objects.requireNonNull(component, "component must not be null");

        Instant now = Instant.now();
        this.component = component;
        this.updatedAt = now;
    }

    /**
     * Changes the type while this work order is {@code CREATED}.
     *
     * @param type the new type; must not be null, and deployment requires an existing target version
     * @throws InvalidWorkOrderStateException if the current status is not {@code CREATED}
     * @throws IllegalArgumentException if changing to deployment without a valid target version
     */
    public void changeType(WorkOrderType type) {
        requireStatus(WorkOrderStatus.CREATED, "change type");
        WorkOrderType requiredType = Objects.requireNonNull(type, "type must not be null");
        validateDeploymentTarget(requiredType, targetVersion);

        Instant now = Instant.now();
        this.type = requiredType;
        this.updatedAt = now;
    }

    /**
     * Changes the title while this work order is {@code CREATED} or {@code PLANNED}.
     *
     * @param title the new title; must not be null
     * @throws InvalidWorkOrderStateException if the current status is not editable
     */
    public void changeTitle(String title) {
        requireCreatedOrPlanned("change title");
        Objects.requireNonNull(title, "title must not be null");

        Instant now = Instant.now();
        this.title = title;
        this.updatedAt = now;
    }

    /**
     * Changes the nullable description while this work order is {@code CREATED} or {@code PLANNED}.
     *
     * @param description the new description; may be null
     * @throws InvalidWorkOrderStateException if the current status is not editable
     */
    public void changeDescription(String description) {
        requireCreatedOrPlanned("change description");

        Instant now = Instant.now();
        this.description = description;
        this.updatedAt = now;
    }

    /**
     * Changes priority while this work order is non-terminal.
     *
     * @param priority the new priority; must not be null
     * @throws InvalidWorkOrderStateException if the current status is terminal
     */
    public void changePriority(Priority priority) {
        if (status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CANCELLED) {
            throw invalidState("change priority");
        }
        Objects.requireNonNull(priority, "priority must not be null");

        Instant now = Instant.now();
        this.priority = priority;
        this.updatedAt = now;
    }

    /**
     * Changes the target version while this work order is {@code CREATED} or {@code PLANNED}.
     *
     * @param targetVersion the new target version; deployment requires a non-null, non-blank value
     * @throws InvalidWorkOrderStateException if the current status is not editable
     * @throws IllegalArgumentException if a deployment would have no valid target version
     */
    public void changeTargetVersion(String targetVersion) {
        requireCreatedOrPlanned("change target version");
        validateDeploymentTarget(type, targetVersion);

        Instant now = Instant.now();
        this.targetVersion = targetVersion;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public SoftwareComponent getComponent() {
        return component;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public WorkOrderType getType() {
        return type;
    }

    public Priority getPriority() {
        return priority;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public String getBlockingReason() {
        return blockingReason;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPlannedAt() {
        return plannedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void requireStatus(WorkOrderStatus requiredStatus, String operation) {
        if (status != requiredStatus) {
            throw invalidState(operation);
        }
    }

    private void requireCreatedOrPlanned(String operation) {
        if (status != WorkOrderStatus.CREATED && status != WorkOrderStatus.PLANNED) {
            throw invalidState(operation);
        }
    }

    private InvalidWorkOrderStateException invalidState(String operation) {
        return new InvalidWorkOrderStateException(
                "Cannot " + operation + " work order while status is " + status);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void validateDeploymentTarget(WorkOrderType type, String targetVersion) {
        if (type == WorkOrderType.DEPLOYMENT
                && (targetVersion == null || targetVersion.isBlank())) {
            throw new IllegalArgumentException(
                    "targetVersion must not be null or blank for deployment");
        }
    }
}
