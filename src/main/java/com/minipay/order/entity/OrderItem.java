package com.minipay.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order_item")
public class OrderItem extends BaseEntity {

    private String orderNo;
    private String itemNo;
    private Long productId;
    private String productName;
    private Long unitPrice;
    private Integer quantity;
    private Long amount;
}
