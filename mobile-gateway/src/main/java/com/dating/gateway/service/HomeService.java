package com.dating.gateway.service;

import com.dating.gateway.client.UserProfileClient;
import com.dating.zhaoshihang.proto.user.GetProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 首页 BFF 聚合服务。
 * 并发调多个下游服务组装首页卡片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserProfileClient profileClient;

    // TODO: 后续接入 relation-service(关注关系) 和 im-service(在线状态)

    /**
     * 获取首页用户卡片（BFF 聚合）。
     */
    public HomeCardVO getHomeCard(Long viewerId, Long targetId) {
        // 并发调 user-service 拿资料
        CompletableFuture<GetProfileResponse> profileFuture =
                CompletableFuture.supplyAsync(() -> profileClient.getProfile(targetId));

        // 等待全部完成
        CompletableFuture.allOf(profileFuture).join();

        var profileResp = profileFuture.join();
        var profile = profileResp.getProfile();

        return new HomeCardVO(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAge(),
                profile.getGender(),
                profile.getCustomAvatar(),
                profile.getCityName()
        );
    }

    public record HomeCardVO(Long userId, String nickname, int age, int gender,
                             String avatarKey, String cityName) {}
}
