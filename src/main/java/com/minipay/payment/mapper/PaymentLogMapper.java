package com.minipay.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.payment.entity.PaymentLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentLogMapper extends BaseMapper<PaymentLog> {
}
