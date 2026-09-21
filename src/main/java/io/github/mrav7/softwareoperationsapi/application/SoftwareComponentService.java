package io.github.mrav7.softwareoperationsapi.application;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

@Service
public class SoftwareComponentService {
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String COMPONENT_NAME_CONSTRAINT = "uk_software_component_name";
    private static final Set<WorkOrderStatus> ACTIVE_WORK_STATUSES = Set.of(
            WorkOrderStatus.CREATED,
            WorkOrderStatus.PLANNED,
            WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.BLOCKED);

    private final SoftwareComponentRepository componentRepository;
    private final WorkOrderRepository workOrderRepository;

    public SoftwareComponentService(
            SoftwareComponentRepository componentRepository,
            WorkOrderRepository workOrderRepository) {
        this.componentRepository = componentRepository;
        this.workOrderRepository = workOrderRepository;
    }

    @Transactional
    public SoftwareComponent register(String name, String description) {
        SoftwareComponent component = constructComponent(name, description);
        if (componentRepository.existsByName(component.getName())) {
            throw new ComponentNameConflictException();
        }

        try {
            return componentRepository.saveAndFlush(component);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateName(exception);
        }
    }

    public SoftwareComponent get(UUID id) {
        return requireComponent(id);
    }

    public List<SoftwareComponent> list() {
        return componentRepository.findAll();
    }

    @Transactional
    public SoftwareComponent update(UUID id, UpdateSoftwareComponentCommand command) {
        SoftwareComponent component = requireComponent(id);

        if (command.namePresent()) {
            updateName(component, command.name());
        }
        if (command.descriptionPresent()) {
            component.changeDescription(command.description());
        }

        return component;
    }

    @Transactional
    public SoftwareComponent deactivate(UUID id) {
        SoftwareComponent component = requireComponent(id);
        if (!component.isActive()) {
            return component;
        }
        if (workOrderRepository.existsByComponent_IdAndStatusIn(id, ACTIVE_WORK_STATUSES)) {
            throw new ComponentHasActiveWorkException(id);
        }

        component.deactivate();
        return component;
    }

    private SoftwareComponent requireComponent(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component", id));
    }

    private void updateName(SoftwareComponent component, String name) {
        if (Objects.equals(component.getName(), name)) {
            return;
        }
        if (componentRepository.existsByNameAndIdNot(name, component.getId())) {
            throw new ComponentNameConflictException();
        }

        try {
            component.changeName(name);
            componentRepository.flush();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateName(exception);
        }
    }

    private static SoftwareComponent constructComponent(String name, String description) {
        try {
            return new SoftwareComponent(name, description);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidDomainInputException(exception.getMessage());
        }
    }

    private static RuntimeException translateDuplicateName(
            DataIntegrityViolationException exception) {
        if (isDuplicateNameViolation(exception)) {
            return new ComponentNameConflictException();
        }
        return exception;
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
}
