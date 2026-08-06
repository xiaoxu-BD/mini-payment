package com.minipay.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {

    private String orderNo;
    private Long userId;
    private Long totalAmount;
    private String status;
    private LocalDateTime expiredTime;
    private String cancelType;
    private LocalDateTime cancelTime;
    private LocalDateTime paidTime;
    private String idempotentKey;
}
