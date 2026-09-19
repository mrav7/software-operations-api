package io.github.mrav7.softwareoperationsapi.domain;

public class InvalidWorkOrderStateException extends RuntimeException {
    public InvalidWorkOrderStateException(String message) {
        super(message);
    }
}
