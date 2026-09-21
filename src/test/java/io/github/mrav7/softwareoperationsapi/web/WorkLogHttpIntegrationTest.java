package io.github.mrav7.softwareoperationsapi.web;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import io.github.mrav7.softwareoperationsapi.domain.WorkLog;
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
class WorkLogHttpIntegrationTest {
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
    void postCreatesNoteAndGetReturnsPersistedExactMessage() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse created = postLog(
                workOrder.getId(), "{\"message\":\"  Diagnostic note  \"}");

        assertEquals(201, created.getStatus());
        assertTrue(created.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        JsonNode createdBody = json.readTree(created.getContentAsString());
        String logId = createdBody.get("id").asString();
        UUID.fromString(logId);
        assertEquals("NOTE", createdBody.get("type").asString());
        assertEquals("  Diagnostic note  ", createdBody.get("message").asString());
        assertNotNull(createdBody.get("createdAt"));
        assertEquals(4, createdBody.size());

        MockHttpServletResponse listed = mvc.perform(
                get("/api/work-orders/{id}/logs", workOrder.getId()))
                .andReturn().getResponse();
        JsonNode listBody = json.readTree(listed.getContentAsString());
        assertEquals(200, listed.getStatus());
        assertEquals(1, listBody.size());
        assertEquals(logId, listBody.get(0).get("id").asString());
        assertEquals("  Diagnostic note  ", listBody.get(0).get("message").asString());
        assertEquals(1, workLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void postRejectsBlankMessage(String message) throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(
                workOrder.getId(), "{\"message\":\"" + message + "\"}");

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postRejectsMissingMessage() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(workOrder.getId(), "{}");

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postRejectsNullMessage() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(workOrder.getId(), "{\"message\":null}");

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postForMissingWorkOrderReturnsNotFound() throws Exception {
        MockHttpServletResponse response = postLog(
                UUID.randomUUID(), "{\"message\":\"Diagnostic note\"}");

        assertProblem(response, 404);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postRejectsClientControlledType() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(workOrder.getId(), """
                {"message":"Fake transition","type":"STATUS_CHANGE"}
                """);

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt", "workOrderId", "unknown"})
    void postRejectsOtherUnsupportedFields(String field) throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(workOrder.getId(),
                "{\"message\":\"Note\",\"" + field + "\":\"client-controlled\"}");

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postRejectsMalformedBody() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = postLog(workOrder.getId(), "{");

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void postRejectsMissingBody() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = mvc.perform(
                post("/api/work-orders/{id}/logs", workOrder.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();

        assertProblem(response, 400);
        assertEquals(0, workLogRepository.count());
    }

    @Test
    void getReturnsEmptyArrayForExistingWorkOrderWithoutLogs() throws Exception {
        WorkOrder workOrder = persistWorkOrder();

        MockHttpServletResponse response = mvc.perform(
                get("/api/work-orders/{id}/logs", workOrder.getId()))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertEquals(0, json.readTree(response.getContentAsString()).size());
    }

    @Test
    void getReturnsOrderedNotesAndFutureStatusChangesWithoutEntityGraph() throws Exception {
        WorkOrder workOrder = persistWorkOrder();
        workLogRepository.saveAndFlush(WorkLog.note(workOrder, "First note"));
        workLogRepository.saveAndFlush(WorkLog.statusChange(workOrder, "Future status change"));
        workLogRepository.saveAndFlush(WorkLog.note(workOrder, "Last note"));

        MockHttpServletResponse response = mvc.perform(
                get("/api/work-orders/{id}/logs", workOrder.getId()))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        JsonNode body = json.readTree(response.getContentAsString());
        assertEquals(3, body.size());
        assertTrue(containsMessage(body, "First note"));
        assertTrue(containsMessage(body, "Last note"));
        assertTrue(containsType(body, "STATUS_CHANGE"));
        for (JsonNode entry : body) {
            assertEquals(4, entry.size());
            assertFalse(entry.has("workOrder"));
            assertFalse(entry.has("component"));
            assertFalse(entry.has("logs"));
        }
        assertCanonicalOrder(body);
    }

    @Test
    void getForMissingWorkOrderReturnsNotFound() throws Exception {
        MockHttpServletResponse response = mvc.perform(
                get("/api/work-orders/{id}/logs", UUID.randomUUID()))
                .andReturn().getResponse();

        assertProblem(response, 404);
    }

    private MockHttpServletResponse postLog(UUID workOrderId, String body) throws Exception {
        return mvc.perform(post("/api/work-orders/{id}/logs", workOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn().getResponse();
    }

    private static void assertProblem(MockHttpServletResponse response, int status) {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    private static boolean containsMessage(JsonNode body, String message) {
        for (JsonNode entry : body) {
            if (message.equals(entry.get("message").asString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsType(JsonNode body, String type) {
        for (JsonNode entry : body) {
            if (type.equals(entry.get("type").asString())) {
                return true;
            }
        }
        return false;
    }

    private static void assertCanonicalOrder(JsonNode body) {
        for (int index = 0; index < body.size() - 1; index++) {
            JsonNode current = body.get(index);
            JsonNode next = body.get(index + 1);
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
                "work-log-http-" + UUID.randomUUID(), "WorkLog HTTP fixture"));
        return workOrderRepository.saveAndFlush(new WorkOrder(
                component,
                "Investigate timeout",
                "Connection timeout under load",
                WorkOrderType.CORRECTIVE_MAINTENANCE,
                Priority.HIGH,
                null));
    }
}
