package com.lojafacil.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.lojafacil", considerNestedRepositories = true)
public class JpaConfig {
}
