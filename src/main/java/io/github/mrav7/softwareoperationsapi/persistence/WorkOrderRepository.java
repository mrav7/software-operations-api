package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByComponent_IdAndStatusIn(
            UUID componentId, Collection<WorkOrderStatus> statuses);

    @Query("""
            select w
            from WorkOrder w
            where (:componentId is null or w.component.id = :componentId)
              and (:status is null or w.status = :status)
              and (:type is null or w.type = :type)
              and (:priority is null or w.priority = :priority)
            """)
    Page<WorkOrder> findAllFiltered(
            @Param("componentId") UUID componentId,
            @Param("status") WorkOrderStatus status,
            @Param("type") WorkOrderType type,
            @Param("priority") Priority priority,
            Pageable pageable);
}
