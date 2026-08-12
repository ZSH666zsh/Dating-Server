package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.entity.VisitRecord;
import com.dating.match.mapper.VisitRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Visit 记录管理器。
 */
@Component
@RequiredArgsConstructor
public class VisitRecordManager {

    private final VisitRecordMapper visitRecordMapper;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * UPSERT visit 记录，累加 visit_count。
     */
    public void upsert(VisitRecord record) {
        var existing = visitRecordMapper.selectOne(
                new LambdaQueryWrapper<VisitRecord>()
                        .eq(VisitRecord::getFromUserId, record.getFromUserId())
                        .eq(VisitRecord::getToUserId, record.getToUserId()));
        if (existing != null) {
            existing.setVisitCount(existing.getVisitCount() + 1);
            existing.setVisitedAt(record.getVisitedAt());
            existing.setSource(record.getSource());
            visitRecordMapper.updateById(existing);
        } else {
            record.setVisitCount(1);
            visitRecordMapper.insert(record);
        }
    }

    /**
     * 批量 UPSERT（DH 互动任务批量执行用）：一条 SQL 完成 N 条。
     */
    public int batchUpsert(List<VisitRecord> records) {
        if (records == null || records.isEmpty()) return 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (VisitRecord r : records) {
            r.setId(idGenerator.nextId());
            r.setVisitCount(1); // 新插入为 1，冲突时由 SQL 累加
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            r.setDeleted(0);
        }
        return visitRecordMapper.batchUpsert(records);
    }

    /**
     * 查询收到 Visit 列表（分页）。
     */
    public List<VisitRecord> listVisitsOfMe(Long userId, int pageSize, Long cursor) {
        LambdaQueryWrapper<VisitRecord> wrapper = new LambdaQueryWrapper<VisitRecord>()
                .eq(VisitRecord::getToUserId, userId)
                .orderByDesc(VisitRecord::getVisitedAt);
        if (cursor != null && cursor > 0) {
            wrapper.lt(VisitRecord::getId, cursor);
        }
        return visitRecordMapper.selectList(wrapper.last("LIMIT " + pageSize));
    }
}
