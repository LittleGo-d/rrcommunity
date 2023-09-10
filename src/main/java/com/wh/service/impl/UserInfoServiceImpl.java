package com.wh.service.impl;

import com.wh.entity.UserInfo;
import com.wh.mapper.UserInfoMapper;
import com.wh.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 *
 *  服务实现类
 *
 *
 * @author wh
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
