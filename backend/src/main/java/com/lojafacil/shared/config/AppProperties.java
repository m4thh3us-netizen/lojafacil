package com.lojafacil.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(

        String frontendBaseUrl,

        String publicApiBaseUrl,
        List<String> corsAllowedOrigins,
        String uploadsDir,

        String encryptionKey,

        boolean demoMode,
        String mailFrom,
        Payments payments) {
    public record Payments(
            String mercadoPagoApiBase,

            boolean allowLiveTransactions,

            int reconcileAfterMinutes) {
    }
}
