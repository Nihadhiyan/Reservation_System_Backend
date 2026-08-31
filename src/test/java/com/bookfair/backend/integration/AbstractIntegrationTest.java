package com.bookfair.backend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

/**
 * Shared base for every Testcontainers-backed integration test. Containers are
 * started ONCE per JVM (static fields, no @Container/@Testcontainers lifecycle
 * management restarting them per class) and reused across every subclass —
 * starting a fresh Postgres+Kafka pair per test class would make the suite
 * take minutes instead of seconds without buying any extra isolation, since
 * each test already gets a clean slate via @BeforeEach cleanup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("clausis_test")
            .withReuse(true);

    // The org.testcontainers.containers.KafkaContainer class (not the newer
    // org.testcontainers.kafka.KafkaContainer, which targets the apache/kafka
    // native image's own startup scripts and fails against Confluent images even
    // with asCompatibleSubstituteFor) — this one is built for Confluent images and
    // has first-class KRaft support via withKraft().
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"))
            .withKraft()
            .withReuse(true);

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void cleanDatabase() throws Exception {
        // Truncate every app table between tests rather than relying on transaction
        // rollback — @SpringBootTest with RANDOM_PORT makes real HTTP calls through
        // a separate thread/connection than the test method, so @Transactional
        // test-rollback doesn't apply here the way it does for @DataJpaTest-style tests.
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE
                        reservations, event_space_bookings, event_stalls, events,
                        layout_markers, stalls, halls, floors, buildings, venues,
                        organization_invites, organization_members, organizations,
                        refresh_tokens, users, genres, payments, event_settlements,
                        transaction_histories, pricing_rules, failed_tasks
                    RESTART IDENTITY CASCADE
                    """);
        }
    }
}
