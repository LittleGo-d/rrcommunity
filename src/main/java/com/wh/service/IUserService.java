package com.wh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wh.dto.LoginFormDTO;
import com.wh.dto.Result;
import com.wh.entity.User;

import javax.servlet.http.HttpSession;

/**
 *
 *  服务类
 *
 *

 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    /**
     * 发送邮箱
     * @param to
     * @param subject
     */
    Result sendMsg(String to,String subject);


    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();


    Result signCount();
}
