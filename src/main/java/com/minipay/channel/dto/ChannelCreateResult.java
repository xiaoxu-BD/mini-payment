package com.minipay.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelCreateResult {

    private String channelTransactionNo;
    private String payUrl;
}
