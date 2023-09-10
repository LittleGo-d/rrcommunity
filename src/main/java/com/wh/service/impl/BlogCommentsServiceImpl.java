package com.wh.service.impl;

import com.wh.entity.BlogComments;
import com.wh.mapper.BlogCommentsMapper;
import com.wh.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 *
 *  服务实现类
 *
 *

 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
