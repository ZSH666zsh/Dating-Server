package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.UserInfo;
import com.dating.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户主档案管理 —— 单表 CRUD。
 * 遵循 1ARCHITECTURE.md §15.1 红线 #1：禁多表 JOIN，跨表在 service 层组合。
 */
@Component
@RequiredArgsConstructor
public class UserInfoManager {

    private final UserInfoMapper userInfoMapper;

    // ─── 写 ───

    public void insert(UserInfo userInfo) {
        userInfoMapper.insert(userInfo);
    }

    public void updateById(UserInfo userInfo) {  // 按物理主键更新
        userInfoMapper.updateById(userInfo);
    }

    /**  按业务主键 user_id 更新指定字段（仅 SET 非 null 字段） */
    public void updateSelective(UserInfo userInfo) {
        userInfoMapper.update(userInfo, new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserId, userInfo.getUserId()));
    }

    // ─── 读 ───

    /** 单用户查询 */
    public UserInfo getByUserId(Long userId) {
        return userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>()
                        .eq(UserInfo::getUserId, userId)
                        .eq(UserInfo::getDeleted, 0)
        );
    }

    /** 批量查用户（去重保序，上限 200） */
    public List<UserInfo> selectBatchByUserIds(List<Long> userIds) {
        return userInfoMapper.selectList(
                new LambdaQueryWrapper<UserInfo>()
                        .in(UserInfo::getUserId, userIds)
                        .eq(UserInfo::getDeleted, 0)
        );
    }

    /** 查多个 userId → UserInfo 映射 */
    public Map<Long, UserInfo> getMapByUserIds(List<Long> userIds) {
        return selectBatchByUserIds(userIds).stream()
                .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
    }

    /**
     * 候选人查询（DH/BH 筛选的核心方法）。
     *
     * 这里用 LambdaQueryWrapper 动态拼接 WHERE 条件，核心技巧是：
     * 条件 .eq(UserInfo::getUserType, userType) 首先固定 userType + 未删除，
     * 让数据库能命中 idx_user_info_recall (user_type, gender, city_id, age, beauty_score) 复合索引。
     *
     * 【为什么 .eq(UserInfo::getUserType, userType) 能让索引命中？】
     * → 因为 idx_user_info_recall 复合索引的第一个字段就是 user_type。
     *    WHERE user_type=? AND deleted=0 可以用索引做精准定位，不需要全表扫描。
     *    如果不传 userType，这个索引就用不了 —— 这叫索引的"最左前缀原则"：
     *    复合索引 (A, B, C) 只能用在 WHERE A=? 或 WHERE A=? AND B=? 等从 A 开始的查询。
     *
     * 【为什么这里不用 JOIN？】
     * → 红线要求单表操作。如果需要补充其他表的信息（比如城市名），
     *    在 Service 层拿到 userIds 列表后查 geo_city 表组合，严禁 Manager 层跨表 JOIN。
     *
     * @param userType       1=BH(真人) 2=DH(数字人)
     * @param gender         目标性别（null=不限）
     * @param ageMin,ageMax  年龄范围
     * @param beautyMin,beautyMax  颜值分范围
     * @param races          人种白名单（null=不限）
     * @param excludeUserIds 需要排除的用户（如已划过的卡）
     * @param limit          最多返回条数（内部会 clamp 到 240）
     */
    public List<UserInfo> listCandidates(int userType, Integer gender,
                                          Integer ageMin, Integer ageMax,
                                          Integer beautyMin, Integer beautyMax,
                                          List<String> races,
                                          List<Long> excludeUserIds,
                                          int limit) {
        // 【1】构建查询条件：固定 userType 让索引命中（最左前缀）
        // 对应 SQL: SELECT ... FROM user_info WHERE user_type=? AND deleted=0 ...
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserType, userType)  // 【关键行】匹配 idx_user_info_recall 首字段
                .eq(UserInfo::getDeleted, 0);          // 逻辑删除过滤

        // 【2】以下都是可选过滤条件 —— 不影响索引的定位能力
        // （PG/MySQL 可以在已经通过索引定位的行上做 Filter）
        if (gender != null && gender > 0) {
            wrapper.eq(UserInfo::getGender, gender);  // 索引的第二个字段，如果传了可以直接过滤
        }
        if (ageMin != null) wrapper.ge(UserInfo::getAge, ageMin);
        if (ageMax != null) wrapper.le(UserInfo::getAge, ageMax);
        if (beautyMin != null) wrapper.ge(UserInfo::getBeautyScore, beautyMin);
        if (beautyMax != null) wrapper.le(UserInfo::getBeautyScore, beautyMax);
        if (races != null && !races.isEmpty()) {
            wrapper.in(UserInfo::getRace, races);
        }
        if (excludeUserIds != null && !excludeUserIds.isEmpty()) {
            wrapper.notIn(UserInfo::getUserId, excludeUserIds);
        }

        // 【3】排序 + 截断：按颜值降序（"高质量"用户排前面），取前 limit 个
        wrapper.orderByDesc(UserInfo::getBeautyScore);
        wrapper.last("LIMIT " + limit);               // 拼接到 SQL 末尾

        // 【4】执行单表查询，返回 UserInfo 列表
        return userInfoMapper.selectList(wrapper);
    }

    /** 同城用户查询（按 city_id 过滤） */
    public List<UserInfo> listNearbyUsers(Long cityId, Integer targetGender,
                                           int maxDaysInactive, List<Long> excludeIds, int limit) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getCityId, cityId)
                .eq(UserInfo::getDeleted, 0)
                .eq(UserInfo::getUserType, 1); // BH only

        if (targetGender != null && targetGender > 0) {
            wrapper.eq(UserInfo::getGender, targetGender);
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(UserInfo::getUserId, excludeIds);
        }

        // 最近活跃
        OffsetDateTime since = OffsetDateTime.now().minusDays(maxDaysInactive);
        wrapper.ge(UserInfo::getLastOpenAt, since);

        wrapper.orderByDesc(UserInfo::getBeautyScore);
        wrapper.last("LIMIT " + limit);

        return userInfoMapper.selectList(wrapper);
    }

    /** 按 phoneE164 查用户（通过绑定表关联，由 Service 层组合调用） */
    public UserInfo getByPhone(String phoneE164) {
        // 此方法应通过 UserLoginManager.getByPhone → 取 userId → this.getByUserId 组合实现
        // 不在 Manager 层 JOIN，符合红线 #1
        throw new UnsupportedOperationException(
            "请在 Service 层通过 UserLoginManager.getByPhone(phone).userId → userInfoManager.getByUserId(userId) 组合查询"
        );
    }
}
