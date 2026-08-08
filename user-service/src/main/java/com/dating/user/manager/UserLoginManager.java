package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.UserLoginPhone;
import com.dating.user.mapper.UserLoginPhoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 手机号绑定管理 */
@Component
@RequiredArgsConstructor
public class UserLoginManager {

    private final UserLoginPhoneMapper userLoginPhoneMapper;

    /** 按手机号查绑定记录 */
    public UserLoginPhone getByPhone(String phoneE164) {
        return userLoginPhoneMapper.selectOne(
                new LambdaQueryWrapper<UserLoginPhone>()
                        .eq(UserLoginPhone::getPhoneE164, phoneE164)
        );
    }

    /** 查用户的所有手机号 */
    public List<UserLoginPhone> listByUserId(Long userId) {
        return userLoginPhoneMapper.selectList(
                new LambdaQueryWrapper<UserLoginPhone>()
                        .eq(UserLoginPhone::getUserId, userId)
        );
    }

    public void insert(UserLoginPhone record) {
        userLoginPhoneMapper.insert(record);
    }

    /** 手机号是否已被绑定 */
    public boolean isPhoneBound(String phoneE164) {
        return userLoginPhoneMapper.selectCount(
                new LambdaQueryWrapper<UserLoginPhone>()
                        .eq(UserLoginPhone::getPhoneE164, phoneE164)
        ) > 0;
    }
}
