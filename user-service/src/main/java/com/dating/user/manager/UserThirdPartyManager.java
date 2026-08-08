package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.UserThirdPartyRegistration;
import com.dating.user.mapper.UserThirdPartyRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 第三方账号绑定管理 */
@Component
@RequiredArgsConstructor
public class UserThirdPartyManager {

    private final UserThirdPartyRegistrationMapper mapper;

    /** 按第三方 ID + 平台查绑定记录 */
    public UserThirdPartyRegistration getByThirdPartyId(String thirdPartyUserId, int platform) {
        return mapper.selectOne(
                new LambdaQueryWrapper<UserThirdPartyRegistration>()
                        .eq(UserThirdPartyRegistration::getThirdPartyLoginUserId, thirdPartyUserId)
                        .eq(UserThirdPartyRegistration::getPlatform, platform)
                        .eq(UserThirdPartyRegistration::getDeleted, 0)
        );
    }

    /** 查用户的所有第三方绑定 */
    public java.util.List<UserThirdPartyRegistration> listByUserId(Long userId) {
        return mapper.selectList(
                new LambdaQueryWrapper<UserThirdPartyRegistration>()
                        .eq(UserThirdPartyRegistration::getUserId, userId)
                        .eq(UserThirdPartyRegistration::getDeleted, 0)
        );
    }

    public void insert(UserThirdPartyRegistration record) {
        mapper.insert(record);
    }
}
