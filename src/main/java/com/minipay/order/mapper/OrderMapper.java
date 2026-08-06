package com.minipay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按订单号加行锁：串行化"同一订单创建支付意图"，防止并发创建多个进行中意图。
     */
    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo} FOR UPDATE")
    Order lockByOrderNo(String orderNo);
}
