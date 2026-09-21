package io.github.mrav7.softwareoperationsapi.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import io.github.mrav7.softwareoperationsapi.domain.WorkOrder;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderStatus;
import io.github.mrav7.softwareoperationsapi.domain.WorkOrderType;
import io.github.mrav7.softwareoperationsapi.persistence.SoftwareComponentRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkLogRepository;
import io.github.mrav7.softwareoperationsapi.persistence.WorkOrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class CollectionPaginationHttpIntegrationTest {
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

    private SoftwareComponent firstComponent;
    private SoftwareComponent secondComponent;
    private List<WorkOrder> orders;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        firstComponent = componentRepository.saveAndFlush(new SoftwareComponent(
                "http-first-" + UUID.randomUUID(), "First HTTP component"));
        secondComponent = componentRepository.saveAndFlush(new SoftwareComponent(
                "http-second-" + UUID.randomUUID(), "Second HTTP component"));
        orders = List.of(
                persist(firstComponent, WorkOrderType.DEPLOYMENT, Priority.CRITICAL,
                        WorkOrderStatus.BLOCKED),
                persist(firstComponent, WorkOrderType.DEPLOYMENT, Priority.HIGH,
                        WorkOrderStatus.CREATED),
                persist(firstComponent, WorkOrderType.CORRECTIVE_MAINTENANCE, Priority.CRITICAL,
                        WorkOrderStatus.BLOCKED),
                persist(secondComponent, WorkOrderType.PREVENTIVE_MAINTENANCE, Priority.MEDIUM,
                        WorkOrderStatus.PLANNED),
                persist(secondComponent, WorkOrderType.OPERATIONAL_SUPPORT, Priority.LOW,
                        WorkOrderStatus.IN_PROGRESS));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void workOrderCollectionUsesFrozenPageResponseForDefaultPagingAndBoundaries() throws Exception {
        JsonNode defaultPage = body(get("/api/work-orders"));
        assertPageShape(defaultPage, 0, 20, 5, 1, 5);

        JsonNode firstPage = body(get("/api/work-orders?page=0&size=2"));
        JsonNode secondPage = body(get("/api/work-orders?page=1&size=2"));
        JsonNode lastPage = body(get("/api/work-orders?page=2&size=2"));
        JsonNode beyondLast = body(get("/api/work-orders?page=20&size=2"));
        assertPageShape(firstPage, 0, 2, 5, 3, 2);
        assertPageShape(secondPage, 1, 2, 5, 3, 2);
        assertPageShape(lastPage, 2, 2, 5, 3, 1);
        assertPageShape(beyondLast, 20, 2, 5, 3, 0);
        assertFalse(ids(firstPage).stream().anyMatch(ids(secondPage)::contains));
        assertFalse(ids(firstPage).stream().anyMatch(ids(lastPage)::contains));
        assertFalse(ids(secondPage).stream().anyMatch(ids(lastPage)::contains));
        assertEquals(200, mvc.perform(get("/api/work-orders?size=100"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void workOrderFiltersBindIndividuallyAndCombineWithAndSemantics() throws Exception {
        assertPageShape(body(get("/api/work-orders?componentId=" + firstComponent.getId())),
                0, 20, 3, 1, 3);
        assertPageShape(body(get("/api/work-orders?status=BLOCKED")), 0, 20, 2, 1, 2);
        assertPageShape(body(get("/api/work-orders?type=DEPLOYMENT")), 0, 20, 2, 1, 2);
        assertPageShape(body(get("/api/work-orders?priority=CRITICAL")), 0, 20, 2, 1, 2);
        assertPageShape(body(get("/api/work-orders?componentId=" + firstComponent.getId()
                + "&status=BLOCKED")), 0, 20, 2, 1, 2);
        assertPageShape(body(get("/api/work-orders?status=BLOCKED&priority=CRITICAL")),
                0, 20, 2, 1, 2);
        assertPageShape(body(get("/api/work-orders?type=DEPLOYMENT&priority=CRITICAL")),
                0, 20, 1, 1, 1);
        assertPageShape(body(get("/api/work-orders?componentId=" + firstComponent.getId()
                + "&status=BLOCKED&type=DEPLOYMENT&priority=CRITICAL")), 0, 20, 1, 1, 1);
        assertPageShape(body(get("/api/work-orders?componentId=" + secondComponent.getId()
                + "&status=BLOCKED&type=DEPLOYMENT&priority=CRITICAL")), 0, 20, 0, 0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page=-1", "size=0", "size=-5", "size=101", "status=INVALID",
            "type=INVALID", "priority=INVALID", "componentId=not-a-uuid"
    })
    void invalidWorkOrderQueryParametersUseBadRequestProblemDetail(String query) throws Exception {
        assertProblem(get("/api/work-orders?" + query));
    }

    @Test
    void componentsUseTheSamePageContractWithFixedOrderAndBoundaryHandling() throws Exception {
        componentRepository.saveAndFlush(new SoftwareComponent(
                "http-third-" + UUID.randomUUID(), "Third HTTP component"));
        componentRepository.saveAndFlush(new SoftwareComponent(
                "http-fourth-" + UUID.randomUUID(), "Fourth HTTP component"));

        JsonNode first = body(get("/api/components?page=0&size=2"));
        JsonNode second = body(get("/api/components?page=1&size=2"));
        JsonNode last = body(get("/api/components?page=2&size=2"));
        JsonNode beyondLast = body(get("/api/components?page=20&size=2"));
        assertPageShape(first, 0, 2, 4, 2, 2);
        assertPageShape(second, 1, 2, 4, 2, 2);
        assertPageShape(last, 2, 2, 4, 2, 0);
        assertPageShape(beyondLast, 20, 2, 4, 2, 0);
        assertFalse(ids(first).stream().anyMatch(ids(second)::contains));
        assertEquals(200, mvc.perform(get("/api/components?size=100"))
                .andReturn().getResponse().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = { "page=-1", "size=0", "size=101" })
    void invalidComponentPaginationUsesBadRequestProblemDetail(String query) throws Exception {
        assertProblem(get("/api/components?" + query));
    }

    private WorkOrder persist(SoftwareComponent component, WorkOrderType type, Priority priority,
            WorkOrderStatus status) {
        WorkOrder order = new WorkOrder(component, "HTTP fixture " + UUID.randomUUID(), null,
                type, priority, type == WorkOrderType.DEPLOYMENT ? "1.0.0" : null);
        switch (status) {
            case CREATED -> { }
            case PLANNED -> order.plan();
            case IN_PROGRESS -> {
                order.plan();
                order.start();
            }
            case BLOCKED -> {
                order.plan();
                order.start();
                order.block("Fixture block");
            }
            case COMPLETED -> {
                order.plan();
                order.start();
                order.complete("Fixture completion");
            }
            case CANCELLED -> order.cancel("Fixture cancellation");
        }
        return workOrderRepository.saveAndFlush(order);
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        return json.readTree(response.getContentAsString());
    }

    private void assertProblem(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
    }

    private static void assertPageShape(JsonNode body, int page, int size, int totalElements,
            int totalPages, int itemCount) {
        assertEquals(Set.of("items", "page", "size", "totalElements", "totalPages"),
                body.properties().stream().map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(page, body.get("page").asInt());
        assertEquals(size, body.get("size").asInt());
        assertEquals(totalElements, body.get("totalElements").asInt());
        assertEquals(totalPages, body.get("totalPages").asInt());
        assertEquals(itemCount, body.get("items").size());
    }

    private static List<String> ids(JsonNode body) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : body.get("items")) {
            ids.add(item.get("id").asString());
        }
        return ids;
    }

    private void cleanDatabase() {
        workLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        componentRepository.deleteAll();
    }
}
