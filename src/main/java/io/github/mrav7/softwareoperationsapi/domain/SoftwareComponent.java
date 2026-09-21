package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    @Version
    @Column(nullable = false)
    private long version;

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
        this.name = requireName(name);
        this.description = description;
        this.active = true;
        Instant now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Changes the component name when the supplied value differs from the current name.
     *
     * @param name the new non-null, non-blank name
     */
    public void changeName(String name) {
        String requiredName = requireName(name);
        if (this.name.equals(requiredName)) {
            return;
        }

        this.name = requiredName;
        this.updatedAt = now();
    }

    /**
     * Changes or clears the component description when its value differs.
     *
     * @param description the new description; may be null
     */
    public void changeDescription(String description) {
        if (Objects.equals(this.description, description)) {
            return;
        }

        this.description = description;
        this.updatedAt = now();
    }

    /** Deactivates this component. Repeated deactivation is a no-op. */
    public void deactivate() {
        if (!active) {
            return;
        }

        this.active = false;
        this.updatedAt = now();
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
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
