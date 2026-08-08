-- ============================================================
-- payment-service 初始化建表
-- 对应 payment-service-design.md §3
-- ============================================================

-- 金币账户（每用户一行，乐观锁 version）
CREATE TABLE IF NOT EXISTS coin_accounts (
    user_id         BIGINT PRIMARY KEY,
    balance         BIGINT NOT NULL DEFAULT 0,
    paid_balance    BIGINT NOT NULL DEFAULT 0,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 金币流水（append-only）
CREATE TABLE IF NOT EXISTS coin_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    type                VARCHAR(20) NOT NULL,
    amount              BIGINT NOT NULL DEFAULT 0,
    paid_amount         BIGINT NOT NULL DEFAULT 0,
    balance_after        BIGINT NOT NULL DEFAULT 0,
    paid_balance_after   BIGINT NOT NULL DEFAULT 0,
    reason              VARCHAR(255),
    extra               JSONB,
    idempotency_key     VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ledger_user ON coin_ledger (user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_idempotent ON coin_ledger (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 支付订单
CREATE TABLE IF NOT EXISTS payment_orders (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    order_id            VARCHAR(64) NOT NULL UNIQUE,
    product_id          VARCHAR(128),
    amount              NUMERIC(16,4) NOT NULL DEFAULT 0,
    currency            VARCHAR(10) DEFAULT 'USD',
    payment_channel     VARCHAR(32),
    status              VARCHAR(20) NOT NULL DEFAULT 'INIT',
    refund_status       VARCHAR(20) NOT NULL DEFAULT 'NONE',
    refunded_amount     NUMERIC(16,4) NOT NULL DEFAULT 0,
    ext_transaction_id  VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 用户订阅
CREATE TABLE IF NOT EXISTS user_subscription (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    tier            SMALLINT NOT NULL DEFAULT 1,
    expires_at      TIMESTAMPTZ,
    source          VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sub_user ON user_subscription (user_id) WHERE deleted = 0;
