# Modelo de dados

Esquema criado pela migração Flyway `backend/src/main/resources/db/migration/V1__schema_inicial.sql`.
Todas as tabelas de negócio têm `store_id`: é a base do isolamento entre lojas.

```mermaid
erDiagram
    stores ||--o{ users : "equipe"
    stores ||--o{ user_invitations : "convites"
    users ||--o{ password_reset_tokens : ""
    stores ||--o{ categories : ""
    stores ||--o{ products : ""
    categories |o--o{ products : ""
    products ||--|{ product_variants : "1+ variantes (SKU + estoque)"
    products ||--o{ product_images : ""
    product_variants ||--o{ stock_movements : "histórico"
    product_variants ||--o{ stock_reservations : "reservas"
    stores ||--o{ customers : ""
    customers ||--o{ customer_addresses : ""
    stores ||--o{ suppliers : ""
    customers |o--o{ orders : ""
    orders ||--|{ order_items : "fotografia da venda"
    orders ||--o{ order_history : "linha do tempo"
    orders ||--o{ order_returns : ""
    order_returns ||--|{ order_return_items : ""
    orders ||--o{ stock_reservations : ""
    orders ||--o{ payments : ""
    payments ||--o{ refunds : ""
    stores ||--o| payment_integrations : "credenciais cifradas"
    stores ||--o{ financial_categories : ""
    financial_categories ||--o{ financial_entries : ""
    orders |o--o{ financial_entries : "vendas, taxas, reembolsos"
    financial_entries |o--o| financial_entries : "estorno de"
    stores ||--o{ audit_log : ""

    product_variants {
        int physical_qty "CHECK >= 0"
        int reserved_qty "CHECK >= 0 e <= physical"
    }
    orders {
        int number "único por loja"
        varchar tracking_token_hash "SHA-256 do link"
        varchar status "OPEN, CONFIRMED, CANCELLED, COMPLETED"
        varchar payment_status "PENDING ... REFUNDED"
        varchar fulfillment_status "AWAITING ... RETURNED"
        bool stock_committed "baixa feita uma única vez"
    }
    payments {
        uuid public_id "external_reference"
        uuid idempotency_key "X-Idempotency-Key"
        varchar provider "SIMULATOR, MERCADO_PAGO, MANUAL"
    }
    financial_entries {
        numeric amount "NUMERIC(14,2) > 0"
        varchar source_type "MANUAL, ORDER_PAYMENT, PAYMENT_FEE, REFUND, REVERSAL"
        varchar source_ref "único por loja: evita duplicidade"
    }
```

Tabelas de apoio não mostradas: `webhook_events` (deduplicação de notificações, chave única
`provider + event_key`) e `simulator_charges` (o "lado do provedor" do simulador local).

## Decisões de modelagem

- **Toda unidade vendável é uma variante.** Produto simples = uma variante "padrão". Assim estoque,
  reservas e itens de pedido apontam sempre para o mesmo tipo de registro.
- **Saldo físico e reservado na variante**, com `CHECK` no banco. Disponível = físico − reservado.
- **Valores sempre positivos** em `financial_entries`; o tipo (`INCOME`/`EXPENSE`) define o sinal.
  Um estorno é um novo lançamento do tipo oposto apontando para o original (`reversal_of_id`).
- **Chaves únicas parciais** (`WHERE source_ref IS NOT NULL`, `WHERE provider_payment_id IS NOT NULL`)
  garantem idempotência sem impedir lançamentos manuais.
- **Tokens guardados como hash** (`tracking_token_hash`, `token_hash`): se o banco vazar, links continuam inutilizáveis.
