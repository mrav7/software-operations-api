package io.github.mrav7.softwareoperationsapi.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mrav7.softwareoperationsapi.application.InvalidDomainInputException;
import io.github.mrav7.softwareoperationsapi.application.WorkLogService;

@RestController
@RequestMapping("/api/work-orders/{workOrderId}/logs")
class WorkLogController {
    private final WorkLogService workLogService;

    WorkLogController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @PostMapping
    ResponseEntity<WorkLogResponse> addNote(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody CreateWorkLogRequest request) {
        if (!request.unknownFields().isEmpty()) {
            throw new InvalidDomainInputException(
                    "Work log creation contains unsupported fields: "
                            + String.join(", ", request.unknownFields()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkLogResponse.from(
                        workLogService.addNote(workOrderId, request.message())));
    }

    @GetMapping
    ResponseEntity<List<WorkLogResponse>> list(@PathVariable UUID workOrderId) {
        List<WorkLogResponse> response = workLogService.list(workOrderId).stream()
                .map(WorkLogResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
