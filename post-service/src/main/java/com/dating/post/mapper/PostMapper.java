package com.dating.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.post.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子 Mapper —— 单表，禁 JOIN
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
