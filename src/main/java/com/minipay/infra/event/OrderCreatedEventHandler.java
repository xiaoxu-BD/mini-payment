package com.minipay.infra.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.enums.MqEventType;
import com.minipay.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单创建事件：演示 outbox 解耦；支付发起由支付接口按渠道显式触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String eventType() {
        return MqEventType.ORDER_CREATED.name();
    }

    @Override
    public void handle(String eventType, String payloadJson) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(payloadJson, OrderCreatedEvent.class);
        log.info("收到订单创建事件 eventType={}, orderNo={}, amount={}",
                eventType, event.getOrderNo(), event.getTotalAmount());
    }
}
