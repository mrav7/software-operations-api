package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByComponent_IdAndStatusIn(
            UUID componentId, Collection<WorkOrderStatus> statuses);
}
