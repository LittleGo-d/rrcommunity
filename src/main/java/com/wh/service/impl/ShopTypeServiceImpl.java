package com.wh.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wh.dto.Result;
import com.wh.entity.Shop;
import com.wh.entity.ShopType;
import com.wh.mapper.ShopTypeMapper;
import com.wh.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wh.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.wh.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 *
 *  服务实现类
 *
 *

 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //先在redis中查询
        String typeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //判断是否为空
        if (StrUtil.isNotBlank(typeJson)) {
            return Result.ok(JSONUtil.toList(typeJson,ShopType.class));
        }
        //没查询到
        List<ShopType> shopList = list();
        //保存到redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopList));
        return Result.ok(shopList);
    }
}
