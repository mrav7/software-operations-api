package io.github.mrav7.softwareoperationsapi.web;

import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;

@RestController
@RequestMapping("/api/components")
class ComponentController {
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String COMPONENT_NAME_CONSTRAINT = "uk_software_component_name";

    private final SoftwareComponentRepository componentRepository;

    ComponentController(SoftwareComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    @PostMapping
    ResponseEntity<ComponentResponse> create(@Valid @RequestBody CreateComponentRequest request) {
        if (componentRepository.existsByName(request.name())) {
            throw new ComponentNameConflictException();
        }

        SoftwareComponent component = new SoftwareComponent(request.name(), request.description());
        SoftwareComponent persisted;
        try {
            persisted = componentRepository.saveAndFlush(component);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateNameViolation(exception)) {
                throw new ComponentNameConflictException();
            }
            throw exception;
        }

        return ResponseEntity.created(URI.create("/api/components/" + persisted.getId()))
                .body(ComponentResponse.from(persisted));
    }

    @GetMapping("/{id}")
    ResponseEntity<ComponentResponse> get(@PathVariable UUID id) {
        SoftwareComponent component = componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component", id));
        return ResponseEntity.ok(ComponentResponse.from(component));
    }

    private static boolean isDuplicateNameViolation(DataIntegrityViolationException exception) {
        String sqlState = null;
        String constraintName = null;
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                sqlState = sqlException.getSQLState();
            }
            if (current instanceof ConstraintViolationException constraintViolation) {
                constraintName = constraintViolation.getConstraintName();
            }
            current = current.getCause();
        }
        return UNIQUE_VIOLATION_SQL_STATE.equals(sqlState)
                && COMPONENT_NAME_CONSTRAINT.equals(constraintName);
    }

    public record CreateComponentRequest(@NotBlank String name, String description) {}

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
