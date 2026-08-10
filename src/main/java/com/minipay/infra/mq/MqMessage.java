package com.minipay.infra.mq;

/**
 * MQ 消息信封：eventId 幂等键 + eventType 路由标签 + payload 业务负载 + traceId 链路追踪。
 */
public record MqMessage(String eventId, String eventType, String payload, String traceId) {
}
