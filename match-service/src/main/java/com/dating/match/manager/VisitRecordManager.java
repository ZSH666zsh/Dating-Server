package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.match.entity.VisitRecord;
import com.dating.match.mapper.VisitRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Visit 记录管理器。
 */
@Component
@RequiredArgsConstructor
public class VisitRecordManager {

    private final VisitRecordMapper visitRecordMapper;

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
