package com.lojafacil.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações próprias da aplicação (prefixo "app" no application.yml).
 * Valores sensíveis chegam por variáveis de ambiente, nunca pelo repositório.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        /** URL do frontend, usada em links de e-mail e retornos do checkout. */
        String frontendBaseUrl,
        /** URL pública da API, usada como notification_url dos webhooks. */
        String publicApiBaseUrl,
        List<String> corsAllowedOrigins,
        String uploadsDir,
        /** Chave AES-256 em Base64 para cifrar credenciais de integração. */
        String encryptionKey,
        /** Ambiente demonstrativo: bloqueia credenciais reais de pagamento. */
        boolean demoMode,
        String mailFrom,
        Payments payments) {

    public record Payments(
            String mercadoPagoApiBase,
            /** Quando false, transações com live_mode=true são rejeitadas. */
            boolean allowLiveTransactions,
            /** Minutos após os quais pagamentos pendentes são conferidos no provedor. */
            int reconcileAfterMinutes) {
    }
}
