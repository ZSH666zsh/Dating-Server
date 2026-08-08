package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.UserInterest;
import com.dating.user.mapper.UserInterestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 用户兴趣标签管理 */
@Component
@RequiredArgsConstructor
public class UserInterestManager {

    private final UserInterestMapper mapper;

    public void insert(UserInterest interest) {
        mapper.insert(interest);
    }

    /** 查用户的所有兴趣标签 */
    public List<UserInterest> listByUserId(Long userId) {
        return mapper.selectList(
                new LambdaQueryWrapper<UserInterest>()
                        .eq(UserInterest::getUserId, userId)
        );
    }

    /** 批量查多个用户的兴趣 */
    public List<UserInterest> listByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        return mapper.selectList(
                new LambdaQueryWrapper<UserInterest>()
                        .in(UserInterest::getUserId, userIds)
        );
    }

    /** 全量替换用户的兴趣标签（事务内 DELETE + INSERT） */
    @Transactional(rollbackFor = Exception.class)
    public void replaceInterests(Long userId, List<UserInterest> newInterests) {
        // 删旧标签
        mapper.delete(new LambdaQueryWrapper<UserInterest>()
                .eq(UserInterest::getUserId, userId));

        // 插新标签
        for (UserInterest interest : newInterests) {
            interest.setUserId(userId);
            mapper.insert(interest);
        }
    }
}
