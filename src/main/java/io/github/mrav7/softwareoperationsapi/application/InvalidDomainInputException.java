package io.github.mrav7.softwareoperationsapi.application;

public final class InvalidDomainInputException extends RuntimeException {
    public InvalidDomainInputException(String detail) {
        super(detail);
    }
}
