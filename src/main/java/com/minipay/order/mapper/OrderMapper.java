package com.minipay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按订单号加行锁：串行化"同一订单创建支付意图"，防止并发创建多个进行中意图。
     */
    Order lockByOrderNo(String orderNo);
}
