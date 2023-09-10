package com.wh.service;

import com.wh.dto.Result;
import com.wh.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 *  服务类
 *
 *

 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
