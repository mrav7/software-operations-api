package io.github.mrav7.softwareoperationsapi.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class TestDatabaseSafetyGuard {
    private static final String DATABASE_NAME_QUERY = "SELECT current_database()";

    TestDatabaseSafetyGuard(JdbcTemplate jdbcTemplate) {
        requireSafeDatabaseName(jdbcTemplate.queryForObject(DATABASE_NAME_QUERY, String.class));
    }

    static void requireSafeDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank() || !databaseName.endsWith("_test")) {
            throw new IllegalStateException(
                    "Refusing to run integration tests: current PostgreSQL database name must end with _test");
        }
    }
}
