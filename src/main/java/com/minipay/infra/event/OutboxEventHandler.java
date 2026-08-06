package com.minipay.infra.event;

/**
 * outbox 事件处理器：本地转发器直接调用（阶段6接入RocketMQ后，由MQ消费者调用同一批处理器）。
 */
public interface OutboxEventHandler {

    String eventType();

    void handle(String payloadJson) throws Exception;
}
