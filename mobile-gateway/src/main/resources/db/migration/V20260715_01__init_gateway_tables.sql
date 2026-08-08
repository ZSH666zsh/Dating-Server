-- ============================================================
-- mobile-gateway 初始化建表（仅鉴权域）
-- 对应 mobile-gateway-design.md §5.1
-- ============================================================

-- 设备指纹登记；登录时 upsert，刷新时 touch lastSeen
CREATE TABLE IF NOT EXISTS auth_device (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    platform        SMALLINT DEFAULT 0,          -- 1=iOS 2=Android 3=Web
    push_token      VARCHAR(512),
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, device_id)
);

-- Refresh token 表；hash 存 token，轮换链表防止重放
CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,         -- SHA-256 hex
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,                  -- 轮换后标记时间
    rotated_to_id   BIGINT,                       -- 下一代 id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON auth_refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_device ON auth_refresh_token (user_id, device_id);
