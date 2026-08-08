package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.UserDeviceRegistration;
import com.dating.user.mapper.UserDeviceRegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 设备绑定管理 */
@Component
@RequiredArgsConstructor
public class UserDeviceManager {

    private final UserDeviceRegistrationMapper mapper;

    /** 按设备 ID + 平台查绑定 */
    public UserDeviceRegistration getByDeviceId(String deviceId, int platform) {
        return mapper.selectOne(
                new LambdaQueryWrapper<UserDeviceRegistration>()
                        .eq(UserDeviceRegistration::getDeviceId, deviceId)
                        .eq(UserDeviceRegistration::getPlatform, platform)
                        .eq(UserDeviceRegistration::getDeleted, 0)
        );
    }

    public void insert(UserDeviceRegistration record) {
        mapper.insert(record);
    }
}
