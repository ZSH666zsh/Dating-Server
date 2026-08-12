package com.dating.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.match.entity.LikeRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Like 记录 Mapper。
 */
public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    /**
     * 批量 UPSERT：按 (from_user_id, to_user_id) 冲突更新，一条 SQL 完成 N 条，
     * 替代原来"逐条先查再插"的 N+1。幂等由唯一约束兜底，重复执行安全。
     */
    @Insert("""
            <script>
            INSERT INTO like_record
              (id, from_user_id, to_user_id, from_user_type, source, like_content, liked_at, created_at, updated_at, deleted)
            VALUES
            <foreach collection="records" item="r" separator=",">
              (#{r.id}, #{r.fromUserId}, #{r.toUserId}, #{r.fromUserType}, #{r.source}, #{r.likeContent}, #{r.likedAt}, now(), now(), 0)
            </foreach>
            ON CONFLICT (from_user_id, to_user_id) DO UPDATE SET
              source = EXCLUDED.source,
              liked_at = EXCLUDED.liked_at,
              like_content = EXCLUDED.like_content,
              updated_at = now()
            </script>
            """)
    int batchUpsert(@Param("records") List<LikeRecord> records);
}
