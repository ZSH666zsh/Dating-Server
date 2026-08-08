package com.dating.user.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 用户档案 VO —— 给前端/调用方看的完整用户信息 */
@Data
@Builder
public class UserProfileVO {
    private Long userId;
    private String nickname;
    private Integer age;
    private Integer gender;
    private LocalDate birthday;
    private Long cityId;
    private String cityName;
    private String stateCode;
    private Integer beautyScore;
    private String race;
    private String customAvatar;    // JSONB 字符串
    private Integer userType;       // 1=BH 2=DH
    private Boolean pending;
    private List<UserInterestVO> interests;
    private OffsetDateTime createdAt;

    @Data
    @Builder
    public static class UserInterestVO {
        private String tabKey;
        private String tagKey;
        private String picKey;
    }
}
