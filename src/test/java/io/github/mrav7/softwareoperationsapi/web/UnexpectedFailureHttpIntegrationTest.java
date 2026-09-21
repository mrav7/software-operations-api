package io.github.mrav7.softwareoperationsapi.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UnexpectedFailureHttpIntegrationTest.ThrowingControllerConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedFailureHttpIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void unexpectedExceptionIsLoggedAndReturnedAsSanitizedProblemDetail(CapturedOutput output)
            throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/test-support/unexpected-failure"))
                .andReturn().getResponse();

        assertEquals(500, response.getStatus());
        JsonNode problem = json.readTree(response.getContentAsString());
        assertEquals("An unexpected error occurred", problem.get("detail").asString());
        assertEquals("/test-support/unexpected-failure", problem.get("instance").asString());
        assertFalse(response.getContentAsString().contains("test-only internal detail"));
        assertTrue(output.getOut().contains(
                "Unexpected request failure: method=GET path=/test-support/unexpected-failure"));
        assertTrue(output.getOut().contains("test-only internal detail"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ThrowingControllerConfiguration {
        @Bean
        ThrowingController throwingController() {
            return new ThrowingController();
        }
    }

    @Controller
    static class ThrowingController {
        @GetMapping("/test-support/unexpected-failure")
        void throwUnexpectedly() {
            throw new IllegalStateException("test-only internal detail");
        }
    }
}
