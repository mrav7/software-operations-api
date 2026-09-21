package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;

public interface SoftwareComponentRepository extends JpaRepository<SoftwareComponent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select component from SoftwareComponent component where component.id = :id")
    Optional<SoftwareComponent> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
