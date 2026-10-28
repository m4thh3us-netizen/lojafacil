package com.lojafacil.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Habilita repositórios declarados como interfaces internas (ex.: {@code Invitation.Repo}),
 * o que mantém entidades pequenas e seus repositórios no mesmo arquivo.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.lojafacil", considerNestedRepositories = true)
public class JpaConfig {
}
