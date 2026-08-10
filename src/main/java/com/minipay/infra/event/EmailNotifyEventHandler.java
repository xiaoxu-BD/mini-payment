package com.minipay.infra.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.enums.MqEventType;
import com.minipay.infra.notify.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 用户通知处理器（模拟真实消费端动作）：
 * 消费到 支付成功 / 退款成功 / 退款失败 / 订单取消 事件后，通过 163 邮箱发送通知邮件。
 * 邮件发送为异步、尽力而为，失败不影响业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotifyEventHandler implements OutboxEventHandler {

    private static final List<String> EVENTS = List.of(
            MqEventType.PAYMENT_SUCCEEDED.name(),
            MqEventType.REFUND_SUCCEEDED.name(),
            MqEventType.REFUND_FAILED.name(),
            MqEventType.ORDER_CANCELLED.name()
    );

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${app.notify.email.enabled:false}")
    private boolean enabled;

    @Override
    public String eventType() {
        return MqEventType.PAYMENT_SUCCEEDED.name();
    }

    @Override
    public Collection<String> eventTypes() {
        return EVENTS;
    }

    @Override
    public void handle(String eventType, String payloadJson) throws Exception {
        if (!enabled) {
            return;
        }
        JsonNode node = objectMapper.readTree(payloadJson);
        String orderNo = node.path("orderNo").asText();
        long amountFen = node.path("amount").asLong(0);
        String amountYuan = String.format("%.2f", amountFen / 100.0);

        String subject;
        String content;
        switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> {
                subject = "【MiniPay】订单支付成功通知";
                content = "您的订单 " + orderNo + " 已支付成功，支付金额：" + amountYuan + " 元。感谢您的购买！";
            }
            case "REFUND_SUCCEEDED" -> {
                subject = "【MiniPay】退款成功通知";
                content = "您的订单 " + orderNo + " 已退款成功，退款金额：" + amountYuan + " 元，请留意到账。";
            }
            case "REFUND_FAILED" -> {
                subject = "【MiniPay】退款失败通知";
                content = "您的订单 " + orderNo + " 退款失败（金额：" + amountYuan
                        + " 元），请稍后重试或联系客服。";
            }
            case "ORDER_CANCELLED" -> {
                subject = "【MiniPay】订单取消通知";
                content = "您的订单 " + orderNo + " 已取消。如有疑问请联系客服。";
            }
            default -> {
                log.debug("无需邮件通知的事件 eventType={}", eventType);
                return;
            }
        }
        emailService.sendNotify(subject, content);
        log.info("已投递用户通知邮件 eventType={}, orderNo={}", eventType, orderNo);
    }
}
