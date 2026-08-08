package com.dating.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dating.user.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/** 用户主档案 Mapper —— 单表，禁 JOIN */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
