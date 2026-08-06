package com.minipay.channel.mock;

import com.minipay.common.enums.BizType;
import com.minipay.common.enums.NotifyEventType;
import lombok.Data;

@Data
public class MockNotifyRequest {

    private BizType bizType;
    private String bizNo;
    private NotifyEventType eventType;
    private String channelTransactionNo;
    private Long amount;
}
