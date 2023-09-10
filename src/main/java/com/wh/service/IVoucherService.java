package com.wh.service;

import com.wh.dto.Result;
import com.wh.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 *
 *  服务类
 *
 *

 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
