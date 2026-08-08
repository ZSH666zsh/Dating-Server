package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/** 用户兴趣标签表（1:N）—— user_interest */
@Data
@TableName("user_interest")
public class UserInterest {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String tabKey;          // 分类，如 sports/music
    private String tagKey;          // 标签，如 basketball/guitar
    private String picKey;          // 关联图片 key

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
