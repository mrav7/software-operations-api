package io.github.mrav7.softwareoperationsapi.web;

import java.util.UUID;

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

import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class HttpBoundaryTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private SoftwareComponentRepository componentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @BeforeEach
    void cleanDatabase() {
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }

    @Test
    void componentCanBeCreatedAndRetrievedAtItsLocation() throws Exception {
        MockHttpServletResponse created = createComponent();
        JsonNode body = json.readTree(created.getContentAsString());

        assertEquals(201, created.getStatus());
        UUID.fromString(body.get("id").asString());
        assertEquals("configuration-service", body.get("name").asString());
        assertEquals("Configuration API", body.get("description").asString());
        assertTrue(body.get("active").asBoolean());
        assertFalse(body.get("createdAt").isNull());
        assertEquals(body.get("createdAt"), body.get("updatedAt"));
        assertEquals("/api/components/" + body.get("id").asString(), created.getHeader("Location"));

        MockHttpServletResponse retrieved = mvc.perform(get(created.getHeader("Location")))
                .andReturn().getResponse();
        assertEquals(200, retrieved.getStatus());
        assertTrue(retrieved.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertEquals(body, json.readTree(retrieved.getContentAsString()));
    }

    @Test
    void persistedComponentsCanBeListedWithoutRelyingOnDatabaseOrder() throws Exception {
        String firstId = json.readTree(createComponent("alpha-service", "Alpha")
                .getContentAsString()).get("id").asString();
        String secondId = json.readTree(createComponent("beta-service", "Beta")
                .getContentAsString()).get("id").asString();

        MockHttpServletResponse response = mvc.perform(get("/api/components"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        JsonNode body = json.readTree(response.getContentAsString());
        assertEquals(2, body.size());
        assertTrue(containsId(body, firstId));
        assertTrue(containsId(body, secondId));
    }

    @Test
    void componentNamePatchPersists() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString())
                .get("id").asString();

        MockHttpServletResponse response = mvc.perform(patch("/api/components/{id}", componentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"renamed-service\"}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("renamed-service",
                json.readTree(response.getContentAsString()).get("name").asString());
        assertEquals("renamed-service", componentRepository.findById(UUID.fromString(componentId))
                .orElseThrow().getName());
    }

    @Test
    void explicitNullDescriptionPatchClearsPersistedDescription() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString())
                .get("id").asString();

        MockHttpServletResponse response = mvc.perform(patch("/api/components/{id}", componentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":null}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertTrue(json.readTree(response.getContentAsString()).get("description").isNull());
        assertNull(componentRepository.findById(UUID.fromString(componentId))
                .orElseThrow().getDescription());
    }

    @Test
    void componentWithoutActiveWorkCanBeDeactivated() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString())
                .get("id").asString();

        MockHttpServletResponse response = mvc.perform(
                post("/api/components/{id}/deactivation", componentId))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertFalse(json.readTree(response.getContentAsString()).get("active").asBoolean());
        assertFalse(componentRepository.findById(UUID.fromString(componentId))
                .orElseThrow().isActive());
    }

    @Test
    void workOrderUsesRegisteredComponentAndDomainInitialState() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString()).get("id").asString();
        MockHttpServletResponse created = mvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "\"2.4.1\"")))
                .andReturn().getResponse();
        assertEquals(201, created.getStatus());
        JsonNode body = json.readTree(created.getContentAsString());
        UUID orderId = UUID.fromString(body.get("id").asString());
        assertEquals(componentId, body.get("componentId").asString());
        assertEquals("Deploy release", body.get("title").asString());
        assertEquals("Release update", body.get("description").asString());
        assertEquals("DEPLOYMENT", body.get("type").asString());
        assertEquals("HIGH", body.get("priority").asString());
        assertEquals("CREATED", body.get("status").asString());
        assertEquals("2.4.1", body.get("targetVersion").asString());
        assertFalse(body.has("component"));
        assertNotNull(body.get("createdAt"));
        assertEquals(body.get("createdAt"), body.get("updatedAt"));
        assertEquals("/api/work-orders/" + orderId, created.getHeader("Location"));

        MockHttpServletResponse retrieved = mvc.perform(get(created.getHeader("Location")))
                .andReturn().getResponse();
        assertEquals(200, retrieved.getStatus());
        assertEquals(body, json.readTree(retrieved.getContentAsString()));
    }

    @Test
    void unknownIdsDoNotPreventSubsequentRequests() throws Exception {
        UUID missingId = UUID.randomUUID();
        assertEquals(404, mvc.perform(get("/api/components/{id}", missingId))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/work-orders/{id}", missingId))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(missingId.toString(), "\"2.4.1\"")))
                .andReturn().getResponse().getStatus());
        assertTrue(componentRepository.findById(missingId).isEmpty());
        assertTrue(workOrderRepository.findById(missingId).isEmpty());
        assertEquals(201, createComponent().getStatus());
    }

    @Test
    void httpCreationDoesNotBypassDeploymentInvariant() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString()).get("id").asString();
        MockHttpServletResponse response = mvc.perform(
                post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(workOrderRequest(componentId, "null")))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
    }

    @Test
    void lifecycleTransitionsRemainVisibleThroughSubsequentHttpReads() throws Exception {
        String orderId = createCorrectiveWorkOrder();

        transition(orderId, "{\"action\":\"PLAN\"}", "PLANNED");
        assertRetrievedStatus(orderId, "PLANNED");
        transition(orderId, "{\"action\":\"START\"}", "IN_PROGRESS");
        assertRetrievedStatus(orderId, "IN_PROGRESS");
        transition(orderId,
                "{\"action\":\"BLOCK\",\"blockingReason\":\"Waiting for access\"}",
                "BLOCKED");
        assertRetrievedStatus(orderId, "BLOCKED");
        transition(orderId, "{\"action\":\"RESUME\"}", "IN_PROGRESS");
        assertRetrievedStatus(orderId, "IN_PROGRESS");
        transition(orderId,
                "{\"action\":\"COMPLETE\",\"resolutionSummary\":\"Access restored\"}",
                "COMPLETED");
        assertRetrievedStatus(orderId, "COMPLETED");
    }

    @Test
    void cancellationRemainsVisibleThroughSubsequentHttpRead() throws Exception {
        String orderId = createCorrectiveWorkOrder();

        transition(orderId,
                "{\"action\":\"CANCEL\",\"cancellationReason\":\"Work superseded\"}",
                "CANCELLED");
        assertRetrievedStatus(orderId, "CANCELLED");
    }

    private MockHttpServletResponse createComponent() throws Exception {
        return createComponent("configuration-service", "Configuration API");
    }

    private MockHttpServletResponse createComponent(String name, String description) throws Exception {
        return mvc.perform(post("/api/components").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","description":"%s"}
                        """.formatted(name, description)))
                .andReturn().getResponse();
    }

    private static boolean containsId(JsonNode components, String id) {
        for (JsonNode component : components) {
            if (id.equals(component.get("id").asString())) {
                return true;
            }
        }
        return false;
    }

    private String createCorrectiveWorkOrder() throws Exception {
        String componentId = json.readTree(createComponent().getContentAsString())
                .get("id").asString();
        MockHttpServletResponse response = mvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workOrderRequest(componentId, "null").replace(
                        "\"DEPLOYMENT\"", "\"CORRECTIVE_MAINTENANCE\"")))
                .andReturn().getResponse();
        assertEquals(201, response.getStatus());
        return json.readTree(response.getContentAsString()).get("id").asString();
    }

    private void transition(String orderId, String request, String expectedStatus) throws Exception {
        MockHttpServletResponse response = mvc.perform(
                post("/api/work-orders/{id}/transitions", orderId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(expectedStatus,
                json.readTree(response.getContentAsString()).get("status").asString());
    }

    private void assertRetrievedStatus(String orderId, String expectedStatus) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/work-orders/{id}", orderId))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(expectedStatus,
                json.readTree(response.getContentAsString()).get("status").asString());
    }

    private String workOrderRequest(String componentId, String targetVersionJson) {
        return """
                {"componentId":"%s","title":"Deploy release","description":"Release update",
                 "type":"DEPLOYMENT","priority":"HIGH","targetVersion":%s}
                """.formatted(componentId, targetVersionJson);
    }
}
