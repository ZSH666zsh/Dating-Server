package com.dating.match.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.match.entity.DhInteractionTask;
import com.dating.match.mapper.DhInteractionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DH 互动任务管理器。
 */
@Component
@RequiredArgsConstructor
public class DhInteractionTaskManager {

    private final DhInteractionTaskMapper taskMapper;

    /**
     * 批量插入任务。
     */
    public void batchInsert(List<DhInteractionTask> tasks) {
        tasks.forEach(taskMapper::insert);
    }

    /**
     * 扫描到期任务。
     */
    public List<DhInteractionTask> scanDueTasks(int limit) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<DhInteractionTask>()
                        .le(DhInteractionTask::getExecuteTime, OffsetDateTime.now())
                        .orderByAsc(DhInteractionTask::getExecuteTime)
                        .last("LIMIT " + limit));
    }

    /**
     * 删除已执行任务。
     */
    public void deleteById(Long id) {
        taskMapper.deleteById(id);
    }

    /**
     * 批量删除已执行任务（配合批量执行，减少一次调度内的删除次数）。
     */
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        taskMapper.delete(
                new LambdaQueryWrapper<DhInteractionTask>().in(DhInteractionTask::getId, ids));
    }

    /**
     * 检查用户是否有未执行的 ONLINE 任务。
     */
    public boolean hasPendingOnlineTask(Long toUserId) {
        Long count = taskMapper.selectCount(
                new LambdaQueryWrapper<DhInteractionTask>()
                        .eq(DhInteractionTask::getToUserId, toUserId)
                        .eq(DhInteractionTask::getScene, 1));
        return count != null && count > 0;
    }
}
