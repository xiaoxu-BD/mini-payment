package com.minipay.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OutboxStatus;
import com.minipay.common.exception.BizException;
import com.minipay.common.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 本地消息表写入：必须在业务事务内调用，与业务数据同库同事务（阶段6由转发器投递MQ）。
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public void record(String service, MqEventType eventType, Object payload) {
        try {
            Outbox outbox = new Outbox();
            outbox.setEventId(BizNoGenerator.eventId());
            outbox.setService(service);
            outbox.setEventType(eventType.name());
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(OutboxStatus.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setNextRetryTime(LocalDateTime.now());
            outboxMapper.insert(outbox);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "事件序列化失败: " + e.getMessage());
        }
    }
}
