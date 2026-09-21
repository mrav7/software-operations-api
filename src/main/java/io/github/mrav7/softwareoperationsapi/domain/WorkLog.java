package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_log")
public class WorkLog {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkLogType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkLog() {
    }

    private WorkLog(WorkOrder workOrder, WorkLogType type, String message) {
        this.id = UUID.randomUUID();
        this.workOrder = Objects.requireNonNull(workOrder, "workOrder must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.message = requireMessage(message);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        workOrder.addLog(this);
    }

    public static WorkLog note(WorkOrder workOrder, String message) {
        return new WorkLog(workOrder, WorkLogType.NOTE, message);
    }

    public static WorkLog statusChange(WorkOrder workOrder, String message) {
        return new WorkLog(workOrder, WorkLogType.STATUS_CHANGE, message);
    }

    private static String requireMessage(String message) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }

    public UUID getId() {
        return id;
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public WorkLogType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
