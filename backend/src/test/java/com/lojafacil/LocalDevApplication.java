package com.lojafacil;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

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
            try { pg.close(); } catch (Exception ignored) {}
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        SpringApplication.run(LojaFacilApplication.class, args);
    }
}
