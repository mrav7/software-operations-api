package io.github.mrav7.softwareoperationsapi.application;

import java.util.UUID;

public final class InactiveComponentException extends RuntimeException {
    public InactiveComponentException(UUID componentId) {
        super("Component " + componentId + " is inactive");
    }
}
