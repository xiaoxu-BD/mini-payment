package com.minipay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 批量插入明细：MyBatis foreach，避免循环内单条 insert。
     */
    int insertList(@Param("items") List<OrderItem> items);
}
