package com.wh.service.impl;

import com.wh.dto.Result;
import com.wh.entity.SeckillVoucher;
import com.wh.mapper.SeckillVoucherMapper;
import com.wh.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wh.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 *
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 *
 *
 * @author wh
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {


}
