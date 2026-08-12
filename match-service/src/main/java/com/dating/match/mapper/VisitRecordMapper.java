package com.dating.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.match.entity.VisitRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Visit 记录 Mapper。
 */
public interface VisitRecordMapper extends BaseMapper<VisitRecord> {

    /**
     * 批量 UPSERT：按 (from_user_id, to_user_id) 冲突则 visit_count+1（同人多次访问累加）。
     */
    @Insert("""
            <script>
            INSERT INTO visit_record
              (id, from_user_id, to_user_id, from_user_type, source, visit_count, visited_at, created_at, updated_at, deleted)
            VALUES
            <foreach collection="records" item="r" separator=",">
              (#{r.id}, #{r.fromUserId}, #{r.toUserId}, #{r.fromUserType}, #{r.source}, 1, #{r.visitedAt}, now(), now(), 0)
            </foreach>
            ON CONFLICT (from_user_id, to_user_id) DO UPDATE SET
              visit_count = visit_record.visit_count + 1,
              source = EXCLUDED.source,
              visited_at = EXCLUDED.visited_at,
              updated_at = now()
            </script>
            """)
    int batchUpsert(@Param("records") List<VisitRecord> records);
}
