package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SoftwareComponent {
    private final UUID id;
    private String name;
    private String description;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

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
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

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
