package io.github.mrav7.softwareoperationsapi.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "app.environment=phase10-test")
class ApplicationPropertiesIntegrationTest {
    @Autowired
    private ApplicationProperties applicationProperties;

    @Test
    void bindsExplicitApplicationEnvironment() {
        assertEquals("phase10-test", applicationProperties.environment());
    }
}
