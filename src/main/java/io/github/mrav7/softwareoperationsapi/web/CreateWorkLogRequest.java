package io.github.mrav7.softwareoperationsapi.web;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;

public final class CreateWorkLogRequest {
    @NotBlank
    private String message;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonSetter("message")
    public void readMessage(String message) {
        this.message = message;
    }

    @JsonAnySetter
    public void readUnknown(String field, Object ignoredValue) {
        unknownFields.add(field);
    }

    String message() {
        return message;
    }

    Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }
}
