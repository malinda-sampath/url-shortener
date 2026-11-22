package com.malinda.url_shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: the suite talks to the Testcontainers PostgreSQL, never to the Compose database
 * (jdbc:postgresql://localhost:5432/shortener), and Flyway built the schema there.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TestDatabaseIsolationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void dataSourcePointsAtTheTestContainerNotTheComposeDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).isEqualTo(postgres.getJdbcUrl());
            assertThat(url).doesNotContain("localhost:5432/shortener");
        }
        String database = jdbc.sql("SELECT current_database()").query(String.class).single();
        assertThat(database).isEqualTo(postgres.getDatabaseName()).isNotEqualTo("shortener");
    }

    @Test
    void flywayCreatedTheSchemaInTheTestContainer() {
        Integer applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
                .query(Integer.class).single();
        assertThat(applied).isEqualTo(1);

        Integer linksTable = jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name = 'links'")
                .query(Integer.class).single();
        assertThat(linksTable).isEqualTo(1);
    }
}
