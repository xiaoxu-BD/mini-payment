package com.minipay.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OutboxStatus;
import com.minipay.common.exception.BizException;
import com.minipay.common.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            outbox.setId(BizNoGenerator.id());
            outbox.setEventId(BizNoGenerator.eventId());
            outbox.setService(service);
            outbox.setEventType(eventType.name());
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(OutboxStatus.PENDING.name());
            outbox.setRetryCount(0);
            outbox.setVersion(0);
            outbox.setNextRetryTime(LocalDateTime.now());
            outbox.setCreatedAt(LocalDateTime.now());
            outbox.setUpdatedAt(LocalDateTime.now());
            outboxMapper.insert(outbox);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "事件序列化失败: " + e.getMessage());
        }
    }

    /**
     * 批量写入本地消息：同事务内调用，避免循环内单条 insert。
     */
    public void recordBatch(String service, MqEventType eventType, List<Object> payloads) {
        if (CollectionUtils.isEmpty(payloads)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Outbox> list = new ArrayList<>(payloads.size());
        try {
            for (Object payload : payloads) {
                Outbox outbox = new Outbox();
                outbox.setId(BizNoGenerator.id());
                outbox.setEventId(BizNoGenerator.eventId());
                outbox.setService(service);
                outbox.setEventType(eventType.name());
                outbox.setPayload(objectMapper.writeValueAsString(payload));
                outbox.setStatus(OutboxStatus.PENDING.name());
                outbox.setRetryCount(0);
                outbox.setVersion(0);
                outbox.setNextRetryTime(now);
                outbox.setCreatedAt(now);
                outbox.setUpdatedAt(now);
                list.add(outbox);
            }
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "事件序列化失败: " + e.getMessage());
        }
        outboxMapper.insertBatch(list);
    }
}
