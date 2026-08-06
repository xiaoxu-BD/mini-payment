package com.minipay.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_difference")
public class ReconDifference extends BaseEntity {

    private String differenceNo;
    private Long taskId;
    private String channel;
    private LocalDate billDate;
    private String diffType;
    private String channelTransactionNo;
    private String paymentNo;
    private Long channelAmount;
    private Long systemAmount;
    private String status;
    private String operator;
    private LocalDateTime handleTime;
    private String remark;
}
