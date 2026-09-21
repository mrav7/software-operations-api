package io.github.mrav7.softwareoperationsapi.testsupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDatabaseSafetyGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {"software_operations_api_test", "another_project_test"})
    void acceptsDatabaseNamesWithTestSuffix(String databaseName) {
        assertDoesNotThrow(() -> TestDatabaseSafetyGuard.requireSafeDatabaseName(databaseName));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "software_operations_api_dev", "software_operations_api", "postgres"})
    void rejectsDatabaseNamesWithoutTestSuffix(String databaseName) {
        assertThrows(IllegalStateException.class,
                () -> TestDatabaseSafetyGuard.requireSafeDatabaseName(databaseName));
    }
}
