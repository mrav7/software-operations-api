package io.github.mrav7.softwareoperationsapi.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class WorkOrderOperationalLoggingIntegrationTest {
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @BeforeEach
    void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @Test
    void successfulCreationAndTransitionProduceFocusedLogs(CapturedOutput output) {
        SoftwareComponent component = componentRepository.save(
                new SoftwareComponent("phase10-log-service", "Logging test component"));

        WorkOrder workOrder = workOrderService.create(component.getId(), "Logging test", null,
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null);
        workOrderService.plan(workOrder.getId());

        String logs = output.getOut();
        assertTrue(logs.contains("WorkOrder created: id=" + workOrder.getId()
                + " componentId=" + component.getId() + " type=CORRECTIVE_MAINTENANCE priority=HIGH"));
        assertTrue(logs.contains("WorkOrder transition: id=" + workOrder.getId()
                + " action=PLAN from=CREATED to=PLANNED"));
    }

    @Test
    void invalidTransitionDoesNotProduceSuccessTransitionLog(CapturedOutput output) {
        SoftwareComponent component = componentRepository.save(
                new SoftwareComponent("phase10-invalid-log-service", "Logging test component"));
        WorkOrder workOrder = workOrderService.create(component.getId(), "Logging test", null,
                WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.HIGH, null);

        try {
            workOrderService.start(workOrder.getId());
        } catch (RuntimeException expected) {
            // The existing lifecycle contract rejects START from CREATED.
        }

        assertFalse(output.getOut().contains("WorkOrder transition: id=" + workOrder.getId()
                + " action=START"));
    }
}
