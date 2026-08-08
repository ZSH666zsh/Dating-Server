-- ============================================================
-- user-service 初始化建表
-- 遵循设计规范：
--   - 时间列全部 TIMESTAMPTZ（UTC）
--   - 业务主键 user_id 对外暴露
--   - 逻辑删除用 deleted SMALLINT DEFAULT 0
--   - 单表无 JOIN
--   - Flyway history 表: flyway_history_user
-- ============================================================

-- ───────────────────────────────────────
-- 用户主档案表
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_info (
    id              BIGSERIAL       PRIMARY KEY,                  -- 内部物理主键
    user_id         BIGINT          UNIQUE NOT NULL,               -- 业务主键（雪花 ID）
    nickname        VARCHAR(64)     DEFAULT '',                    -- 昵称
    age             SMALLINT        DEFAULT 0,                     -- 年龄
    gender          SMALLINT        DEFAULT 0,                     -- 0=未设置 1=男 2=女
    birthday        DATE,                                          -- 生日
    city_id         BIGINT          DEFAULT 0,                     -- 城市 ID（引用 geo_city）
    lat             NUMERIC(10,7)   DEFAULT 0,                     -- 纬度
    lng             NUMERIC(10,7)   DEFAULT 0,                     -- 经度
    beauty_score    SMALLINT        DEFAULT 0,                     -- 颜值分（0-100）
    race            VARCHAR(32)     DEFAULT '',                    -- 人种
    custom_avatar   TEXT            DEFAULT '{}',                  -- JSONB：头像 key 集合 {originalKey,minKey,midKey}
    regulation_status SMALLINT     DEFAULT 0,                      -- 0=正常 1=审核 2=封禁 3=暂停
    pending         BOOLEAN         DEFAULT TRUE,                  -- true=待 onboarding
    user_type       SMALLINT        DEFAULT 1,                     -- 1=BH(真人) 2=DH(数字人)
    last_open_at    TIMESTAMPTZ,                                   -- 最后活跃时间
    deleted         SMALLINT        DEFAULT 0,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 召回主索引（user_type, gender, city_id, age, beauty_score）
CREATE INDEX IF NOT EXISTS idx_user_info_recall
    ON user_info (user_type, gender, city_id, age, beauty_score)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_user_info_city
    ON user_info (city_id)
    WHERE deleted = 0 AND user_type = 1;

-- ───────────────────────────────────────
-- 手机号登录绑定表
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_login_phone (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    phone_e164      VARCHAR(20)     NOT NULL,                      -- 带国家码，如 +8613800138000
    app_name        VARCHAR(32)     DEFAULT 'vibe',                -- 应用标识
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (phone_e164, app_name)
);

CREATE INDEX IF NOT EXISTS idx_login_phone_user
    ON user_login_phone (user_id);

-- ───────────────────────────────────────
-- 第三方账号绑定表
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_third_party_registration (
    id                          BIGSERIAL       PRIMARY KEY,
    user_id                     BIGINT          NOT NULL,
    third_party_login_user_id   VARCHAR(128)    NOT NULL,           -- 第三方平台用户 ID
    platform                    SMALLINT        NOT NULL,           -- 1=Google 2=Facebook 3=Apple
    deleted                     SMALLINT        DEFAULT 0,
    created_at                  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (third_party_login_user_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_third_party_user
    ON user_third_party_registration (user_id);

-- ───────────────────────────────────────
-- 设备绑定表
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_device_registration (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    device_id       VARCHAR(128)    NOT NULL,                       -- 设备唯一标识
    platform        SMALLINT        NOT NULL,                       -- 1=iOS 2=Android 3=Web
    deleted         SMALLINT        DEFAULT 0,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_device_user
    ON user_device_registration (user_id, deleted);

-- ───────────────────────────────────────
-- 用户兴趣标签表（1:N）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_interest (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    tab_key         VARCHAR(32)     NOT NULL,                       -- 分类 key，如 sports/music
    tag_key         VARCHAR(32)     NOT NULL,                       -- 标签 key，如 basketball/guitar
    pic_key         VARCHAR(128)    DEFAULT '',                     -- 关联图片 key
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_interest_user
    ON user_interest (user_id);

-- ───────────────────────────────────────
-- 城市字典表（SimpleMaps US ~28k 行）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS geo_city (
    id              BIGSERIAL       PRIMARY KEY,
    city            VARCHAR(64)     NOT NULL,
    state_code      VARCHAR(8)      NOT NULL,
    state_name      VARCHAR(64)     NOT NULL,
    lat             NUMERIC(10,7)   NOT NULL,
    lng             NUMERIC(10,7)   NOT NULL,
    population      INTEGER         DEFAULT 0,
    source_id       INTEGER         DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_geo_city_state
    ON geo_city (state_code, city);

-- ───────────────────────────────────────
-- ShedLock 锁表（多实例 Job 互斥）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)     PRIMARY KEY,
    lock_until  TIMESTAMPTZ     NOT NULL,
    locked_at   TIMESTAMPTZ     NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL
);
