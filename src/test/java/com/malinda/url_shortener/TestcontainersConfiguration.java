package com.malinda.url_shortener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway PostgreSQL for the test suite.
 *
 * @ServiceConnection makes Spring Boot point the DataSource (and therefore Flyway and JPA)
 * at this container instead of spring.datasource.* from application.yaml, so tests never
 * touch the Docker Compose database. Flyway creates the schema from db/migration.
 *
 * Image: postgres:18-alpine, the same image docker-compose.yaml uses.
 * Every @SpringBootTest class imports this configuration. The container is a bean, so it
 * lives as long as the (cached) application context that owns it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE)
                .asCompatibleSubstituteFor("postgres"));
    }
}
