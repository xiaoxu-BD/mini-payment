package com.minipay.infra.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.EnumUtils;
import com.minipay.common.enums.CloseType;
import com.minipay.common.enums.MqEventType;
import com.minipay.order.event.OrderCancelledEvent;
import com.minipay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单取消事件 → 关闭支付意图 + 联动关闭渠道支付（D7）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler implements OutboxEventHandler {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Override
    public String eventType() {
        return MqEventType.ORDER_CANCELLED.name();
    }

    @Override
    public void handle(String eventType, String payloadJson) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payloadJson, OrderCancelledEvent.class);
        log.info("处理订单取消事件 orderNo={}", event.getOrderNo());
        CloseType closeType = EnumUtils.getEnum(CloseType.class, event.getCancelType());
        if (closeType == null) {
            log.warn("订单取消事件类型非法，跳过关闭支付 orderNo={}, cancelType={}",
                    event.getOrderNo(), event.getCancelType());
            return;
        }
        paymentService.closeActivePaymentOrder(event.getOrderNo(), closeType, "ORDER_CANCELLED_EVENT");
    }
}
