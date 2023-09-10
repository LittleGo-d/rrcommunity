package com.wh.service;

import com.wh.dto.Result;
import com.wh.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 *  服务类
 *
 *

 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

}
