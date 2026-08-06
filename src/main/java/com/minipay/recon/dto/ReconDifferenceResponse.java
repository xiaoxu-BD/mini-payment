package com.minipay.recon.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ReconDifferenceResponse {

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
