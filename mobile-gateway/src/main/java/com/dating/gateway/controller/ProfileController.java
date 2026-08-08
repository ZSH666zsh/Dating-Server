package com.dating.gateway.controller;

import com.dating.gateway.service.ProfileService;
import com.dating.gateway.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户档案 REST 接口。
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 获取用户资料（可查他人）
     * GET /api/v1/profile?userId=123
     */
    @GetMapping
    public Result<?> getProfile(
            HttpServletRequest request,
            @RequestParam Long userId) {
        var resp = profileService.getProfile(userId);
        if (resp.getBase().getCode() != 0) {
            return Result.error(resp.getBase().getCode(), resp.getBase().getMessage());
        }
        var p = resp.getProfile();
        return Result.ok(Map.of(
                "userId", p.getUserId(),
                "nickname", p.getNickname(),
                "age", p.getAge(),
                "gender", p.getGender(),
                "cityName", p.getCityName(),
                "avatarKey", p.getCustomAvatar(),
                "userType", p.getUserType()
        ));
    }

    /**
     * Onboarding 完善资料
     * POST /api/v1/profile/onboarding
     */
    @PostMapping("/onboarding")
    public Result<?> onboarding(
            HttpServletRequest request,
            @RequestBody OnboardingReq req) {
        Long userId = (Long) request.getAttribute("userId");
        var resp = profileService.onboarding(userId, req.nickname(), req.gender(), req.birthday(), req.cityId());
        if (resp.getBase().getCode() != 0) {
            return Result.error(resp.getBase().getCode(), resp.getBase().getMessage());
        }
        return Result.ok(Map.of("userId", userId));
    }

    /**
     * 更新资料
     * PATCH /api/v1/profile
     */
    @PatchMapping
    public Result<Void> updateProfile(
            HttpServletRequest request,
            @RequestBody UpdateProfileReq req) {
        Long userId = (Long) request.getAttribute("userId");
        profileService.updateProfile(userId, req.nickname(), req.gender(), req.birthday(), req.cityId());
        return Result.ok();
    }

    public record OnboardingReq(String nickname, int gender, String birthday, Long cityId) {}
    public record UpdateProfileReq(String nickname, Integer gender, String birthday, Long cityId) {}
}
