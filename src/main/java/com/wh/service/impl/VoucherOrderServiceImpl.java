package com.wh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.wh.dto.Result;
import com.wh.entity.VoucherOrder;
import com.wh.mapper.VoucherOrderMapper;
import com.wh.service.ISeckillVoucherService;
import com.wh.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wh.utils.RedisIdWorker;
import com.wh.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *  服务实现类
 *
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //创建阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //在类初始化之后立马执行线程任务
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //开启线程任务
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while(true){
                //获取队列中的订单信息
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }

            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 这已经是线程池中另一个线程了，再从UserHolder中取不到用户
        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock("lock:order:");
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.error("一人只能下一单！");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int res = result.intValue();
        if(res != 0){
            return Result.fail(res == 1 ? "库存不足" : "一人只能下一单");
        }
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //同样不能从UserHolder中取
        Long userId = voucherOrder.getId();
        //一人一单
        Long orderId = redisIdWorker.nextId("order");
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if(count > 0){
            //给用户已经下过单了
            log.error("用户已经买过一次了！");
            return;
        }
        //扣减库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).update();
        //判断库存
        if(!success){
            log.error("库存不足！");
            return;
        }
        //添加订单
        save(voucherOrder);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //判断时间
//        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀还未开始");
//        }
//        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束");
//        }
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:");
//        boolean isLock = lock.tryLock();
//        if(!isLock){
//            return  Result.fail("一个人只能下一单");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
////        synchronized (userId.toString().intern()){
////            //获取代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//    }

    //开启线程任务
//    private class VoucherOrderHandler implements Runnable {
//        private final String queueName = "stream.order";
//        @Override
//        public void run() {
//            while(true){
//                //获取队列中的订单信息
//                try {
//                    //初始化stream
//                    initStream();
//                    //获取队列中的订单信息 XREADGROUP GROUP  g1 c1 COUNNT 1 BLOCK  2000 STREAMS s1
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
//                    );
//                    //判断是否获取信息成功
//                    //如果不成功
//                    if(list == null || list.isEmpty()){
//                        //没有消息，继续下一次循环
//                        continue;
//                    }
//                    //获取成功
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> recordMap = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordMap, new VoucherOrder(), true);
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                    //确认消息
//                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常",e);
//                    handlePendingList();
//                }
//
//            }
//        }
//
//        public void initStream(){
//            Boolean exists = stringRedisTemplate.hasKey(queueName);
//            if (BooleanUtil.isFalse(exists)) {
//                log.info("stream不存在，开始创建stream");
//                // 不存在，需要创建
//                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
//                log.info("stream和group创建完毕");
//                return;
//            }
//            // stream存在，判断group是否存在
//            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(queueName);
//            if(groups.isEmpty()){
//                log.info("group不存在，开始创建group");
//                // group不存在，创建group
//                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
//                log.info("group创建完毕");
//            }
//        }
//
//        private void handlePendingList() {
//            while(true){
//                //获取队列中的订单信息
//                try {
//                    //获取队列中的订单信息 XREADGROUP GROUP  g1 c1 COUNNT 1 BLOCK  2000 STREAMS s1
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1),
//                            StreamOffset.create(queueName, ReadOffset.from("0"))
//                    );
//                    //判断是否获取信息成功
//                    //如果不成功
//                    if(list == null || list.isEmpty()){
//                        //没有消息，结束循环
//                        break;
//                    }
//                    //获取成功
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> recordMap = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordMap, new VoucherOrder(), true);
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                    //确认消息
//                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
//                } catch (Exception e) {
//                    log.error("处理pending_list订单异常",e);
//                }
//
//            }
//        }
//    }
}
