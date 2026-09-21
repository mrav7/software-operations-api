package io.github.mrav7.softwareoperationsapi.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftwareComponentTest {

    @Test
    void newComponentHasGeneratedIdentityAndInitialTimestamps() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", "Configuration API");

        assertNotNull(component.getId());
        assertNotNull(component.getCreatedAt());
        assertNotNull(component.getUpdatedAt());
        assertEquals(component.getCreatedAt(), component.getUpdatedAt());
    }

    @Test
    void newComponentExposesConstructorValuesAndStartsActive() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", "Configuration API");

        assertEquals("configuration-service", component.getName());
        assertEquals("Configuration API", component.getDescription());
        assertTrue(component.isActive());
    }

    @Test
    void nameIsRequired() {
        assertThrows(NullPointerException.class, () -> new SoftwareComponent(null, "Description"));
    }

    @Test
    void nameMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new SoftwareComponent("   ", "Description"));
    }

    @Test
    void nullableDescriptionIsAccepted() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);

        assertNull(component.getDescription());
    }

    @Test
    void changeNameChangesNameAndUpdatedTimestamp() {
        SoftwareComponent component = new SoftwareComponent("old-name", null);
        Instant previousUpdate = component.getUpdatedAt();
        awaitClockAfter(previousUpdate);

        component.changeName("new-name");

        assertEquals("new-name", component.getName());
        assertTrue(component.getUpdatedAt().isAfter(previousUpdate));
    }

    @Test
    void changeNameToCurrentValueIsNoOp() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);
        Instant previousUpdate = component.getUpdatedAt();

        component.changeName("configuration-service");

        assertEquals(previousUpdate, component.getUpdatedAt());
    }

    @Test
    void changeNameRejectsNullAndBlank() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);

        assertThrows(NullPointerException.class, () -> component.changeName(null));
        assertThrows(IllegalArgumentException.class, () -> component.changeName("  "));
        assertEquals("configuration-service", component.getName());
    }

    @Test
    void changeDescriptionChangesDescriptionAndUpdatedTimestamp() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", "Old");
        Instant previousUpdate = component.getUpdatedAt();
        awaitClockAfter(previousUpdate);

        component.changeDescription("New");

        assertEquals("New", component.getDescription());
        assertTrue(component.getUpdatedAt().isAfter(previousUpdate));
    }

    @Test
    void changeDescriptionCanClearDescription() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", "Old");

        component.changeDescription(null);

        assertNull(component.getDescription());
    }

    @Test
    void changeDescriptionToCurrentValueIsNoOp() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", "Same");
        Instant previousUpdate = component.getUpdatedAt();

        component.changeDescription("Same");

        assertEquals(previousUpdate, component.getUpdatedAt());
    }

    @Test
    void deactivateMarksActiveComponentInactiveAndUpdatesTimestamp() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);
        Instant previousUpdate = component.getUpdatedAt();
        awaitClockAfter(previousUpdate);

        component.deactivate();

        assertFalse(component.isActive());
        assertTrue(component.getUpdatedAt().isAfter(previousUpdate));
    }

    @Test
    void deactivateAlreadyInactiveComponentIsNoOp() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);
        component.deactivate();
        Instant deactivatedAt = component.getUpdatedAt();
        awaitClockAfter(deactivatedAt);

        component.deactivate();

        assertFalse(component.isActive());
        assertEquals(deactivatedAt, component.getUpdatedAt());
    }

    private static void awaitClockAfter(Instant timestamp) {
        while (!Instant.now().truncatedTo(ChronoUnit.MICROS).isAfter(timestamp)) {
            Thread.onSpinWait();
        }
    }
}
