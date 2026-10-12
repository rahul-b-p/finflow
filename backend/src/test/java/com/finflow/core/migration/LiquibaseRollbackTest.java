package com.finflow.core.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThatCode;

class LiquibaseRollbackTest {

    @Test
    @DisplayName("Applies all changelogs and rolls them back cleanly")
    void shouldExecuteMigrationAndRollback() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:liquibase_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        assertThatCode(() -> {
            try (Connection connection = dataSource.getConnection()) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));

                Liquibase liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        new ClassLoaderResourceAccessor(),
                        database
                );

                // Run migration forward
                liquibase.update(new Contexts(), new LabelExpression());

                // Roll back all 4 changesets
                liquibase.rollback(4, new Contexts(), new LabelExpression());

                // Re-apply to ensure idempotency
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }).doesNotThrowAnyException();
    }
}
