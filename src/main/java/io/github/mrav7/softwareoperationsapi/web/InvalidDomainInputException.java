package io.github.mrav7.softwareoperationsapi.web;

class InvalidDomainInputException extends RuntimeException {
    InvalidDomainInputException(String detail) {
        super(detail);
    }
}
