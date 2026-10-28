-- =====================================================================
-- LojaFácil - esquema inicial
-- Convenções:
--   * valores monetários: NUMERIC(14,2) (nunca float/double)
--   * toda tabela de negócio possui store_id para isolamento entre lojas
--   * CHECKs no banco reforçam regras críticas (ex.: estoque nunca negativo)
-- =====================================================================

CREATE TABLE stores (
    id                     BIGSERIAL PRIMARY KEY,
    slug                   VARCHAR(60)  NOT NULL UNIQUE,
    name                   VARCHAR(120) NOT NULL,
    description            VARCHAR(1000),
    logo_path              VARCHAR(255),
    banner_path            VARCHAR(255),
    primary_color          VARCHAR(7)   NOT NULL DEFAULT '#1F5EFF',
    secondary_color        VARCHAR(7)   NOT NULL DEFAULT '#0F172A',
    contact_email          VARCHAR(160),
    contact_phone          VARCHAR(30),
    whatsapp               VARCHAR(30),
    instagram              VARCHAR(120),
    facebook               VARCHAR(120),
    address_line           VARCHAR(200),
    address_city           VARCHAR(100),
    address_state          VARCHAR(2),
    address_zip            VARCHAR(9),
    service_hours          VARCHAR(300),
    pickup_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    local_delivery_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    local_delivery_fee     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (local_delivery_fee >= 0),
    reservation_minutes    INT          NOT NULL DEFAULT 30 CHECK (reservation_minutes BETWEEN 5 AND 1440),
    next_order_number      INT          NOT NULL DEFAULT 1,
    demo                   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    store_id       BIGINT       NOT NULL REFERENCES stores(id),
    full_name      VARCHAR(120) NOT NULL,
    email          VARCHAR(160) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    phone          VARCHAR(30),
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER','MANAGER','OPERATOR')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_store ON users (store_id);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_invitations (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT       NOT NULL REFERENCES stores(id),
    email        VARCHAR(160) NOT NULL,
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('MANAGER','OPERATOR')),
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    invited_by   BIGINT       NOT NULL REFERENCES users(id),
    expires_at   TIMESTAMPTZ  NOT NULL,
    accepted_at  TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_invitations_store ON user_invitations (store_id);

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT      NOT NULL REFERENCES stores(id),
    user_id      BIGINT      REFERENCES users(id),
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    BIGINT,
    action       VARCHAR(60) NOT NULL,
    details      VARCHAR(2000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_store_date ON audit_log (store_id, created_at DESC);

-- ---------------------------------------------------------------- catálogo
CREATE TABLE categories (
    id        BIGSERIAL PRIMARY KEY,
    store_id  BIGINT      NOT NULL REFERENCES stores(id),
    name      VARCHAR(80) NOT NULL,
    slug      VARCHAR(90) NOT NULL,
    active    BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (store_id, slug)
);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    store_id      BIGINT        NOT NULL REFERENCES stores(id),
    category_id   BIGINT        REFERENCES categories(id),
    name          VARCHAR(160)  NOT NULL,
    slug          VARCHAR(180)  NOT NULL,
    description   VARCHAR(4000),
    sku           VARCHAR(60)   NOT NULL,
    barcode       VARCHAR(60),
    cost_price    NUMERIC(14,2) NOT NULL CHECK (cost_price >= 0),
    sale_price    NUMERIC(14,2) NOT NULL CHECK (sale_price > 0),
    promo_price   NUMERIC(14,2) CHECK (promo_price > 0),
    -- preço praticado (promocional, se houver); mantido pela aplicação para filtrar/ordenar
    effective_price NUMERIC(14,2) NOT NULL,
    unit         VARCHAR(10)   NOT NULL DEFAULT 'UN',
    min_stock     INT           NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    option1_name  VARCHAR(40),
    option2_name  VARCHAR(40),
    has_variants  BOOLEAN       NOT NULL DEFAULT FALSE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    visible       BOOLEAN       NOT NULL DEFAULT TRUE,
    featured      BOOLEAN       NOT NULL DEFAULT FALSE,
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (store_id, slug),
    UNIQUE (store_id, sku),
    CHECK (promo_price IS NULL OR promo_price < sale_price)
);
CREATE INDEX ix_products_store_cat ON products (store_id, category_id);
CREATE INDEX ix_products_store_name ON products (store_id, lower(name));

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id),
    path        VARCHAR(255) NOT NULL,
    position    INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_product_images_product ON product_images (product_id);

-- Toda unidade vendável é uma variante. Produto simples = uma única variante padrão.
CREATE TABLE product_variants (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT        NOT NULL REFERENCES stores(id),
    product_id      BIGINT        NOT NULL REFERENCES products(id),
    sku             VARCHAR(60)   NOT NULL,
    barcode         VARCHAR(60),
    option1_value   VARCHAR(40),
    option2_value   VARCHAR(40),
    price_override  NUMERIC(14,2) CHECK (price_override > 0),
    physical_qty    INT           NOT NULL DEFAULT 0,
    reserved_qty    INT           NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    version         BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (store_id, sku),
    -- regras de estoque garantidas pelo próprio banco
    CONSTRAINT ck_variant_physical_non_negative CHECK (physical_qty >= 0),
    CONSTRAINT ck_variant_reserved_non_negative CHECK (reserved_qty >= 0),
    CONSTRAINT ck_variant_reserved_le_physical  CHECK (reserved_qty <= physical_qty)
);
CREATE INDEX ix_variants_product ON product_variants (product_id);

-- ---------------------------------------------------------------- clientes e fornecedores
CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT       NOT NULL REFERENCES stores(id),
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    phone       VARCHAR(30),
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_customers_store_email ON customers (store_id, lower(email));

CREATE TABLE customer_addresses (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    label        VARCHAR(40),
    street       VARCHAR(160) NOT NULL,
    number       VARCHAR(20)  NOT NULL,
    complement   VARCHAR(80),
    district     VARCHAR(80)  NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(2)   NOT NULL,
    zip          VARCHAR(9)   NOT NULL
);
CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id);

CREATE TABLE suppliers (
    id            BIGSERIAL PRIMARY KEY,
    store_id      BIGINT       NOT NULL REFERENCES stores(id),
    name          VARCHAR(160) NOT NULL,
    contact_name  VARCHAR(120),
    phone         VARCHAR(30),
    email         VARCHAR(160),
    address       VARCHAR(300),
    notes         VARCHAR(2000),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_suppliers_store ON suppliers (store_id);

-- ---------------------------------------------------------------- pedidos
CREATE TABLE orders (
    id                     BIGSERIAL PRIMARY KEY,
    store_id               BIGINT        NOT NULL REFERENCES stores(id),
    number                 INT           NOT NULL,
    tracking_token_hash    VARCHAR(64)   NOT NULL UNIQUE,
    customer_id            BIGINT        REFERENCES customers(id),
    customer_name          VARCHAR(120)  NOT NULL,
    customer_email         VARCHAR(160)  NOT NULL,
    customer_phone         VARCHAR(30),
    delivery_method        VARCHAR(20)   NOT NULL CHECK (delivery_method IN ('PICKUP','LOCAL_DELIVERY')),
    delivery_address       VARCHAR(400),
    channel                VARCHAR(10)   NOT NULL CHECK (channel IN ('ONLINE','MANUAL')),
    subtotal               NUMERIC(14,2) NOT NULL,
    discount_total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    delivery_fee           NUMERIC(14,2) NOT NULL DEFAULT 0,
    total                  NUMERIC(14,2) NOT NULL CHECK (total >= 0),
    status                 VARCHAR(20)   NOT NULL,
    payment_status         VARCHAR(30)   NOT NULL,
    fulfillment_status     VARCHAR(30)   NOT NULL,
    stock_committed        BOOLEAN       NOT NULL DEFAULT FALSE,
    requires_attention     BOOLEAN       NOT NULL DEFAULT FALSE,
    attention_reason       VARCHAR(300),
    notes                  VARCHAR(1000),
    reservation_expires_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    confirmed_at           TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (store_id, number)
);
CREATE INDEX ix_orders_store_created ON orders (store_id, created_at DESC);
CREATE INDEX ix_orders_store_status ON orders (store_id, status);
CREATE INDEX ix_orders_store_confirmed ON orders (store_id, confirmed_at);
CREATE INDEX ix_orders_customer ON orders (customer_id);

CREATE TABLE order_items (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES orders(id),
    product_id     BIGINT        NOT NULL REFERENCES products(id),
    variant_id     BIGINT        NOT NULL REFERENCES product_variants(id),
    -- fotografia dos dados no momento da venda
    product_name   VARCHAR(160)  NOT NULL,
    variant_name   VARCHAR(100),
    sku            VARCHAR(60)   NOT NULL,
    quantity       INT           NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(14,2) NOT NULL,
    unit_discount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total     NUMERIC(14,2) NOT NULL,
    unit_cost      NUMERIC(14,2) NOT NULL
);
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_variant ON order_items (variant_id);

CREATE TABLE order_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders(id),
    user_id     BIGINT      REFERENCES users(id),
    field       VARCHAR(20) NOT NULL,
    from_value  VARCHAR(40),
    to_value    VARCHAR(40),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_history_order ON order_history (order_id);

CREATE TABLE order_returns (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT       NOT NULL REFERENCES stores(id),
    order_id        BIGINT       NOT NULL REFERENCES orders(id),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('AWAITING_RECEIPT','RECEIVED')),
    reason          VARCHAR(500) NOT NULL,
    restocked       BOOLEAN      NOT NULL DEFAULT FALSE,
    condition_ok    BOOLEAN,
    inspection_note VARCHAR(500),
    created_by      BIGINT       REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    received_by     BIGINT       REFERENCES users(id),
    received_at     TIMESTAMPTZ
);
CREATE INDEX ix_order_returns_order ON order_returns (order_id);

CREATE TABLE order_return_items (
    id             BIGSERIAL PRIMARY KEY,
    return_id      BIGINT NOT NULL REFERENCES order_returns(id),
    order_item_id  BIGINT NOT NULL REFERENCES order_items(id),
    quantity       INT    NOT NULL CHECK (quantity > 0)
);

-- ---------------------------------------------------------------- estoque
CREATE TABLE stock_movements (
    id             BIGSERIAL PRIMARY KEY,
    store_id       BIGINT       NOT NULL REFERENCES stores(id),
    variant_id     BIGINT       NOT NULL REFERENCES product_variants(id),
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('ENTRY','EXIT','ADJUSTMENT','SALE','RETURN')),
    quantity       INT          NOT NULL,           -- positivo entra, negativo sai
    balance_after  INT          NOT NULL CHECK (balance_after >= 0),
    reason         VARCHAR(300) NOT NULL,
    order_id       BIGINT       REFERENCES orders(id),
    supplier_id    BIGINT       REFERENCES suppliers(id),
    user_id        BIGINT       REFERENCES users(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_mov_store_date ON stock_movements (store_id, created_at DESC);
CREATE INDEX ix_stock_mov_variant ON stock_movements (variant_id);

CREATE TABLE stock_reservations (
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT      NOT NULL REFERENCES stores(id),
    order_id     BIGINT      NOT NULL REFERENCES orders(id),
    variant_id   BIGINT      NOT NULL REFERENCES product_variants(id),
    quantity     INT         NOT NULL CHECK (quantity > 0),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','RELEASED','CONSUMED')),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX ix_reservations_active_exp ON stock_reservations (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_reservations_order ON stock_reservations (order_id);

-- ---------------------------------------------------------------- pagamentos
CREATE TABLE payment_integrations (
    id                  BIGSERIAL PRIMARY KEY,
    store_id            BIGINT      NOT NULL UNIQUE REFERENCES stores(id),
    provider            VARCHAR(20) NOT NULL CHECK (provider IN ('SIMULATOR','MERCADO_PAGO')),
    access_token_enc    VARCHAR(1000),
    public_key          VARCHAR(200),
    webhook_secret_enc  VARCHAR(1000),
    last_test_at        TIMESTAMPTZ,
    last_test_ok        BOOLEAN,
    updated_by          BIGINT      REFERENCES users(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id                    BIGSERIAL PRIMARY KEY,
    store_id              BIGINT        NOT NULL REFERENCES stores(id),
    order_id              BIGINT        NOT NULL REFERENCES orders(id),
    provider              VARCHAR(20)   NOT NULL,
    public_id             UUID          NOT NULL UNIQUE,     -- external_reference enviado ao provedor
    idempotency_key       UUID          NOT NULL UNIQUE,
    provider_payment_id   VARCHAR(80),
    provider_checkout_id  VARCHAR(120),
    checkout_url          VARCHAR(600),
    status                VARCHAR(30)   NOT NULL,
    amount                NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    currency              VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    refunded_amount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    fee_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,
    method                VARCHAR(40),
    simulated             BOOLEAN       NOT NULL DEFAULT FALSE,
    provider_updated_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,
    CHECK (refunded_amount <= amount)
);
CREATE UNIQUE INDEX ux_payments_provider_id ON payments (provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_status ON payments (status);

-- Notificações recebidas (deduplicação de webhooks repetidos)
CREATE TABLE webhook_events (
    id            BIGSERIAL PRIMARY KEY,
    provider      VARCHAR(20)  NOT NULL,
    event_key     VARCHAR(160) NOT NULL,
    resource_id   VARCHAR(80),
    store_id      BIGINT       REFERENCES stores(id),
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ,
    result        VARCHAR(300),
    UNIQUE (provider, event_key)
);

-- "Lado do provedor" do simulador local: imita um gateway externo, com estado próprio.
-- Usado quando a loja não configurou o Mercado Pago (pagamentos marcados como SIMULADOS).
CREATE TABLE simulator_charges (
    id                  UUID          PRIMARY KEY,
    store_id            BIGINT        NOT NULL REFERENCES stores(id),
    external_reference  UUID          NOT NULL,
    idempotency_key     UUID          NOT NULL UNIQUE,
    description         VARCHAR(200)  NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    refunded_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    return_url          VARCHAR(600)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_simulator_charges_ref ON simulator_charges (external_reference);

CREATE TABLE refunds (
    id                  BIGSERIAL PRIMARY KEY,
    payment_id          BIGINT        NOT NULL REFERENCES payments(id),
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    idempotency_key     UUID          NOT NULL UNIQUE,
    provider_refund_id  VARCHAR(80),
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('REQUESTED','COMPLETED','FAILED')),
    reason              VARCHAR(300),
    created_by          BIGINT        REFERENCES users(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);

-- ---------------------------------------------------------------- financeiro
CREATE TABLE financial_categories (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id),
    name        VARCHAR(80) NOT NULL,
    type        VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    system_key  VARCHAR(20),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (store_id, type, name)
);
CREATE UNIQUE INDEX ux_fin_cat_system ON financial_categories (store_id, system_key) WHERE system_key IS NOT NULL;

CREATE TABLE financial_entries (
    id                  BIGSERIAL PRIMARY KEY,
    store_id            BIGINT        NOT NULL REFERENCES stores(id),
    category_id         BIGINT        NOT NULL REFERENCES financial_categories(id),
    type                VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    description         VARCHAR(200)  NOT NULL,
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date            DATE          NOT NULL,
    paid_date           DATE,
    status              VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING','PAID','CANCELLED')),
    installment_group   UUID,
    installment_number  INT,
    installment_count   INT,
    counterparty        VARCHAR(160),
    supplier_id         BIGINT        REFERENCES suppliers(id),
    customer_id         BIGINT        REFERENCES customers(id),
    order_id            BIGINT        REFERENCES orders(id),
    payment_id          BIGINT        REFERENCES payments(id),
    source_type         VARCHAR(20)   NOT NULL CHECK (source_type IN ('MANUAL','ORDER_PAYMENT','PAYMENT_FEE','REFUND','REVERSAL')),
    source_ref          VARCHAR(120),
    reversal_of_id      BIGINT        REFERENCES financial_entries(id),
    reversed            BOOLEAN       NOT NULL DEFAULT FALSE,
    notes               VARCHAR(500),
    created_by          BIGINT        REFERENCES users(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_paid_date CHECK ((status = 'PAID') = (paid_date IS NOT NULL))
);
-- Garante que cada evento de integração (pagamento, taxa, reembolso) gere no máximo um lançamento
CREATE UNIQUE INDEX ux_fin_entries_source ON financial_entries (store_id, source_type, source_ref) WHERE source_ref IS NOT NULL;
CREATE INDEX ix_fin_entries_store_due ON financial_entries (store_id, due_date);
CREATE INDEX ix_fin_entries_store_paid ON financial_entries (store_id, paid_date);
