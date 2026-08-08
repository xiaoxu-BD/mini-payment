package com.minipay.channel.dto;

import com.minipay.common.enums.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelCreateRequest {

    private Channel channel;
    private String paymentNo;
    private Long amount;
}
