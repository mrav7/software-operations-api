package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
}
