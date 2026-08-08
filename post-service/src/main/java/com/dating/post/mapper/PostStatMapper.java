package com.dating.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.post.entity.PostStat;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计数底座 Mapper
 */
@Mapper
public interface PostStatMapper extends BaseMapper<PostStat> {
}
