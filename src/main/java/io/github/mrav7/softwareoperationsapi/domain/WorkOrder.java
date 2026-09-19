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
     * @param targetVersion the optional target version; may be null
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
        this.type = Objects.requireNonNull(type, "type must not be null");
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
}
