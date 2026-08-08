package com.dating.gateway.controller;

import com.dating.gateway.service.HomeService;
import com.dating.gateway.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 首页 BFF 聚合接口。
 */
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 首页用户卡片（BFF 聚合：用户资料 + 关系 + 在线状态）
     * GET /api/v1/home/card?targetId=456
     */
    @GetMapping("/card")
    public Result<?> homeCard(HttpServletRequest request, @RequestParam Long targetId) {
        Long userId = (Long) request.getAttribute("userId");
        var card = homeService.getHomeCard(userId, targetId);
        return Result.ok(card);
    }
}
