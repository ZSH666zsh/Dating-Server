package com.dating.match.service;

import com.dating.match.client.ImServiceClient;
import com.dating.match.client.UserServiceClient;
import com.dating.match.config.CacheKeyBuilder;
import com.dating.match.config.SnowflakeIdGenerator;
import com.dating.match.constant.Direction;
import com.dating.match.constant.ErrorCode;
import com.dating.match.constant.Source;
import com.dating.match.constant.TargetUserType;
import com.dating.match.entity.UserSwipeHistory;
import com.dating.match.exception.BizException;
import com.dating.match.manager.MatchManager;
import com.dating.match.manager.QuotaManager;
import com.dating.match.manager.SwipeHistoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 划卡服务。
 * 对应 match-service-prd-tech.md §5 ~ §7.6。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwipeService {

    private final SwipeHistoryManager swipeHistoryManager;
    private final MatchManager matchManager;
    private final QuotaService quotaService;
    private final QuotaManager quotaManager;
    private final UserServiceClient userClient;
    private final ImServiceClient imClient;
    private final DhDelayedMatchService delayedMatchService;
    private final RedissonClient redisson;
    private final SnowflakeIdGenerator idGenerator;
    private final CacheKeyBuilder keyBuilder;

    /**
     * 划卡（LEFT / RIGHT）。
     *
     * @return 是否立即 match
     */
    @Transactional(rollbackFor = Exception.class)
    public SwipeResult swipe(Long userId, Long targetUserId, int direction) {
        // 分布式锁防并发
        RLock lock = redisson.getLock(keyBuilder.lockPrefix() + "swipe:" + userId + ":" + targetUserId);
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.CONCURRENT_SWIPE, "操作太频繁，请重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.CONCURRENT_SWIPE, "操作被中断");
        }

        try {
            // 幂等检查：已经划过
            if (swipeHistoryManager.exists(userId, targetUserId)) {
                log.warn("Swipe duplicate: userId={} targetId={}", userId, targetUserId);
                throw new BizException(ErrorCode.SWIPE_DUPLICATE, "已经划过该用户");
            }

            // user-service查目标用户类型
            Integer targetType = userClient.getUserType(targetUserId);
            if (targetType == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND, "目标用户不存在");
            }

            // 扣配额
            quotaService.checkAndConsumeCard(userId);  // 扣卡片配额
            if (direction == Direction.RIGHT) {
                quotaService.checkAndConsumeRightSwipe(userId);  // 扣右划次数
            }

            // 写划卡历史
            UserSwipeHistory history = new UserSwipeHistory();
            history.setId(idGenerator.nextId());
            history.setUserId(userId);
            history.setTargetUserId(targetUserId);
            history.setTargetUserType(targetType);
            history.setDirection(direction);
            history.setSwipedAt(OffsetDateTime.now());
            swipeHistoryManager.insert(history);

            // 右划 → 进一步处理匹配
            if (direction == Direction.RIGHT) {
                return handleRightSwipe(userId, targetUserId, targetType);
            }

            return new SwipeResult(false, null, 0, 0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Super Hi 划卡（无视对方意愿，立即匹配）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SuperHiResult superHi(Long userId, Long targetUserId, String clientRequestId) {
        // 幂等检查
        if (swipeHistoryManager.exists(userId, targetUserId)) {
            throw new BizException(ErrorCode.SWIPE_DUPLICATE, "已经划过该用户");
        }

        // 查目标类型
        Integer targetType = userClient.getUserType(targetUserId);
        if (targetType == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }

        // 检查 Super Hi 配额 / 金币
        var quotaCheck = quotaService.checkSuperHi(userId);
        int coinCost = quotaCheck.coinCost();

        // 写划卡历史
        UserSwipeHistory history = new UserSwipeHistory();
        history.setId(idGenerator.nextId());
        history.setUserId(userId);
        history.setTargetUserId(targetUserId);
        history.setTargetUserType(targetType);
        history.setDirection(3); // SUPER_HI
        history.setSwipedAt(OffsetDateTime.now());
        swipeHistoryManager.insert(history);

        // 消耗配额
        quotaService.consumeSuperHi(userId, coinCost);

        // Super Hi 立即匹配
        Long matchId = createMatchAndNotify(userId, targetUserId, Source.SWIPE_SUPER_HI);

        return new SuperHiResult(true, matchId,
                Math.max(0, quotaCheck.hasFreeSuperHi() ? 1 : 0),
                coinCost);
    }

    /**
     * 处理右划逻辑。
     */
    private SwipeResult handleRightSwipe(Long userId, Long targetUserId, int targetType) {
        if (targetType == TargetUserType.DH) {
            // DH 右划 → 延迟匹配（15s-2min）
            delayedMatchService.scheduleDelayedMatch(userId, targetUserId);
            return new SwipeResult(false, null, 0, 0);
        }

        // BH 右划 → 检查对方是否已右划过我
        boolean theyLikedMe = swipeHistoryManager.exists(targetUserId, userId)
                && userClient.getUserType(targetUserId) == TargetUserType.BH;

        if (theyLikedMe) {
            // 互划 → 立即 match
            Long matchId = createMatchAndNotify(userId, targetUserId, Source.SWIPE_MATCH);
            return new SwipeResult(true, matchId, 0, 0);
        }

        return new SwipeResult(false, null, 0, 0);
    }

    /**
     * 创建匹配并发送通知。
     */
    private Long createMatchAndNotify(Long userA, Long userB, String source) {
        Long matchId = idGenerator.nextId();
        int rows = matchManager.createMatch(matchId, userA, userB, source);

        if (rows == 0) {
            // 已存在匹配
            var existing = matchManager.getByPair(userA, userB);
            if (existing != null) {
                log.warn("Duplicate match: pair=({}, {})", userA, userB);
                return existing.getId();
            }
        }

        // 异步通知 im-service（TODO: 走 match_outbox）
        imClient.notifyMatchSuccess(matchId, userA, userB);
        imClient.ensureConversation(userA, userB);

        return matchId;
    }

    // ─── 结果类 ───

    public record SwipeResult(boolean matched, Long matchId,
                               int remainingRightSwipes, int remainingCards) {}

    public record SuperHiResult(boolean matched, Long matchId,
                                 int remainingSuperHi, int coinsUsed) {}
}
