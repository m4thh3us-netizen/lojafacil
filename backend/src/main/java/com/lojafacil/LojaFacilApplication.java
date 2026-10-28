package com.lojafacil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação.
 *
 * O backend é um monólito modular: cada pacote de primeiro nível (auth, store, catalog,
 * inventory, customers, orders, payments, finance, dashboard) representa um módulo de negócio.
 * O pacote "shared" contém apenas infraestrutura comum (segurança, erros, auditoria, utilitários).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class LojaFacilApplication {

    public static void main(String[] args) {
        SpringApplication.run(LojaFacilApplication.class, args);
    }
}
