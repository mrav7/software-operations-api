package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;

public interface SoftwareComponentRepository extends JpaRepository<SoftwareComponent, UUID> {
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
