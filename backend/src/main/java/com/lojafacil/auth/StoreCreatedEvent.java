package com.lojafacil.auth;

/**
 * Publicado quando uma loja nova é criada. Outros módulos (ex.: financeiro) reagem a ele
 * para criar seus dados iniciais sem que o módulo de autenticação precise conhecê-los.
 */
public record StoreCreatedEvent(Long storeId) {
}
