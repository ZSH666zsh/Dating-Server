package com.dating.gateway.controller;

import com.dating.gateway.client.MatchClient;
import com.dating.gateway.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.dating.zhaoshihang.proto.match.SwipeDirection;
import java.util.Map;

/**
 * 匹配 REST 接口（BFF 路由 → match-service gRPC）。
 */
@RestController
@RequestMapping("/api/v1/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchClient matchClient;

    /**
     * 拉推荐 Feed
     * GET /api/v1/match/feed?count=5
     */
    @GetMapping("/feed")
    public Result<?> getFeed(@RequestParam(defaultValue = "5") int count) {
        var resp = matchClient.getTodayFeed(count);
        return Result.ok(resp.getCardsList());
    }

    /**
     * 右划/左划
     * POST /api/v1/match/swipe
     */
    @PostMapping("/swipe")
    public Result<?> swipe(@RequestBody SwipeReq req) {
        var direction = req.direction() == 2 ? SwipeDirection.RIGHT : SwipeDirection.LEFT;
        var resp = matchClient.swipe(req.targetUserId(), direction);
        return Result.ok(Map.of(
                "matched", resp.getMatched(),
                "matchId", resp.getMatchId()
        ));
    }

    /**
     * Super Hi
     * POST /api/v1/match/super-hi
     */
    @PostMapping("/super-hi")
    public Result<?> superHi(@RequestBody SuperHiReq req) {
        var resp = matchClient.superHi(req.targetUserId(), req.clientRequestId());
        return Result.ok(Map.of(
                "matched", resp.getMatched(),
                "matchId", resp.getMatchId(),
                "coinsUsed", resp.getCoinsUsed()
        ));
    }

    /**
     * 获取配额
     * GET /api/v1/match/quota
     */
    @GetMapping("/quota")
    public Result<?> getQuota() {
        var resp = matchClient.getQuota();
        return Result.ok(Map.of(
                "dailyRightSwipeLimit", resp.getDailyRightSwipeLimit(),
                "dailyRightSwipeUsed", resp.getDailyRightSwipeUsed(),
                "dailyCardLimit", resp.getDailyCardLimit(),
                "dailyCardUsed", resp.getDailyCardUsed(),
                "subscriptionTier", resp.getSubscriptionTier()
        ));
    }

    /**
     * 谁 Like 了我
     * GET /api/v1/match/likes?pageSize=20
     */
    @GetMapping("/likes")
    public Result<?> listLikes(@RequestParam(defaultValue = "20") int pageSize,
                                @RequestParam(defaultValue = "") String pageToken) {
        var resp = matchClient.listLikesOfMe(pageSize, pageToken);
        return Result.ok(Map.of("likes", resp.getLikesList(), "nextToken", resp.getNextPageToken()));
    }

    /**
     * 谁访问了我
     * GET /api/v1/match/visits?pageSize=20
     */
    @GetMapping("/visits")
    public Result<?> listVisits(@RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(defaultValue = "") String pageToken) {
        var resp = matchClient.listVisitsOfMe(pageSize, pageToken);
        return Result.ok(Map.of("visits", resp.getVisitsList(), "nextToken", resp.getNextPageToken()));
    }

    /**
     * 上报主页访问
     * POST /api/v1/match/visit/record
     */
    @PostMapping("/visit/record")
    public Result<Void> recordVisit(@RequestBody VisitReq req) {
        matchClient.recordVisit(req.targetUserId());
        return Result.ok();
    }

    public record SwipeReq(Long targetUserId, int direction) {}
    public record SuperHiReq(Long targetUserId, String clientRequestId) {}
    public record VisitReq(Long targetUserId) {}
}
