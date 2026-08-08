-- ============================================================
-- post-service 初始化建表
-- 说明：
--   - 时间列全部 TIMESTAMPTZ（UTC）
--   - 业务主键 post_id / comment_id 对外暴露，内部 id 不对外
--   - 逻辑删除用 deleted SMALLINT DEFAULT 0
--   - 单表无 JOIN
-- ============================================================

-- ───────────────────────────────────────
-- 帖子主表
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS posts (
    id          BIGSERIAL       PRIMARY KEY,                  -- 内部物理主键，不对外
    post_id     BIGINT          UNIQUE NOT NULL,               -- 雪花 ID，业务主键
    user_id     BIGINT          NOT NULL,                      -- 发帖人
    content     VARCHAR(1024)   NOT NULL,                      -- 文本
    status      SMALLINT        DEFAULT 1,                     -- 1=正常 0=删除 2=审核中
    deleted     SMALLINT        DEFAULT 0,                     -- 逻辑删除
    created_at  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_posts_user_created
    ON posts (user_id, created_at DESC);

-- ───────────────────────────────────────
-- 帖子图片
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_images (
    post_id     BIGINT          NOT NULL,
    sort_order  SMALLINT        NOT NULL,                      -- 0..8
    image_key   VARCHAR(128)    NOT NULL,                      -- 对象存储 key，不存 URL
    created_at  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, sort_order)
);

-- ───────────────────────────────────────
-- 计数底座（已刷盘部分）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_stats (
    post_id         BIGINT      PRIMARY KEY,
    like_count      INTEGER     DEFAULT 0,
    comment_count   INTEGER     DEFAULT 0,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_stats_likes
    ON post_stats (like_count DESC);

-- ───────────────────────────────────────
-- 点赞幂等记录
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_likes (
    user_id     BIGINT          NOT NULL,
    post_id     BIGINT          NOT NULL,
    status      SMALLINT        DEFAULT 1,                     -- 1=已赞 0=已取消
    created_at  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id)                             -- 联合主键防重复点赞
);

CREATE INDEX IF NOT EXISTS idx_post_likes_post
    ON post_likes (post_id) WHERE status = 1;                  -- partial index

-- ───────────────────────────────────────
-- 评论表（预留楼中楼字段）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_comments (
    id              BIGSERIAL       PRIMARY KEY,
    comment_id      BIGINT          UNIQUE NOT NULL,            -- 业务主键
    post_id         BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    content         VARCHAR(512)    NOT NULL,
    root_id         BIGINT          DEFAULT 0,                  -- 根评论 ID
    parent_id       BIGINT          DEFAULT 0,                  -- 直接父评论 ID
    reply_to_user_id BIGINT         DEFAULT 0,                  -- 被回复人
    status          SMALLINT        DEFAULT 1,
    deleted         SMALLINT        DEFAULT 0,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comments_post
    ON post_comments (post_id, root_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_comments_root
    ON post_comments (root_id, created_at ASC);

-- ───────────────────────────────────────
-- ShedLock 锁表（多实例 Job 互斥）
-- ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)     PRIMARY KEY,
    lock_until  TIMESTAMPTZ     NOT NULL,
    locked_at   TIMESTAMPTZ     NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL
);
