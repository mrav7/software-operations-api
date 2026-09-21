package io.github.mrav7.softwareoperationsapi.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ApplicationLifecycleLogger {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    private final ApplicationProperties applicationProperties;

    ApplicationLifecycleLogger(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logStartup() {
        log.info("Application started: environment={}", applicationProperties.environment());
    }

    @EventListener(ContextClosedEvent.class)
    void logShutdown() {
        log.info("Application shutdown initiated.");
    }
}
