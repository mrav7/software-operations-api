package io.github.mrav7.softwareoperationsapi.web;

class ResourceNotFoundException extends RuntimeException {
    ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found");
    }
}
