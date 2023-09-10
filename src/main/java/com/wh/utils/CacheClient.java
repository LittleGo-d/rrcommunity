package com.wh.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wh.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.wh.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    //定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //逻辑过期解决缓存击穿
    public <R,ID> R queryByIdLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        //查询redis
        String shopJson= stringRedisTemplate.opsForValue().get(key);
        //查询到了，返回
        R r1 = JSONUtil.toBean(shopJson, type);
        if(StrUtil.isBlank(shopJson)){
            return null;
        }

        //命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //返回店铺信息
            return r1;
        }
        //过期
        //重建缓存
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R newR = dbFallBack.apply(id);
                    //重建缓存
                    this.setLogicalExpire(key,newR,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException();
                }finally {
                    //释放锁
                    deleteKey(lockKey);
                }
            });
        }
        //返回
        return r;
    }

    private <R> void setLogicalExpire(String key, R newR, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(newR);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //解决缓存穿透
    public <R,ID> R queryByIdPassThrough(String keyPrefix ,ID id,Class<R> type,Long time,TimeUnit unit,Function<ID,R> dpFallBack) {
        String key = keyPrefix + id;
        //查询redis
        String shopJson= stringRedisTemplate.opsForValue().get(key);
        //查询到了，返回
        R r1 = JSONUtil.toBean(shopJson, type);
        if(StrUtil.isNotBlank(shopJson)){
            return r1;
        }
        //判断是否是null
        if (shopJson != null) {
            return null;
        }
        //没查询到，查询数据库
        R r = dpFallBack.apply(id);
        //没查到，返回错误
        if(r == null){
            //缓存空值
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, unit);
            return null;
        }
        //查到了，添加到redis
        this.set(key,JSONUtil.toJsonStr(r),time, unit);
        //返回
        return r;
    }

    //互斥锁解决缓存击穿
    public <R,ID> R queryByIdMutex(String keyPrefix,ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        //查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(keyPrefix);
        //查询到了，返回
        R r1 = JSONUtil.toBean(shopJson, type);
        if (StrUtil.isNotBlank(shopJson)) {
            return r1;
        }
        R r = null;
        try {
            //模拟重建的延时
            Thread.sleep(200);
            //判断是否是null
            if (shopJson != null) {
                return null;
            }
            //缓存重建
            //获取锁
            boolean tryLock = tryLock(LOCK_SHOP_KEY + id);
            //判断是否为空
            if (!tryLock) {
                //失败，休眠一段时间重试
                Thread.sleep(50);
                return queryByIdMutex(keyPrefix,id,type,dbFallBack,time,unit);
            }
            //没查询到，查询数据库
            r = dbFallBack.apply(id);
            if (r == null) {
                //缓存空值
                stringRedisTemplate.opsForValue().set(keyPrefix, "", time, unit);
                return null;
            }
            //查到了，添加到redis
            this.set(key,r,time,unit);

        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            //释放锁
            deleteKey(LOCK_SHOP_KEY + id);
        }
        //返回
        return r;
    }

    //获取锁
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //删除锁
    public void deleteKey(String key){
        stringRedisTemplate.delete(key);
    }
}
