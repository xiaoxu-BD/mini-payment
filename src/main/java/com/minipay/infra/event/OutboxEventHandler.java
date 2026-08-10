package com.minipay.infra.event;

import java.util.Collection;
import java.util.List;

/**
 * outbox 事件处理器：本地转发器直接调用（阶段6接入RocketMQ后，由MQ消费者调用同一批处理器）。
 */
public interface OutboxEventHandler {

    String eventType();

    /** 支持的事件类型集合：允许一个处理器消费多个事件（如邮件通知）。 */
    default Collection<String> eventTypes() {
        return List.of(eventType());
    }

    void handle(String eventType, String payloadJson) throws Exception;
}
