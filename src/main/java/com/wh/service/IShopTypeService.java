package com.wh.service;

import com.wh.dto.Result;
import com.wh.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 *  服务类
 *
 *

 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();

}
