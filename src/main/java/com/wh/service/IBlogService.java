package com.wh.service;

import com.wh.dto.Result;
import com.wh.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 *  服务类
 *
 *

 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
