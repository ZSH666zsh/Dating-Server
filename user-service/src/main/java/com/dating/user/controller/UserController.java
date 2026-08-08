package com.dating.user.controller;

import com.dating.user.service.UserIdentityService;
import com.dating.user.service.UserProfileService;
import com.dating.user.service.RecommendationService;
import com.dating.user.vo.Result;
import com.dating.user.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 用户 REST 接口（本机调试用）。
 * 生产侧由 mobile-gateway 走 gRPC，此处 1:1 映射 gRPC 接口。
 *
 * 所有接口从请求头 X-User-Id 取当前用户（模拟 gateway JWT 注入）。
 */
@Slf4j
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserIdentityService userIdentityService;
    private final UserProfileService userProfileService;
    private final RecommendationService recommendationService;

    // ──────────────────────────────────────────────
    //  身份认证
    // ──────────────────────────────────────────────

    /**
     * 手机号登录/注册
     * POST /v1/users/login/phone
     */
    @PostMapping("/login/phone")
    public Result<UserIdentityService.RegisterResult> loginByPhone(@RequestBody LoginPhoneReq req) {
        var result = userIdentityService.resolveOrCreateByPhone(req.phoneE164());
        return Result.ok(result);
    }

    /**
     * 第三方登录/注册
     * POST /v1/users/login/third-party
     */
    @PostMapping("/login/third-party")
    public Result<UserIdentityService.RegisterResult> loginByThirdParty(@RequestBody ThirdPartyLoginReq req) {
        var result = userIdentityService.resolveOrCreateByThirdParty(req.thirdPartyUserId(), req.platform());
        return Result.ok(result);
    }

    /**
     * 设备匿名登录/注册
     * POST /v1/users/login/device
     */
    @PostMapping("/login/device")
    public Result<UserIdentityService.RegisterResult> loginByDevice(@RequestBody DeviceLoginReq req) {
        var result = userIdentityService.resolveOrCreateByDevice(req.deviceId(), req.platform());
        return Result.ok(result);
    }

    /**
     * 封禁检查
     * GET /v1/users/{userId}/ban-check
     */
    @GetMapping("/{userId}/ban-check")
    public Result<Map<String, Boolean>> checkBan(@PathVariable Long userId) {
        boolean banned = userIdentityService.checkBan(userId);
        return Result.ok(Map.of("banned", banned));
    }

    // ──────────────────────────────────────────────
    //  用户档案
    // ──────────────────────────────────────────────

    /**
     * 获取用户档案
     * GET /v1/users/{userId}/profile
     * X-User-Id 为可选（post-service 跨服务调用时不携带）
     */
    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getProfile(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        UserProfileVO profile = userProfileService.getProfile(userId);
        return Result.ok(profile);
    }

    /**
     * 批量获取用户档案
     * POST /v1/users/profiles/batch
     */
    @PostMapping("/profiles/batch")
    public Result<List<UserProfileVO>> batchGetProfile(@RequestBody BatchProfileReq req) {
        var profiles = userProfileService.batchGetProfile(req.userIds());
        return Result.ok(profiles);
    }

    /**
     * 更新用户档案
     * PATCH /v1/users/{userId}/profile
     */
    @PatchMapping("/{userId}/profile")
    public Result<UserProfileVO> updateProfile(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @RequestBody UpdateProfileReq req) {
        var profile = userProfileService.updateProfile(
                userId, req.nickname(), req.gender(), req.birthday(),
                req.cityId(), req.race(), req.beautyScore());
        return Result.ok(profile);
    }

    /**
     * Onboarding 信息完善
     * POST /v1/users/{userId}/onboarding
     */
    @PostMapping("/{userId}/onboarding")
    public Result<UserProfileVO> onboarding(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @RequestBody OnboardingReq req) {
        return Result.ok(userProfileService.upsertOnboarding(
                userId, req.nickname(), req.gender(), req.birthday(), req.cityId()));
    }

    /**
     * 替换兴趣标签
     * PUT /v1/users/{userId}/interests
     */
    @PutMapping("/{userId}/interests")
    public Result<Void> replaceInterests(
            @PathVariable Long userId,
            @RequestBody List<UserProfileVO.UserInterestVO> interests) {
        userProfileService.replaceUserInterests(userId, interests);
        return Result.ok(null);
    }

    // ──────────────────────────────────────────────
    //  推荐
    // ──────────────────────────────────────────────

    /**
     * DH 候选列表
     * GET /v1/users/dh-candidates
     */
    @GetMapping("/dh-candidates")
    public Result<List<UserProfileVO>> listDhCandidates(
            @RequestParam(required = false) Integer gender,
            @RequestParam(defaultValue = "18") int ageMin,
            @RequestParam(defaultValue = "100") int ageMax,
            @RequestParam(defaultValue = "0") int beautyMin,
            @RequestParam(defaultValue = "100") int beautyMax,
            @RequestParam(defaultValue = "20") int limit) {
        var candidates = recommendationService.listDhCandidates(
                gender, ageMin, ageMax, beautyMin, beautyMax,
                List.of(), List.of(), limit);
        var profiles = userProfileService.batchGetProfile(
                candidates.stream().map(c -> c.getUserId()).toList());
        return Result.ok(profiles);
    }

    // ─── 请求体 DTO ───

    public record LoginPhoneReq(String phoneE164) {}
    public record ThirdPartyLoginReq(String thirdPartyUserId, int platform) {}
    public record DeviceLoginReq(String deviceId, int platform) {}
    public record BatchProfileReq(List<Long> userIds) {}
    public record UpdateProfileReq(String nickname, Integer gender, LocalDate birthday,
                                    Long cityId, String race, Integer beautyScore) {}
    public record OnboardingReq(String nickname, int gender, LocalDate birthday, Long cityId) {}
}
