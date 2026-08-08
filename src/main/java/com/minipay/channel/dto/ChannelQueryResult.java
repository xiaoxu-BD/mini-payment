package com.minipay.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelQueryResult {

    private String channelTransactionNo;
    /** CREATED / SUCCESS / FAILED / CLOSED */
    private String status;
    private Long amount;
}
