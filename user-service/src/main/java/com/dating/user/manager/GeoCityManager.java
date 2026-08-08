package com.dating.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.user.entity.GeoCity;
import com.dating.user.mapper.GeoCityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 城市字典管理 */
@Component
@RequiredArgsConstructor
public class GeoCityManager {

    private final GeoCityMapper mapper;

    public GeoCity getById(Long id) {
        return mapper.selectById(id);
    }

    /** 查某州的所有城市 */
    public List<GeoCity> listByStateCode(String stateCode) {
        return mapper.selectList(
                new LambdaQueryWrapper<GeoCity>()
                        .eq(GeoCity::getStateCode, stateCode)
                        .orderByAsc(GeoCity::getCity)
        );
    }

    /** 批量查城市 */
    public Map<Long, GeoCity> getMapByIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return mapper.selectList(
                new LambdaQueryWrapper<GeoCity>()
                        .in(GeoCity::getId, ids)
        ).stream().collect(Collectors.toMap(GeoCity::getId, c -> c, (a, b) -> a));
    }
}
