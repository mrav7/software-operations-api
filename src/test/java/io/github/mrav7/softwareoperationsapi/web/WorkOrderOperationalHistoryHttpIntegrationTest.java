package io.github.mrav7.softwareoperationsapi.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.mrav7.softwareoperationsapi.domain.Priority;
import io.github.mrav7.softwareoperationsapi.domain.SoftwareComponent;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class WorkOrderOperationalHistoryHttpIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void lifecycleAndManualNoteProduceOneCombinedSnapshotConsistentTimeline() throws Exception {
        WorkOrder workOrder = persistWorkOrder();
        UUID id = workOrder.getId();
        assertEquals(0, getLogs(id).size());

        assertEquals(200, transition(id, "{\"action\":\"PLAN\"}").getStatus());
        assertTimeline(getLogs(id),
                new String[] {"STATUS_CHANGE"},
                new String[] {"Work order planned."});

        assertEquals(200, transition(id, "{\"action\":\"START\"}").getStatus());
        JsonNode started = getWorkOrder(id);
        String startedAt = started.get("startedAt").asString();
        assertFalse(startedAt.isBlank());

        MockHttpServletResponse note = mvc.perform(post("/api/work-orders/{id}/logs", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Investigating dependency health\"}"))
                .andReturn().getResponse();
        assertEquals(201, note.getStatus());
        assertEquals("NOTE", json.readTree(note.getContentAsString()).get("type").asString());

        assertEquals(200, transition(id, """
                {"action":"BLOCK","blockingReason":"Waiting for access"}
                """).getStatus());
        JsonNode blocked = getWorkOrder(id);
        assertEquals("BLOCKED", blocked.get("status").asString());
        assertEquals("Waiting for access", blocked.get("blockingReason").asString());
        assertNotNull(blocked.get("blockedAt"));
        assertFalse(blocked.get("blockedAt").isNull());
        assertTimeline(getLogs(id),
                new String[] {"STATUS_CHANGE", "STATUS_CHANGE", "NOTE", "STATUS_CHANGE"},
                new String[] {
                        "Work order planned.",
                        "Work order started.",
                        "Investigating dependency health",
                        "Work order blocked: Waiting for access"
                });

        assertEquals(200, transition(id, "{\"action\":\"RESUME\"}").getStatus());
        JsonNode resumed = getWorkOrder(id);
        assertEquals("IN_PROGRESS", resumed.get("status").asString());
        assertTrue(resumed.get("blockingReason").isNull());
        assertTrue(resumed.get("blockedAt").isNull());
        assertEquals(startedAt, resumed.get("startedAt").asString());
        assertTimeline(getLogs(id),
                new String[] {
                        "STATUS_CHANGE", "STATUS_CHANGE", "NOTE",
                        "STATUS_CHANGE", "STATUS_CHANGE"
                },
                new String[] {
                        "Work order planned.",
                        "Work order started.",
                        "Investigating dependency health",
                        "Work order blocked: Waiting for access",
                        "Work order resumed."
                });

        assertEquals(200, transition(id, """
                {"action":"COMPLETE","resolutionSummary":"Issue resolved"}
                """).getStatus());
        JsonNode completed = getWorkOrder(id);
        assertEquals("COMPLETED", completed.get("status").asString());
        assertEquals("Issue resolved", completed.get("resolutionSummary").asString());
        assertFalse(completed.get("completedAt").isNull());
        assertTimeline(getLogs(id),
                new String[] {
                        "STATUS_CHANGE", "STATUS_CHANGE", "NOTE",
                        "STATUS_CHANGE", "STATUS_CHANGE", "STATUS_CHANGE"
                },
                new String[] {
                        "Work order planned.",
                        "Work order started.",
                        "Investigating dependency health",
                        "Work order blocked: Waiting for access",
                        "Work order resumed.",
                        "Work order completed: Issue resolved"
                });
    }

    @Test
    void cancelTransitionExposesExactHistory() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = transition(workOrder.getId(), """
                {"action":"CANCEL","cancellationReason":"No longer needed"}
                """);

        assertEquals(200, response.getStatus());
        JsonNode cancelled = getWorkOrder(workOrder.getId());
        assertEquals("CANCELLED", cancelled.get("status").asString());
        assertEquals("No longer needed", cancelled.get("cancellationReason").asString());
        assertTimeline(getLogs(workOrder.getId()),
                new String[] {"STATUS_CHANGE"},
                new String[] {"Work order cancelled: No longer needed"});
    }

    private MockHttpServletResponse transition(UUID id, String body) throws Exception {
        return mvc.perform(post("/api/work-orders/{id}/transitions", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn().getResponse();
    }

    private JsonNode getWorkOrder(UUID id) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/work-orders/{id}", id))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return json.readTree(response.getContentAsString());
    }

    private JsonNode getLogs(UUID id) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/work-orders/{id}/logs", id))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return json.readTree(response.getContentAsString());
    }

    private static void assertTimeline(
            JsonNode logs, String[] expectedTypes, String[] expectedMessages) {
        assertEquals(expectedTypes.length, logs.size());
        assertEquals(expectedMessages.length, logs.size());
        for (int index = 0; index < logs.size(); index++) {
            assertEquals(expectedTypes[index], logs.get(index).get("type").asString());
            assertEquals(expectedMessages[index], logs.get(index).get("message").asString());
            assertEquals(4, logs.get(index).size());
        }
        assertCanonicalOrder(logs);
    }

    private static void assertCanonicalOrder(JsonNode logs) {
        for (int index = 0; index < logs.size() - 1; index++) {
            JsonNode current = logs.get(index);
            JsonNode next = logs.get(index + 1);
            int timestampComparison = Instant.parse(current.get("createdAt").asString())
                    .compareTo(Instant.parse(next.get("createdAt").asString()));

            assertFalse(timestampComparison > 0);
            if (timestampComparison == 0) {
                assertTrue(UUID.fromString(current.get("id").asString())
                        .compareTo(UUID.fromString(next.get("id").asString())) <= 0);
            }
        }
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    private WorkOrder persistWorkOrder() {
        SoftwareComponent component = componentRepository.saveAndFlush(new SoftwareComponent(
                "history-http-" + UUID.randomUUID(), "History HTTP fixture"));
        return workOrderRepository.saveAndFlush(new WorkOrder(
                component,
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null));
    }
}
