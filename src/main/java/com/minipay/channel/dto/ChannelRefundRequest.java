package com.minipay.channel.dto;

import com.minipay.common.enums.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelRefundRequest {

    private Channel channel;
    private String refundNo;
    private String channelTransactionNo;
    private Long amount;
}
