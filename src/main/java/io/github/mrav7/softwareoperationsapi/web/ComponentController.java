package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;

@RestController
@RequestMapping("/api/components")
class ComponentController {
    private final TemporaryState state;

    ComponentController(TemporaryState state) {
        this.state = state;
    }

    @PostMapping
    ResponseEntity<ComponentResponse> create(@RequestBody CreateComponentRequest request) {
        SoftwareComponent component = new SoftwareComponent(request.name(), request.description());
        state.addComponent(component);
        return ResponseEntity.created(URI.create("/api/components/" + component.getId()))
                .body(ComponentResponse.from(component));
    }

    @GetMapping("/{id}")
    ResponseEntity<ComponentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.of(state.findComponent(id).map(ComponentResponse::from));
    }

    public record CreateComponentRequest(String name, String description) {}

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
