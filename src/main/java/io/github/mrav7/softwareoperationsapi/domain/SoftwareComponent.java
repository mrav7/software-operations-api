package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "software_component")
public class SoftwareComponent {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SoftwareComponent() {
    }

    /**
     * Creates an active software component with internally generated identity and timestamps.
     *
     * @param name the component name; must not be null
     * @param description the optional description; may be null
     */
    public SoftwareComponent(String name, String description) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.active = true;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
