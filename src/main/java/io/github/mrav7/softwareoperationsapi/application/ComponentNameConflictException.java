package io.github.mrav7.softwareoperationsapi.application;

public final class ComponentNameConflictException extends RuntimeException {
    public ComponentNameConflictException() {
        super("Component name already exists");
    }
}
