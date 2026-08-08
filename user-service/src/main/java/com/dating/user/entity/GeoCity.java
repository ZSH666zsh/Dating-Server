package com.dating.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/** 城市字典表（SimpleMaps US ~28k 行）—— geo_city */
@Data
@TableName("geo_city")
public class GeoCity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String city;
    private String stateCode;
    private String stateName;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer population;
    private Integer sourceId;
}
