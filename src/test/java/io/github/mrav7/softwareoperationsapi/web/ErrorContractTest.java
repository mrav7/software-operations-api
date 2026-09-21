package io.github.mrav7.softwareoperationsapi.web;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @Test
    void validationFailuresUseProblemDetailWithSortedFieldErrors() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"priority\":null}"))
                .andReturn().getResponse();

        JsonNode problem = assertProblem(response, 400, "Bad Request",
                "Request validation failed", "/api/work-orders");
        assertEquals("componentId", problem.get("errors").get(0).get("field").asString());
        assertEquals("priority", problem.get("errors").get(1).get("field").asString());
        assertEquals("title", problem.get("errors").get(2).get("field").asString());
        assertEquals("type", problem.get("errors").get(3).get("field").asString());

        UUID transitionId = UUID.randomUUID();
        JsonNode transitionProblem = assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", transitionId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request validation failed", "/api/work-orders/" + transitionId + "/transitions");
        assertEquals("action", transitionProblem.get("errors").get(0).get("field").asString());
    }

    @Test
    void unreadableBodiesAndInvalidPathValuesUseBadRequestProblemDetail() throws Exception {
        assertProblem(mvc.perform(post("/api/components").contentType(MediaType.APPLICATION_JSON)
                .content("{not-json}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request body is malformed or contains an invalid value", "/api/components");

        String orderId = createWorkOrder();
        assertProblem(mvc.perform(patch("/api/work-orders/{id}", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"componentId\":\"not-a-uuid\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request body is malformed or contains an invalid value",
                "/api/work-orders/" + orderId);
        assertProblem(mvc.perform(patch("/api/work-orders/{id}", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"UNKNOWN\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request body is malformed or contains an invalid value",
                "/api/work-orders/" + orderId);

        assertProblem(mvc.perform(get("/api/work-orders/not-a-uuid"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request parameter is invalid", "/api/work-orders/not-a-uuid");
    }

    @Test
    void unsupportedMethodUsesMethodNotAllowedProblemDetailAndPreservesAllowHeader()
            throws Exception {
        MockHttpServletResponse response = mvc.perform(put("/api/components")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse();

        assertProblem(response, 405, "Method Not Allowed",
                "Request method is not supported", "/api/components");
        String allow = response.getHeader(HttpHeaders.ALLOW);
        assertNotNull(allow);
        assertTrue(allow.contains("GET"));
        assertTrue(allow.contains("POST"));
    }

    @Test
    void unsupportedMediaTypeUsesUnsupportedMediaTypeProblemDetailAndPreservesAcceptHeader()
            throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/components")
                .contentType(MediaType.TEXT_PLAIN)
                .content("component"))
                .andReturn().getResponse();

        assertProblem(response, 415, "Unsupported Media Type",
                "Content type is not supported", "/api/components");
        String accept = response.getHeader(HttpHeaders.ACCEPT);
        assertNotNull(accept);
        assertTrue(accept.contains(MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    void invalidEnumAndDeploymentInvariantUseBadRequestProblemDetail() throws Exception {
        String componentId = createComponent();
        assertProblem(mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "UNKNOWN", "\"2.4.1\"")))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request body is malformed or contains an invalid value", "/api/work-orders");

        assertProblem(mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "DEPLOYMENT", "null")))
                .andReturn().getResponse(), 400, "Bad Request",
                "targetVersion must not be null or blank for deployment", "/api/work-orders");
    }

    @Test
    void unknownResourcesUseNotFoundProblemDetail() throws Exception {
        UUID missing = UUID.randomUUID();
        assertProblem(mvc.perform(get("/api/components/{id}", missing)).andReturn().getResponse(),
                404, "Not Found", "Component " + missing + " was not found", "/api/components/" + missing);
        assertProblem(mvc.perform(get("/api/work-orders/{id}", missing)).andReturn().getResponse(),
                404, "Not Found", "Work order " + missing + " was not found", "/api/work-orders/" + missing);
        assertProblem(mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(missing.toString(), "CORRECTIVE_MAINTENANCE", "null")))
                .andReturn().getResponse(), 404, "Not Found",
                "Component " + missing + " was not found", "/api/work-orders");
        assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", missing)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PLAN\"}"))
                .andReturn().getResponse(), 404, "Not Found",
                "Work order " + missing + " was not found", "/api/work-orders/" + missing + "/transitions");
    }

    @Test
    void transitionsUseDomainBehaviorAndMapInputAndStateFailures() throws Exception {
        String orderId = createWorkOrder();
        MockHttpServletResponse plannedResponse = mvc.perform(
                post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PLAN\"}"))
                .andReturn().getResponse();
        assertEquals(200, plannedResponse.getStatus());
        JsonNode planned = json.readTree(plannedResponse.getContentAsString());
        assertEquals("PLANNED", planned.get("status").asString());

        assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"COMPLETE\"}"))
                .andReturn().getResponse(), 409, "Conflict",
                "Cannot complete work order while status is PLANNED", "/api/work-orders/" + orderId + "/transitions");

        JsonNode inProgress = json.readTree(mvc.perform(post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"START\"}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals("IN_PROGRESS", inProgress.get("status").asString());

        assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"COMPLETE\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Transition input is invalid", "/api/work-orders/" + orderId + "/transitions");

        assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"BLOCK\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Transition input is invalid", "/api/work-orders/" + orderId + "/transitions");

        assertProblem(mvc.perform(post("/api/work-orders/{id}/transitions", orderId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CANCEL\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Transition input is invalid", "/api/work-orders/" + orderId + "/transitions");
    }

    @Test
    void duplicateComponentNameUsesConflictProblemDetail() throws Exception {
        createComponent();

        assertProblem(mvc.perform(post("/api/components")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"configuration-service\",\"description\":\"Duplicate\"}"))
                .andReturn().getResponse(), 409, "Conflict",
                "Component name already exists", "/api/components");
    }

    @Test
    void inactiveComponentRejectsNewWorkOrderWithConflictProblemDetail() throws Exception {
        String componentId = createComponent();
        jdbcTemplate.update("UPDATE software_component SET active = FALSE WHERE id = ?",
                UUID.fromString(componentId));

        assertProblem(mvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "CORRECTIVE_MAINTENANCE", "null")))
                .andReturn().getResponse(), 409, "Conflict",
                "Component " + componentId + " is inactive", "/api/work-orders");
    }

    @Test
    void duplicateComponentRenameUsesConflictProblemDetail() throws Exception {
        createComponent("existing-name");
        String componentId = createComponent("other-name");

        assertProblem(mvc.perform(patch("/api/components/{id}", componentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"existing-name\"}"))
                .andReturn().getResponse(), 409, "Conflict",
                "Component name already exists", "/api/components/" + componentId);
    }

    @Test
    void activeWorkPreventsComponentDeactivationWithConflictProblemDetail() throws Exception {
        String componentId = createComponent("active-work-service");
        MockHttpServletResponse createdWorkOrder = mvc.perform(
                post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(
                        componentId, "CORRECTIVE_MAINTENANCE", "null")))
                .andReturn().getResponse();
        assertEquals(201, createdWorkOrder.getStatus());

        assertProblem(mvc.perform(post("/api/components/{id}/deactivation", componentId))
                .andReturn().getResponse(), 409, "Conflict",
                "Component " + componentId + " has active work orders",
                "/api/components/" + componentId + "/deactivation");
    }

    @Test
    void componentPatchRejectsUnknownForbiddenAndEmptyRequests() throws Exception {
        String componentId = createComponent();
        String path = "/api/components/" + componentId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"unknownField\":\"x\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Component update contains unsupported fields: unknownField", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Component update contains unsupported fields: active", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "At least one component field must be provided", path);
    }

    @Test
    void componentPatchRejectsNullAndBlankNamesAsBadRequests() throws Exception {
        String componentId = createComponent();
        String path = "/api/components/" + componentId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":null}"))
                .andReturn().getResponse(), 400, "Bad Request", "name must not be null", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andReturn().getResponse(), 400, "Bad Request", "name must not be blank", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
                .andReturn().getResponse(), 400, "Bad Request", "name must not be blank", path);
    }

    @Test
    void missingComponentUpdateAndDeactivationUseNotFoundProblemDetail() throws Exception {
        UUID missing = UUID.randomUUID();

        assertProblem(mvc.perform(patch("/api/components/{id}", missing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Updated\"}"))
                .andReturn().getResponse(), 404, "Not Found",
                "Component " + missing + " was not found", "/api/components/" + missing);
        assertProblem(mvc.perform(post("/api/components/{id}/deactivation", missing))
                .andReturn().getResponse(), 404, "Not Found",
                "Component " + missing + " was not found",
                "/api/components/" + missing + "/deactivation");
    }

    @Test
    void workOrderPatchRejectsForbiddenUnknownAndEmptyRequests() throws Exception {
        String orderId = createWorkOrder();
        String path = "/api/work-orders/" + orderId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Work order update contains unsupported fields: status", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"unknownField\":\"x\"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Work order update contains unsupported fields: unknownField", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "At least one work order field must be provided", path);
    }

    @Test
    void workOrderPatchRejectsNullRequiredFieldsAndBlankTitle() throws Exception {
        String orderId = createWorkOrder();
        String path = "/api/work-orders/" + orderId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"componentId\":null}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "componentId must not be null", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":null}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "type must not be null", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":null}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "title must not be null", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "title must not be blank", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":null}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "priority must not be null", path);
    }

    @Test
    void workOrderReassignmentUsesNotFoundInactiveAndLifecycleConflicts() throws Exception {
        String originalComponentId = createComponent("original-reassignment-service");
        String orderId = createWorkOrder(originalComponentId);
        UUID missingTarget = UUID.randomUUID();
        String path = "/api/work-orders/" + orderId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"componentId\":\"" + missingTarget + "\"}"))
                .andReturn().getResponse(), 404, "Not Found",
                "Component " + missingTarget + " was not found", path);

        String inactiveTarget = createComponent("inactive-reassignment-target");
        jdbcTemplate.update("UPDATE software_component SET active = FALSE WHERE id = ?",
                UUID.fromString(inactiveTarget));
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"componentId\":\"" + inactiveTarget + "\"}"))
                .andReturn().getResponse(), 409, "Conflict",
                "Component " + inactiveTarget + " is inactive", path);

        String activeTarget = createComponent("active-reassignment-target");
        MockHttpServletResponse planned = mvc.perform(
                post("/api/work-orders/{id}/transitions", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PLAN\"}"))
                .andReturn().getResponse();
        assertEquals(200, planned.getStatus());
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"componentId\":\"" + activeTarget + "\"}"))
                .andReturn().getResponse(), 409, "Conflict",
                "Cannot change component work order while status is PLANNED", path);
    }

    @Test
    void invalidCombinedDeploymentPatchUsesBadRequestProblemDetail() throws Exception {
        String orderId = createWorkOrder();
        String path = "/api/work-orders/" + orderId;

        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DEPLOYMENT\",\"targetVersion\":null}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "targetVersion must not be null or blank for deployment", path);
        assertProblem(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DEPLOYMENT\",\"targetVersion\":\"   \"}"))
                .andReturn().getResponse(), 400, "Bad Request",
                "targetVersion must not be null or blank for deployment", path);
    }

    private JsonNode assertProblem(MockHttpServletResponse response, int status, String title,
            String detail, String instance) throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        JsonNode problem = json.readTree(response.getContentAsString());
        assertEquals(status, problem.get("status").asInt());
        assertEquals(title, problem.get("title").asString());
        assertEquals(detail, problem.get("detail").asString());
        assertEquals(instance, problem.get("instance").asString());
        return problem;
    }

    private String createComponent() throws Exception {
        return createComponent("configuration-service");
    }

    private String createComponent(String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/components").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":"Configuration API"}
                        """.formatted(name)))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private String createWorkOrder() throws Exception {
        String componentId = createComponent();
        return createWorkOrder(componentId);
    }

    private String createWorkOrder(String componentId) throws Exception {
        return json.readTree(mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "CORRECTIVE_MAINTENANCE", "null")))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private String workOrderRequest(String componentId, String type, String targetVersion) {
        return """
                {"componentId":"%s","title":"Investigate timeout","type":"%s",
                 "priority":"HIGH","targetVersion":%s}
                """.formatted(componentId, type, targetVersion);
    }
}
