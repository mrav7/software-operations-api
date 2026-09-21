package io.github.mrav7.softwareoperationsapi.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mrav7.softwareoperationsapi.domain.WorkLog;

public interface WorkLogRepository extends JpaRepository<WorkLog, UUID> {
    List<WorkLog> findByWorkOrder_IdOrderByCreatedAtAscIdAsc(UUID workOrderId);
}
