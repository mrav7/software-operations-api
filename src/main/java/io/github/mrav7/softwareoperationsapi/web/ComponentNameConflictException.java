package io.github.mrav7.softwareoperationsapi.web;

final class ComponentNameConflictException extends RuntimeException {
    ComponentNameConflictException() {
        super("Component name already exists");
    }
}
