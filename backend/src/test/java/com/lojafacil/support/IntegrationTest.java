package com.lojafacil.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Base dos testes de integração: usa um PostgreSQL REAL.
 * Usamos o banco real porque travas de linha (SELECT ... FOR UPDATE), CHECKs e índices únicos
 * são justamente o que estamos testando — um banco em memória (H2) não reproduziria esse comportamento.
 *
 * Com Docker disponível: Testcontainers (postgres:17-alpine).
 * Sem Docker (ou com LF_TEST_DB=embedded): PostgreSQL 17 embutido, baixado como dependência Maven.
 * Em ambos os casos o banco é único para toda a suíte, porque o contexto Spring é reaproveitado entre classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jobs.expiration-delay-ms=3600000",
        "app.jobs.reconcile-delay-ms=3600000",
        "app.uploads-dir=target/test-uploads",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1"
})
public abstract class IntegrationTest {

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        if (!"embedded".equalsIgnoreCase(System.getenv("LF_TEST_DB")) && dockerAvailable()) {
            PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
            URL = container.getJdbcUrl();
            USER = container.getUsername();
            PASSWORD = container.getPassword();
        } else {
            try {
                EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { pg.close(); } catch (IOException ignored) { /* encerrando */ }
                }));
                URL = pg.getJdbcUrl("postgres", "postgres");
                USER = "postgres";
                PASSWORD = "postgres";
            } catch (IOException e) {
                throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embutido", e);
            }
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Autowired
    protected TestData data;
}
