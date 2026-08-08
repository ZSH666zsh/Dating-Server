package com.dating.match.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.match.entity.MatchEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 匹配关系 Mapper。
 */
public interface MatchMapper extends BaseMapper<MatchEntity> {

    /**
     * 防重复 INSERT（ON CONFLICT DO NOTHING）。
     * 对应 match-service-prd-tech.md §5.3 防御性兜底。
     *
     * ON CONFLICT DO NOTHING
     * 如果匹配已存在，不报错，返回影响行数 0。调用方拿到 0 后查已存在的 match 返回。
     */
    @Insert("INSERT INTO match (id, user_id_low, user_id_high, matched_at, source, created_at, updated_at) " +
            "VALUES (#{id}, #{low}, #{high}, now(), #{source}, now(), now()) " +
            "ON CONFLICT (user_id_low, user_id_high) DO NOTHING")
    int insertIgnoreConflict(@Param("id") Long id, @Param("low") Long low,
                             @Param("high") Long high, @Param("source") String source);
}
