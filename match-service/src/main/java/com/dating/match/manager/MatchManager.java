package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dating.match.entity.MatchEntity;
import com.dating.match.mapper.MatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 匹配关系管理器。
 */
@Component
@RequiredArgsConstructor
public class MatchManager {

    private final MatchMapper matchMapper;

    /**
     * 创建 match（防重复）。
     * @return 1=成功插入, 0=已存在
     */
    public int createMatch(Long id, Long low, Long high, String source) {
        return matchMapper.insertIgnoreConflict(id, Math.min(low, high), Math.max(low, high), source);
    }

    /**
     * 查看已有的匹配。
     */
    public MatchEntity getByPair(Long uidA, Long uidB) {
        long low = Math.min(uidA, uidB);
        long high = Math.max(uidA, uidB);
        return matchMapper.selectOne(
                new LambdaQueryWrapper<MatchEntity>()
                        .eq(MatchEntity::getUserIdLow, low)
                        .eq(MatchEntity::getUserIdHigh, high));
    }

    /**
     * 获取用户的匹配列表（分页）。
     */
    public List<MatchEntity> listMatches(Long userId, int pageSize, Long cursor) {
        Page<MatchEntity> page = new Page<>(0, pageSize);
        LambdaQueryWrapper<MatchEntity> wrapper = new LambdaQueryWrapper<MatchEntity>()
                .and(w -> w.eq(MatchEntity::getUserIdLow, userId)
                        .or(w2 -> w2.eq(MatchEntity::getUserIdHigh, userId)))
                .orderByDesc(MatchEntity::getMatchedAt);
        if (cursor != null && cursor > 0) {
            wrapper.lt(MatchEntity::getId, cursor);
        }
        return matchMapper.selectPage(page, wrapper).getRecords();
    }

    /**
     * 获取双方匹配关系。
     */
    public MatchEntity getMatchBetween(Long uidA, Long uidB) {
        long low = Math.min(uidA, uidB);
        long high = Math.max(uidA, uidB);
        return matchMapper.selectOne(
                new LambdaQueryWrapper<MatchEntity>()
                        .eq(MatchEntity::getUserIdLow, low)
                        .eq(MatchEntity::getUserIdHigh, high));
    }
}
