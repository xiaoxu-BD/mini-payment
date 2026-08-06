package com.minipay.refund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_refund_log")
public class RefundLog extends BaseEntity {

    private String refundNo;
    private String fromStatus;
    private String toStatus;
    private String source;
    private String operator;
    private String remark;
}
