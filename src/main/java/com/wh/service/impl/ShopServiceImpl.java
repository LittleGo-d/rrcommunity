package com.wh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wh.dto.Result;
import com.wh.entity.Shop;
import com.wh.mapper.ShopMapper;
import com.wh.mysqlReadWrite.annotation.Master;
import com.wh.mysqlReadWrite.annotation.Slave;
import com.wh.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wh.utils.CacheClient;
import com.wh.utils.RedisConstants;
import com.wh.utils.RedisData;
import com.wh.utils.SystemConstants;
import org.aspectj.lang.JoinPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.wh.utils.RedisConstants.*;

/**
 *
 *  服务实现类
 *
 *

 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    @Slave
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryByIdPassThrough(id);
        Shop shop = cacheClient.queryByIdPassThrough(CACHE_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);
        //互斥锁解决缓存击穿
//        Shop shop = queryByIdMutex(id);
//        cacheClient.queryByIdMutex(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //逻辑过期解决缓存击穿
//        Shop shop = queryByIdLogicExpire(id);
//        Shop shop = cacheClient.queryByIdLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    public Shop queryByIdLogicExpire(Long id){
        //查询redis
        String shopJson= stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //查询到了，返回
        Shop shop1 = JSONUtil.toBean(shopJson, Shop.class);
        if(StrUtil.isBlank(shopJson)){
            return null;
        }

        //命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);
        if(expireTime.isAfter(LocalDateTime.now())){
            //返回店铺信息
            return shop;
        }
        //过期
        //重建缓存
        //获取互斥锁
        String key = CACHE_SHOP_KEY + id;
        boolean isLock = tryLock(key);
        if (isLock) {
            //成功，开启独立线程，实现缓存重建
            CACHE_BUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException();
                }finally {
                    //释放锁
                    deleteKey(key);
                }
            });
        }
        //返回
        return shop;
    }

    //将商铺数据存到redis
    public void saveShop2Redis(Long id,Long expireTime) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //解决缓存击穿
//    public Shop queryByIdMutex(Long id){
//        //查询redis
//        String shopJson= stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        //查询到了，返回
//        Shop shop1 = JSONUtil.toBean(shopJson, Shop.class);
//        if(StrUtil.isNotBlank(shopJson)){
//            return shop1;
//        }
//        Shop shop = null;
//        try {
//            //模拟重建的延时
//            Thread.sleep(200);
//            //判断是否是null
//            if (shopJson != null) {
//                return null;
//            }
//            //缓存重建
//            //获取锁
//            boolean tryLock = tryLock(LOCK_SHOP_KEY + id);
//            //判断是否为空
//            if (tryLock == false){
//                //失败，休眠一段时间重试
//                Thread.sleep(50);
//                return queryByIdMutex(id);
//            }
//            //没查询到，查询数据库
//            shop = getById(id);
//            if(shop == null){
//                //缓存空值
//                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //查到了，添加到redis
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        } catch (InterruptedException e) {
//            throw new RuntimeException();
//        }finally {
//            //释放锁
//            deleteKey(LOCK_SHOP_KEY + id);
//        }
//        //返回
//        return shop;
//    }

    //获取锁
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //删除锁
    public void deleteKey(String key){
        stringRedisTemplate.delete(key);
    }

    //解决缓存穿透
    public Shop queryByIdPassThrough(Long id){
        //查询redis
        String shopJson= stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //查询到了，返回
        Shop shop1 = JSONUtil.toBean(shopJson, Shop.class);
        if(StrUtil.isNotBlank(shopJson)){
            return shop1;
        }
        //判断是否是null
        if (shopJson != null) {
            return null;
        }
        //没查询到，查询数据库
        Shop shop = getById(id);
        //没查到，返回错误
        if(shop == null){
            //缓存空值
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //查到了，添加到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }
    @Override
    @Transactional
    @Master
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if(shopId == null){
            return Result.fail("商铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        return Result.ok();
    }

    @Override
    @Slave
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
