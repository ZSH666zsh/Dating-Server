package com.dating.gateway.service;

import com.dating.gateway.client.UserProfileClient;
import com.dating.zhaoshihang.proto.user.GetProfileResponse;
import com.dating.zhaoshihang.proto.user.UpsertOnboardingResponse;
import com.dating.zhaoshihang.proto.user.UserProfileProto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 用户资料 BFF 服务。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileClient profileClient;

    /**
     * 获取用户资料（直接透传 user-service 响应）。
     */
    public GetProfileResponse getProfile(Long userId) {
        return profileClient.getProfile(userId);
    }

    /**
     * 更新资料。
     */
    public void updateProfile(Long userId, String nickname, Integer gender,
                               String birthday, Long cityId) {
        profileClient.updateProfile(userId, nickname, gender, birthday, cityId, null, null);
    }

    /**
     * Onboarding 资料完善。
     */
    public UpsertOnboardingResponse onboarding(Long userId, String nickname, int gender,
                                                String birthday, Long cityId) {
        return profileClient.upsertOnboarding(userId, nickname, gender, birthday, cityId);
    }
}
