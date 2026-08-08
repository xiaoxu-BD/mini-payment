package com.minipay.channel.dto;

import com.minipay.common.enums.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelQueryRequest {

    private Channel channel;
    private String channelTransactionNo;
}
