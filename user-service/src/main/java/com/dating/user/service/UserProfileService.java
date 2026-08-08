package com.dating.user.service;

import com.dating.user.config.CacheKeyBuilder;
import com.dating.user.constant.ErrorCode;
import com.dating.user.entity.UserInfo;
import com.dating.user.entity.UserInterest;
import com.dating.user.exception.BizException;
import com.dating.user.manager.GeoCityManager;
import com.dating.user.manager.UserInfoManager;
import com.dating.user.manager.UserInterestManager;
import com.dating.user.vo.UserProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**

 *
 * 用户档案服务 —— 档案 CRUD + 兴趣标签 + 头像上传。
 *
 * <h3>缓存策略</h3>
 * 写：  Cache Aside（先写 DB 再删缓存。不双写）
 * 读：  先查 Redis（TTL 30min），miss → 查 DB → 组装 VO → 写 Redis (TTL 30min)
 *
 * @see <a href="1ARCHITECTURE.md §6.3">Redis 缓存设计</a>

 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserInfoManager userInfoManager;
    private final UserInterestManager userInterestManager;
    private final GeoCityManager geoCityManager;
    private final StringRedisTemplate redisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final ObjectMapper objectMapper;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;
    private final software.amazon.awssdk.services.s3.S3Client s3Client;

    @org.springframework.beans.factory.annotation.Value("${dating.object-storage.bucket:dating-zhaoshihang}")
    private String bucket;

    // ──────────────────────────────────────────────
    //  读取档案
    // ──────────────────────────────────────────────

    /**
     * 获取单个用户档案（缓存优先）。
     * 缓存 key: {@code user:profile:{userId}}，TTL 30min。
     */
    public UserProfileVO getProfile(Long userId) {
        // 1. 尝试缓存
        UserProfileVO cached = getFromCache(userId);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存 miss → 查 DB
        UserInfo user = userInfoManager.getByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        // 3. 查兴趣标签
        List<UserInterest> interests = userInterestManager.listByUserId(userId);

        // 4. 查城市信息
        String cityName = "";
        String stateCode = "";
        if (user.getCityId() != null && user.getCityId() > 0) {
            var city = geoCityManager.getById(user.getCityId());
            if (city != null) {
                cityName = city.getCity();
                stateCode = city.getStateCode();
            }
        }

        // 5. 拼装 VO
        UserProfileVO vo = buildProfileVO(user, interests, cityName, stateCode);

        // 6. 写缓存（best-effort）
        try {
            writeToCache(userId, vo);
        } catch (Exception e) {
            log.warn("Failed to cache user profile, userId={}", userId, e);
        }

        return vo;
    }

    /**
     * 批量获取用户档案（不走缓存，上限 200）。
     * 适用于内部批量查询场景。
     *
     * <h3>为什么这里要特意说"避免 N+1"？</h3>
     * <p>N+1 问题是 ORM/数据库查询中最常见的性能陷阱。</p>
     * <pre>
     * 【N+1 问题的产生】：
     * 假设要给 100 个用户查档案 + 查兴趣标签。
     * 新手写法会在循环里逐条查：
     *   for (Long id : userIds) {              // 1 次
     *       UserInfo user = userInfoMapper.getByUserId(id);      // N=100 次
     *       List<Interest> interests = interestMapper.listByUserId(id); // N=100 次
     *   }
     * 总查询次数 = 1（启动循环） + 100（查用户） + 100（查兴趣） = 201 次数据库查询
     * → 这就是 N+1：1 次主查询 + N 次关联查询，N=100 时就是 201 条 SQL。
     *
     * 【如何避免 N+1】：
     * 把 N 次循环查询合并为 2 次批量查询：
     *   1 次: SELECT * FROM user_info WHERE user_id IN (?,?,?,...)     ← 1 条 SQL 查全部用户
     *   1 次: SELECT * FROM user_interest WHERE user_id IN (?,?,?,...) ← 1 条 SQL 查全部标签
     * 总查询次数 = 2 次（无论 N 是 100 还是 200），效率提升 100 倍。
     * </pre>
     */
    public List<UserProfileVO> batchGetProfile(List<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        if (userIds.size() > 200) {
            userIds = userIds.subList(0, 200);         // 上限 200，截断保护
        }

        // 1. 去重 + 保序（调用方可能传重复 id）
        List<Long> distinctIds = userIds.stream().distinct().collect(Collectors.toList());

        // 2.【重点】一次性查 user_info —— 1 条 SQL 代替 N 条
        //    对应 SQL: SELECT * FROM user_info WHERE user_id IN (id1,id2,...) AND deleted=0
        //    这就是解决 N+1 的第一步：把 N 次单查合并为 1 次批量查
        Map<Long, UserInfo> userMap = userInfoManager.getMapByUserIds(distinctIds);

        // 3.【重点】一次性查 interests —— 1 条 SQL 代替 N 条
        //    同样道理，避免在循环中逐条查兴趣
        List<UserInterest> allInterests = userInterestManager.listByUserIds(distinctIds);
        Map<Long, List<UserInterest>> interestMap = allInterests.stream()  // 转为 Map，方便按 userId 快速定位
                .collect(Collectors.groupingBy(UserInterest::getUserId));

        // 4. 内存中组装每个用户的 VO（此时不查数据库，纯内存操作）
        return distinctIds.stream()
                .map(id -> {
                    UserInfo user = userMap.get(id);          // 从 Map 中 O(1) 取
                    if (user == null) return null;             // 用户不存在（比如被删了）就跳过
                    List<UserInterest> interests = interestMap.getOrDefault(id, List.of());
                    return buildProfileVO(user, interests, "", "");
                })
                .filter(vo -> vo != null)                     // 过滤掉 null 的条目
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    //  更新档案
    // ──────────────────────────────────────────────

    /**
     * 更新用户档案。proto3 optional 语义：只更新有值的字段。
     */
    public UserProfileVO updateProfile(Long userId, String nickname, Integer gender,
                                        LocalDate birthday, Long cityId,
                                        String race, Integer beautyScore) {
        UserInfo user = userInfoManager.getByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        // 动态 SET
        if (nickname != null) user.setNickname(nickname);
        if (gender != null) user.setGender(gender);
        if (birthday != null) user.setBirthday(birthday);
        if (cityId != null) user.setCityId(cityId);
        if (race != null) user.setRace(race);
        if (beautyScore != null) user.setBeautyScore(beautyScore);

        userInfoManager.updateSelective(user);

        // 删缓存
        evictCache(userId);

        log.info("Profile updated: userId={}", userId);
        return getProfile(userId); // 重新查并缓存
    }

    /**
     * Onboarding：首次登录后的信息完善。
     * 校验必填字段，设置 pending=false。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO upsertOnboarding(Long userId, String nickname, int gender,
                                           LocalDate birthday, Long cityId) {
        // 校验必填
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new BizException(ErrorCode.NICKNAME_EMPTY, "昵称不能为空");
        }
        if (nickname.length() > 64) {
            throw new BizException(ErrorCode.NICKNAME_TOO_LONG, "昵称不能超过 64 字符");
        }
        if (gender != 1 && gender != 2) {
            throw new BizException(ErrorCode.GENDER_INVALID, "性别无效");
        }
        if (birthday == null) {
            throw new BizException(ErrorCode.BIRTHDAY_INVALID, "生日不能为空");
        }

        UserInfo user = userInfoManager.getByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        user.setNickname(nickname.trim());
        user.setGender(gender);
        user.setBirthday(birthday);
        user.setCityId(cityId);

        // 更新 user_info + 设置 pending=false
        user.setPending(false);
        userInfoManager.updateSelective(user);

        // 删缓存
        evictCache(userId);

        return getProfile(userId);
    }

    /**
     * 全量替换兴趣标签（事务内 DELETE + INSERT）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserInterests(Long userId, List<UserProfileVO.UserInterestVO> interests) {
        List<UserInterest> entities = interests.stream().map(i -> {
            UserInterest entity = new UserInterest();
            entity.setTabKey(i.getTabKey());
            entity.setTagKey(i.getTagKey());
            entity.setPicKey(i.getPicKey() != null ? i.getPicKey() : "");
            return entity;
        }).collect(Collectors.toList());

        userInterestManager.replaceInterests(userId, entities);

        // 删两个缓存
        redisTemplate.delete(cacheKeyBuilder.userInterest(userId));
        redisTemplate.delete(cacheKeyBuilder.userProfileBig(userId));

        log.info("Interests replaced: userId={} count={}", userId, interests.size());
    }

    // ──────────────────────────────────────────────
    //  头像上传（服务器不碰文件流，App 端通过 presigned URL 直传 MinIO。）
    // ──────────────────────────────────────────────

    /**
     * 预签名头像上传 URL。
     * 返回 presigned PUT URL（5 分钟有效），App 端直传 MinIO。
     *
     * <p>实现说明：使用 AWS S3 SDK 直接生成 presigned URL（替代 dating-common ObjectStorage）。
     * 对应 user-service-design.md §5.4 头像上传。
     */
    public String presignAvatarUpload(Long userId, String ext) {
        // 校验扩展名
        if (ext == null || !List.of("jpg", "jpeg", "png", "webp").contains(ext.toLowerCase())) {
            throw new BizException(ErrorCode.AVATAR_KEY_INVALID, "仅支持 jpg/jpeg/png/webp");
        }

        // 生成 objectKey = "avatar/{userId}/{yyyymm}/{uuid}.jpg"
        String objectKey = "avatar/" + userId + "/"
                + java.time.LocalDate.now() + "/"
                + java.util.UUID.randomUUID() + "." + ext;

        // S3Presigner 生成 presigned PUT URL（5 分钟有效）
        var presignRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        var request = software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(presignRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(request).url().toString();
        log.debug("Presign avatar: userId={} key={} url={}", userId, objectKey, uploadUrl);
        return uploadUrl + "|" + objectKey; // 返回 "url|key" 格式，gateway 拆开使用
    }

    /**
     * 确认头像上传。校验 key 前缀 + 大小 → 更新 custom_avatar JSONB → 删缓存。
     *
     * <p>实现说明：使用 S3Client.headObject 校验文件存在和大小。
     */
    public void confirmAvatarUpload(Long userId, String objectKey) {
        // 校验 key 前缀
        if (objectKey == null || !objectKey.startsWith("avatar/" + userId + "/")) {
            throw new BizException(ErrorCode.AVATAR_KEY_INVALID, "头像 key 格式错误");
        }

        // 调用 S3 headObject 校验文件存在 + 大小 ≤ 10MB
        try {
            var headReq = software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            var headResp = s3Client.headObject(headReq);
            if (headResp.contentLength() > 10 * 1024 * 1024) {
                throw new BizException(ErrorCode.AVATAR_KEY_INVALID, "头像超过 10MB");
            }
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            throw new BizException(ErrorCode.AVATAR_KEY_INVALID, "头像文件不存在，请重新上传");
        }

        UserInfo user = userInfoManager.getByUserId(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        // 更新 custom_avatar JSONB
        String avatarJson = "{\"originalKey\":\"" + objectKey + "\"}";
        user.setCustomAvatar(avatarJson);
        userInfoManager.updateSelective(user);

        // 删缓存
        evictCache(userId);

        log.info("Avatar confirmed: userId={} key={}", userId, objectKey);
    }

    // ─── 私有方法 ───

    private UserProfileVO buildProfileVO(UserInfo user, List<UserInterest> interests,
                                          String cityName, String stateCode) {
        List<UserProfileVO.UserInterestVO> interestVOs = interests.stream()
                .map(i -> UserProfileVO.UserInterestVO.builder()
                        .tabKey(i.getTabKey())
                        .tagKey(i.getTagKey())
                        .picKey(i.getPicKey())
                        .build())
                .collect(Collectors.toList());

        return UserProfileVO.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .age(user.getAge())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .cityId(user.getCityId())
                .cityName(cityName)
                .stateCode(stateCode)
                .beautyScore(user.getBeautyScore())
                .race(user.getRace())
                .customAvatar(user.getCustomAvatar())
                .userType(user.getUserType())
                .pending(user.getPending())
                .interests(interestVOs)
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** 从 Redis Hash 读缓存 */
    private UserProfileVO getFromCache(Long userId) {
        String key = cacheKeyBuilder.userProfile(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) return null;

        try {
            String interestsJson = (String) entries.get("interestsJson");
            List<UserProfileVO.UserInterestVO> interests = List.of();
            if (interestsJson != null && !interestsJson.isEmpty()) {
                interests = objectMapper.readValue(interestsJson,
                        new TypeReference<List<UserProfileVO.UserInterestVO>>() {});
            }

            return UserProfileVO.builder()
                    .userId(Long.valueOf((String) entries.get("userId")))
                    .nickname((String) entries.get("nickname"))
                    .age(entries.containsKey("age") ? Integer.valueOf((String) entries.get("age")) : 0)
                    .gender(entries.containsKey("gender") ? Integer.valueOf((String) entries.get("gender")) : 0)
                    .beautyScore(entries.containsKey("beautyScore") ? Integer.valueOf((String) entries.get("beautyScore")) : 0)
                    .race((String) entries.get("race"))
                    .customAvatar((String) entries.get("customAvatar"))
                    .userType(entries.containsKey("userType") ? Integer.valueOf((String) entries.get("userType")) : 1)
                    .pending("true".equals(entries.get("pending")))
                    .interests(interests)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse profile cache, userId={}", userId, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    /** 写用户档案到 Redis Hash */
    private void writeToCache(Long userId, UserProfileVO vo) {
        try {
            String interestsJson = objectMapper.writeValueAsString(vo.getInterests());

            redisTemplate.opsForHash().putAll(cacheKeyBuilder.userProfile(userId), Map.of(
                    "userId", String.valueOf(vo.getUserId()),
                    "nickname", vo.getNickname() != null ? vo.getNickname() : "",
                    "age", String.valueOf(vo.getAge()),
                    "gender", String.valueOf(vo.getGender()),
                    "beautyScore", String.valueOf(vo.getBeautyScore()),
                    "race", vo.getRace() != null ? vo.getRace() : "",
                    "customAvatar", vo.getCustomAvatar() != null ? vo.getCustomAvatar() : "{}",
                    "userType", String.valueOf(vo.getUserType()),
                    "pending", String.valueOf(vo.getPending()),
                    "interestsJson", interestsJson
            ));
            redisTemplate.expire(cacheKeyBuilder.userProfile(userId), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to write profile cache, userId={}", userId, e);
        }
    }

    private void evictCache(Long userId) {
        redisTemplate.delete(cacheKeyBuilder.userProfile(userId));
        redisTemplate.delete(cacheKeyBuilder.userProfileBig(userId));
    }
}
