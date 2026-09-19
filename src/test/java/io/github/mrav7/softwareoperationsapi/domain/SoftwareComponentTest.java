package io.github.mrav7.softwareoperationsapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void nullableDescriptionIsAccepted() {
        SoftwareComponent component = new SoftwareComponent("configuration-service", null);

        assertNull(component.getDescription());
    }
}
