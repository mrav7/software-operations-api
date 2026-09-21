package io.github.mrav7.softwareoperationsapi.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthHttpIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void healthReportsApplicationAndDatasourceAsUpWithoutSensitiveDetails() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/actuator/health"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        JsonNode health = json.readTree(response.getContentAsString());
        assertEquals("UP", health.get("status").asString());
        assertEquals("UP", health.get("components").get("db").get("status").asString());
        assertFalse(response.getContentAsString().contains("jdbc:"));
        assertFalse(response.getContentAsString().contains("DB_PASSWORD"));
    }

    @Test
    void actuatorEnvironmentEndpointIsNotExposed() throws Exception {
        assertEquals(404, mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus());
    }
}
