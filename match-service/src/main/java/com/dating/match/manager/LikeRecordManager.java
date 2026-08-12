package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.entity.LikeRecord;
import com.dating.match.mapper.LikeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Like 记录管理器。
 */
@Component
@RequiredArgsConstructor
public class LikeRecordManager {

    private final LikeRecordMapper likeRecordMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * UPSERT like 记录（ON CONFLICT 更新 liked_at）。行内使用 INSERT ... ON CONFLICT 由 XML 实现。
     */
    public void upsert(LikeRecord record) {
        // 先查再插：MyBatis-Plus 没有原生 UPSERT
        var existing = likeRecordMapper.selectOne(
                new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getFromUserId, record.getFromUserId())
                        .eq(LikeRecord::getToUserId, record.getToUserId()));
        if (existing != null) {
            existing.setSource(record.getSource());
            existing.setLikedAt(record.getLikedAt());
            existing.setLikeContent(record.getLikeContent());
            likeRecordMapper.updateById(existing);
        } else {
            likeRecordMapper.insert(record);
        }
    }

    /**
     * 批量 UPSERT（DH 互动任务批量执行用）：一条 SQL 完成 N 条，N+1 → 1。
     */
    public int batchUpsert(List<LikeRecord> records) {
        if (records == null || records.isEmpty()) return 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (LikeRecord r : records) {
            r.setId(idGenerator.nextId());
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            r.setDeleted(0);
        }
        return likeRecordMapper.batchUpsert(records);
    }

    /**
     * 删除双向 like_record（match 触发时清理）。
     */
    public void deleteBoth(Long uidA, Long uidB) {
        likeRecordMapper.delete(
                new LambdaQueryWrapper<LikeRecord>()
                        .and(w -> w.eq(LikeRecord::getFromUserId, uidA)
                                .eq(LikeRecord::getToUserId, uidB))
                        .or(w -> w.eq(LikeRecord::getFromUserId, uidB)
                                .eq(LikeRecord::getToUserId, uidA)));
    }

    /**
     * 查询收到 Like 列表（分页）。
     */
    public List<LikeRecord> listLikesOfMe(Long userId, int pageSize, Long cursor) {
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getToUserId, userId)
                .orderByDesc(LikeRecord::getLikedAt);
        if (cursor != null && cursor > 0) {
            wrapper.lt(LikeRecord::getId, cursor);
        }
        return likeRecordMapper.selectList(wrapper.last("LIMIT " + pageSize));
    }
}
