package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.application.InvalidDomainInputException;
import io.github.mrav7.softwareoperationsapi.application.SoftwareComponentService;
import io.github.mrav7.softwareoperationsapi.application.UpdateSoftwareComponentCommand;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;

@RestController
@RequestMapping("/api/components")
class ComponentController {
    private final SoftwareComponentService componentService;

    ComponentController(SoftwareComponentService componentService) {
        this.componentService = componentService;
    }

    @PostMapping
    ResponseEntity<ComponentResponse> create(
            @Valid @RequestBody CreateComponentRequest request) {
        SoftwareComponent component = componentService.register(
                request.name(), request.description());
        return ResponseEntity.created(URI.create("/api/components/" + component.getId()))
                .body(ComponentResponse.from(component));
    }

    @GetMapping("/{id}")
    ResponseEntity<ComponentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ComponentResponse.from(componentService.get(id)));
    }

    @GetMapping
    ResponseEntity<List<ComponentResponse>> list() {
        List<ComponentResponse> components = componentService.list().stream()
                .map(ComponentResponse::from)
                .toList();
        return ResponseEntity.ok(components);
    }

    @PatchMapping("/{id}")
    ResponseEntity<ComponentResponse> update(
            @PathVariable UUID id, @RequestBody UpdateComponentRequest request) {
        if (!request.unknownFields().isEmpty()) {
            throw new InvalidDomainInputException(
                    "Component update contains unsupported fields: "
                            + String.join(", ", request.unknownFields()));
        }

        UpdateSoftwareComponentCommand command = new UpdateSoftwareComponentCommand(
                request.namePresent(), request.name(),
                request.descriptionPresent(), request.description());
        return ResponseEntity.ok(ComponentResponse.from(componentService.update(id, command)));
    }

    @PostMapping("/{id}/deactivation")
    ResponseEntity<ComponentResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ComponentResponse.from(componentService.deactivate(id)));
    }

    public record CreateComponentRequest(@NotBlank String name, String description) {}

    public static final class UpdateComponentRequest {
        private String name;
        private boolean namePresent;
        private String description;
        private boolean descriptionPresent;
        private final Set<String> unknownFields = new LinkedHashSet<>();

        @JsonSetter("name")
        public void readName(String name) {
            this.name = name;
            this.namePresent = true;
        }

        @JsonSetter("description")
        public void readDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }

        @JsonAnySetter
        public void readUnknown(String field, Object ignoredValue) {
            unknownFields.add(field);
        }

        String name() {
            return name;
        }

        boolean namePresent() {
            return namePresent;
        }

        String description() {
            return description;
        }

        boolean descriptionPresent() {
            return descriptionPresent;
        }

        Set<String> unknownFields() {
            return Set.copyOf(unknownFields);
        }
    }

    public record ComponentResponse(
            UUID id, String name, String description, boolean active,
            Instant createdAt, Instant updatedAt) {
        static ComponentResponse from(SoftwareComponent component) {
            return new ComponentResponse(component.getId(), component.getName(),
                    component.getDescription(), component.isActive(),
                    component.getCreatedAt(), component.getUpdatedAt());
        }
    }
}
