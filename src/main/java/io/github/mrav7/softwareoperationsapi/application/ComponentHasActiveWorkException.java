package io.github.mrav7.softwareoperationsapi.application;

import java.util.UUID;

public final class ComponentHasActiveWorkException extends RuntimeException {
    public ComponentHasActiveWorkException(UUID componentId) {
        super("Component " + componentId + " has active work orders");
    }
}
