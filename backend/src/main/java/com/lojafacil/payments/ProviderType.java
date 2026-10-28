package com.lojafacil.payments;

public enum ProviderType {
    /** Simulador local: permite executar o projeto sem credenciais. Pagamentos ficam marcados como SIMULADOS. */
    SIMULATOR,
    /** Mercado Pago (Checkout Pro), em ambiente de teste. */
    MERCADO_PAGO,
    /** Recebimento registrado manualmente pela equipe (dinheiro, maquininha, Pix fora do sistema). */
    MANUAL
}
