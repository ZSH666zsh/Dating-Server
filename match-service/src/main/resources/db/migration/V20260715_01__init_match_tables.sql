-- ============================================================
-- match-service 初始化建表
-- 对应 match-service-prd-tech.md §7.2 数据库 schema
-- 时间统一 TIMESTAMPTZ、deleted 逻辑删除
-- ============================================================

-- 1) 划卡历史（高写入，按 user_id 检索）
CREATE TABLE IF NOT EXISTS user_swipe_history (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    target_user_id      BIGINT NOT NULL,
    target_user_type    SMALLINT NOT NULL,         -- 1=BH, 2=DH
    direction           SMALLINT NOT NULL,         -- 1=LEFT, 2=RIGHT, 3=SUPER_HI
    swiped_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted             SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, target_user_id)
);
CREATE INDEX IF NOT EXISTS idx_swipe_user_time ON user_swipe_history (user_id, swiped_at DESC);
CREATE INDEX IF NOT EXISTS idx_swipe_target_dir ON user_swipe_history (target_user_id, direction) WHERE deleted = 0;

-- 2) 匹配关系
CREATE TABLE IF NOT EXISTS match (
    id              BIGINT PRIMARY KEY,
    user_id_low     BIGINT NOT NULL,               -- min(uid1, uid2)
    user_id_high    BIGINT NOT NULL,               -- max(uid1, uid2)
    matched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    source          VARCHAR(30) NOT NULL,           -- SWIPE_MATCH / SWIPE_SUPER_HI
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (user_id_low, user_id_high)
);
CREATE INDEX IF NOT EXISTS idx_match_low_time  ON match (user_id_low,  matched_at DESC);
CREATE INDEX IF NOT EXISTS idx_match_high_time ON match (user_id_high, matched_at DESC);

-- 3) Match 副作用 retry 表
CREATE TABLE IF NOT EXISTS match_outbox (
    id            BIGINT PRIMARY KEY,
    match_id      BIGINT NOT NULL,
    action        VARCHAR(40) NOT NULL,              -- ENSURE_CONVERSATION / SYSTEM_MSG / DH_OPENING
    payload_json  JSONB NOT NULL DEFAULT '{}',
    attempts      INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / DONE / DEAD
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       SMALLINT NOT NULL DEFAULT 0
);

-- 4) Like 记录（谁单向喜欢了我）
CREATE TABLE IF NOT EXISTS like_record (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT NOT NULL,
    to_user_id      BIGINT NOT NULL,
    from_user_type  SMALLINT NOT NULL,               -- 1=BH 2=DH
    source          SMALLINT NOT NULL,               -- 1=SWIPE_RIGHT 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE
    like_content    VARCHAR(200),
    liked_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (from_user_id, to_user_id)
);
CREATE INDEX IF NOT EXISTS idx_like_to_user_time ON like_record (to_user_id, liked_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_like_to_user_type ON like_record (to_user_id, from_user_type, liked_at DESC) WHERE deleted = 0;

-- 5) Visit 记录（谁访问了我）
CREATE TABLE IF NOT EXISTS visit_record (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT NOT NULL,
    to_user_id      BIGINT NOT NULL,
    from_user_type  SMALLINT NOT NULL,
    source          SMALLINT NOT NULL,               -- 1=PROFILE_VIEW 2=DH_PLAN_ONLINE 3=DH_PLAN_OFFLINE
    visit_count     INT NOT NULL DEFAULT 1,
    visited_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (from_user_id, to_user_id)
);
CREATE INDEX IF NOT EXISTS idx_visit_to_user_time ON visit_record (to_user_id, visited_at DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_visit_to_user_type ON visit_record (to_user_id, from_user_type, visited_at DESC) WHERE deleted = 0;

-- 6) DH 模拟互动任务（短生命周期）
CREATE TABLE IF NOT EXISTS dh_interaction_task (
    id              BIGINT PRIMARY KEY,
    from_user_id    BIGINT NOT NULL,                 -- DH user_id
    to_user_id      BIGINT NOT NULL,                 -- 真人 user_id
    action          SMALLINT NOT NULL,               -- 1=LIKE 2=VISIT
    scene           SMALLINT NOT NULL,               -- 1=ONLINE 2=OFFLINE
    execute_time    TIMESTAMPTZ NOT NULL,
    like_content    VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dh_task_execute_time ON dh_interaction_task (execute_time);
CREATE INDEX IF NOT EXISTS idx_dh_task_to_user_scene ON dh_interaction_task (to_user_id, scene);
