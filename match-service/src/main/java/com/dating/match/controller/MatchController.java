package com.dating.match.controller;

import com.dating.match.service.*;
import com.dating.match.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 匹配 REST 接口（本机调试用，生产侧由 mobile-gateway 走 gRPC）。
 *
 * <p>所有接口入参 userId 从请求头 X-User-Id 取（模拟 gateway JWT 注入）。
 */
@Slf4j
@RestController
@RequestMapping("/v1/match")
@RequiredArgsConstructor
public class MatchController {

    private final FeedService feedService;
    private final SwipeService swipeService;
    private final QuotaService quotaService;
    private final LikeVisitService likeVisitService;

    private Long currentUserId(@RequestHeader("X-User-Id") Long userId) {
        return userId;
    }

    /**
     * 拉取推荐 Feed 卡片
     * GET /v1/match/feed?count=5
     */
    @GetMapping("/feed")
    public Result<List<FeedService.CardResult>> getFeed(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "5") int count) {
        var cards = feedService.getTodayFeed(userId, count);
        return Result.ok(cards);
    }

    /**
     * 右划（喜欢）
     * POST /v1/match/swipe
     */
    @PostMapping("/swipe")
    public Result<SwipeService.SwipeResult> swipe(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SwipeReq req) {
        var result = swipeService.swipe(userId, req.targetUserId(), req.direction());
        return Result.ok(result);
    }

    /**
     * Super Hi
     * POST /v1/match/super-hi
     */
    @PostMapping("/super-hi")
    public Result<SwipeService.SuperHiResult> superHi(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SuperHiReq req) {
        var result = swipeService.superHi(userId, req.targetUserId(), req.clientRequestId());
        return Result.ok(result);
    }

    /**
     * 获取当日配额
     * GET /v1/match/quota
     */
    @GetMapping("/quota")
    public Result<QuotaService.QuotaInfo> getQuota(
            @RequestHeader("X-User-Id") Long userId) {
        return Result.ok(quotaService.getQuota(userId));
    }

    /**
     * 谁 Like 了我
     * GET /v1/match/likes?pageSize=20
     */
    @GetMapping("/likes")
    public Result<List<?>> listLikes(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "0") Long cursor) {
        var likes = likeVisitService.listLikesOfMe(userId, pageSize, cursor > 0 ? cursor : null);
        return Result.ok(likes);
    }

    /**
     * 谁访问了我
     * GET /v1/match/visits?pageSize=20
     */
    @GetMapping("/visits")
    public Result<List<?>> listVisits(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "0") Long cursor) {
        var visits = likeVisitService.listVisitsOfMe(userId, pageSize, cursor > 0 ? cursor : null);
        return Result.ok(visits);
    }

    /**
     * 上报主页访问
     * POST /v1/match/visit/record
     */
    @PostMapping("/visit/record")
    public Result<Void> recordVisit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody VisitReq req) {
        likeVisitService.recordVisit(userId, req.targetUserId());
        return Result.ok(null);
    }

    // ─── 请求体 DTO ───

    public record SwipeReq(Long targetUserId, int direction) {}
    public record SuperHiReq(Long targetUserId, String clientRequestId) {}
    public record VisitReq(Long targetUserId) {}
}
