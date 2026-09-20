package io.github.mrav7.softwareoperationsapi.web;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
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

        assertProblem(mvc.perform(get("/api/work-orders/not-a-uuid"))
                .andReturn().getResponse(), 400, "Bad Request",
                "Request parameter is invalid", "/api/work-orders/not-a-uuid");
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
        return json.readTree(mvc.perform(post("/api/components").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"configuration-service\",\"description\":\"Configuration API\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    private String createWorkOrder() throws Exception {
        String componentId = createComponent();
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
