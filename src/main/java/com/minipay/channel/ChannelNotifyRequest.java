package com.minipay.channel;

import com.minipay.common.enums.BizType;
import com.minipay.common.enums.Channel;
import com.minipay.common.enums.NotifyEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChannelNotifyRequest {

    @NotNull(message = "渠道不能为空")
    private Channel channel;

    @NotNull(message = "业务类型不能为空")
    private BizType bizType;

    @NotBlank(message = "业务号不能为空")
    private String bizNo;

    @NotNull(message = "事件类型不能为空")
    private NotifyEventType eventType;

    /** 渠道通知号：渠道每次推送唯一，重复推送同一通知号即重复回调 */
    private String notifyId;

    private String channelTransactionNo;

    private Long amount;
}
