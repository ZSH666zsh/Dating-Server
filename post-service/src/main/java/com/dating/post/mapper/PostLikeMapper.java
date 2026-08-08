package com.dating.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.post.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞 Mapper
 * upsert 写 XML 里，不走 MyBatis-Plus 自动注入
 */
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    /** 点赞状态管理的数据库操作
     *
     * 特点用 PostgreSQL 的 UPSERT 机制（ON CONFLICT DO UPDATE）
     * 来实现原子性的状态切换。SQL 定义在 PostLikeMapper.xml
     *
     * UPSERT 不是 SQL 标准关键字，而是一个组合词：UPdate + inSERT = UPSERT。
     * 存在则更新，不存在则插入。
     *
     * 对帖子进行点赞/取消点赞操作，每次操作只需调用一次 upsert()，无需先查询再更新。
     *
     * 比 Java 先SELECT判断再INSERT 或 UPDATE的写法更：原子性 + 并发安全 + 少一次网络往返
     *
     * @return 影响行数（>0 表示插入或更新了行，0 表示幂等跳过）
     *
     *
     * -- 用户 1001 对帖子 2026 点赞
     * upsert(1001, 2026, 1)   -- status=1 表示已点赞
     *
     * -- 用户 1001 对帖子 2026 取消点赞
     * upsert(1001, 2026, 0)   -- status=0 表示未点赞
     */
    int upsert(@Param("userId") Long userId,
                @Param("postId") Long postId,
                @Param("status") int status);
}
