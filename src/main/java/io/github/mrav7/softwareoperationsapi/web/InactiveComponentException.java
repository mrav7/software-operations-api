package io.github.mrav7.softwareoperationsapi.web;

import java.util.UUID;

final class InactiveComponentException extends RuntimeException {
    InactiveComponentException(UUID componentId) {
        super("Component " + componentId + " is inactive");
    }
}
