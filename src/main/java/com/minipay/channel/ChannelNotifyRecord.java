package com.minipay.channel;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_channel_notify_record")
public class ChannelNotifyRecord extends BaseEntity {

    private String dedupKey;
    private String channel;
    private String eventType;
    private String bizType;
    private String bizNo;
    private String rawPayload;
    private String processStatus;
}
