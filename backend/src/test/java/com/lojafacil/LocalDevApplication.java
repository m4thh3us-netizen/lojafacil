package com.lojafacil;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Executa o backend SEM Docker, com um PostgreSQL 17 embutido.
 *
 *   mvn spring-boot:test-run -Dspring-boot.run.profiles=demo
 *
 * Os dados ficam em backend/data/dev-postgres (fora do controle de versão) e sobrevivem a reinícios.
 * Porta do banco: 54329, para não conflitar com um PostgreSQL já instalado ou com o Docker Compose.
 * Uso exclusivo em desenvolvimento: em produção use um PostgreSQL gerenciado.
 */
public final class LocalDevApplication {

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of("data", "dev-postgres").toAbsolutePath();
        Files.createDirectories(dataDir);
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(54329)
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { pg.close(); } catch (Exception ignored) { /* encerrando */ }
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        SpringApplication.run(LojaFacilApplication.class, args);
    }
}
