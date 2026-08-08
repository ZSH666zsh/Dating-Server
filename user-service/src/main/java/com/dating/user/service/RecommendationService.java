package com.dating.user.service;

import com.dating.user.entity.GeoCity;
import com.dating.user.entity.UserInfo;
import com.dating.user.manager.GeoCityManager;
import com.dating.user.manager.UserInfoManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐召回服务 —— 数字人(DH)候选 / 附近真人(BH) / DH 城市分配。
 *
 * <h3>三种召回</h3>
 * ① {@link #listDhCandidates} — 按条件筛选数字人
 * ② {@link #nearbyUsers} — 同城 BH 召回
 * ③ {@link #pickDhCitiesForCaller} — 确定性城市分配（防 DH 被集中骚扰）
 *
 * @see <a href="1ARCHITECTURE.md §6.4">RecommendationService 接口定义</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserInfoManager userInfoManager;
    private final GeoCityManager geoCityManager;

    // ──────────────────────────────────────────────
    //  DH 候选列表
    // ──────────────────────────────────────────────

    /**
     * 查询数字人(DH)候选列表。
     * 按颜值分降序排列，上限 240。
     */
    public List<UserInfo> listDhCandidates(Integer targetGender,
                                            Integer ageMin, Integer ageMax,
                                            Integer beautyMin, Integer beautyMax,
                                            List<String> races,
                                            List<Long> excludeUserIds,
                                            int limit) {
        if (limit <= 0 || limit > 240) limit = 240;

        return userInfoManager.listCandidates(
                2,          // userType = DH
                targetGender,
                ageMin, ageMax,
                beautyMin, beautyMax,
                races,
                excludeUserIds,
                limit
        );
    }

    // ──────────────────────────────────────────────
    //  附近用户
    // ──────────────────────────────────────────────

    /**
     * 查询同城 BH 用户。
     * 根据 caller 的 city_id 推 target gender 用户，按活跃度 + 颜值排序。
     */
    public List<UserInfo> nearbyUsers(Long callerUserId,
                                       Integer targetGender,
                                       int maxDaysInactive,
                                       List<Long> excludeUserIds,
                                       int limit) {
        if (limit <= 0 || limit > 240) limit = 240;

        // 查 caller 所在城市
        UserInfo caller = userInfoManager.getByUserId(callerUserId);
        if (caller == null || caller.getCityId() == null || caller.getCityId() <= 0) {
            log.debug("Caller {} has no city, returning empty nearby", callerUserId);
            return List.of();
        }

        List<Long> exclude = new ArrayList<>();
        if (excludeUserIds != null) exclude.addAll(excludeUserIds);
        exclude.add(callerUserId); // 排除自己

        return userInfoManager.listNearbyUsers(
                caller.getCityId(),
                targetGender,
                maxDaysInactive,
                exclude,
                limit
        );
    }

    // ──────────────────────────────────────────────
    //  DH 城市分配
    // ──────────────────────────────────────────────

    /**
     * 为调用者(caller)分配每个 DH 的展示城市。
     * 用哈希算法保证同一对 (caller, DH) 始终分到同一城市。
     * 避免所有 caller 都把同一个 DH 分配到热门城市。
     */
    public Map<Long, Long> pickDhCitiesForCaller(Long callerUserId, List<Long> dhUserIds) {
        if (dhUserIds == null || dhUserIds.isEmpty()) return Map.of();

        // 限制最多 500 个 DH
        if (dhUserIds.size() > 500) {
            dhUserIds = dhUserIds.subList(0, 500);
        }

        // 查 caller 所在州
        UserInfo caller = userInfoManager.getByUserId(callerUserId);
        if (caller == null) return Map.of();

        // 查 caller 所在州的城市列表
        // 先用 caller 的 city_id 查城市获取 state_code
        GeoCity callerCity = caller.getCityId() != null ? geoCityManager.getById(caller.getCityId()) : null;
        if (callerCity == null) {
            return Map.of();
        }

        // 查同州所有城市
        List<GeoCity> citiesInState = geoCityManager.listByStateCode(callerCity.getStateCode());
        if (citiesInState.isEmpty()) {
            return Map.of();
        }

        // 移除 caller 自己的城市（防骚扰）
        List<GeoCity> candidateCities = citiesInState.stream()
                .filter(c -> !c.getId().equals(caller.getCityId()))
                .collect(Collectors.toList());
        if (candidateCities.isEmpty()) {
            candidateCities = citiesInState; // 兜底
        }

        // 确定性哈希分配：hash(dh_id, caller_id) % cities
        Map<Long, Long> result = new HashMap<>();
        for (Long dhId : dhUserIds) {
            int hash = (dhId.hashCode() * 31 + callerUserId.hashCode()) & Integer.MAX_VALUE;
            int idx = hash % candidateCities.size();
            result.put(dhId, candidateCities.get(idx).getId());
        }

        return result;
    }
}
