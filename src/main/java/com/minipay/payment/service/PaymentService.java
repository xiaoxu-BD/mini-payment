package com.minipay.payment.service;

import com.minipay.common.enums.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.EnumUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.channel.ChannelGateway;
import com.minipay.channel.dto.ChannelCloseRequest;
import com.minipay.channel.dto.ChannelCreateRequest;
import com.minipay.channel.dto.ChannelCreateResult;
import com.minipay.common.api.ResultCode;
import com.minipay.common.exception.BizException;
import com.minipay.common.statemachine.PaymentOrderStateMachine;
import com.minipay.common.statemachine.PaymentStateMachine;
import com.minipay.common.util.BizNoGenerator;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.entity.Order;
import com.minipay.order.mapper.OrderMapper;
import com.minipay.payment.dto.InitiatePaymentRequest;
import com.minipay.payment.dto.IntentCreated;
import com.minipay.payment.dto.PayResponse;
import com.minipay.payment.dto.PaymentQueryResponse;
import com.minipay.payment.entity.Payment;
import com.minipay.payment.entity.PaymentOrder;
import com.minipay.payment.mapper.PaymentMapper;
import com.minipay.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 支付服务：意图创建(D8允许重建)、支付流水、渠道交互、意图关闭。
 * 流程：锁订单行 → 建意图+流水(PAYING) → 事务外调渠道 → 回填渠道单号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final ChannelGateway channelGateway;
    private final OutboxService outboxService;

    @Value("${app.payment.expire-minutes:15}")
    private long expireMinutes;

    /**
     * 发起支付。已有进行中意图则幂等返回（同一时刻至多一个进行中意图）。
     */
    public PayResponse initiatePayment(InitiatePaymentRequest request) {
        //查询是否有进行中的支付意图 如果有则返回
        PaymentOrder active = findActiveByOrderNo(request.getOrderNo());
        if (active != null) {
            Payment payment = findLatestPayment(active.getPaymentOrderNo());
            return PayResponse.builder()
                    .paymentOrderNo(active.getPaymentOrderNo())
                    .paymentNo(payment == null ? null : payment.getPaymentNo())
                    .orderNo(active.getOrderNo())
                    .channel(active.getChannel())
                    .status(active.getStatus())
                    .payUrl(payment == null ? null : payment.getChannelPayUrl())
                    .build();
        }

        IntentCreated created = createIntentTx(request.getOrderNo(), request.getChannel());
        try {
            // 事务外调渠道：网络调用不进事务
            ChannelCreateResult result = channelGateway.createPayment(
                    new ChannelCreateRequest(request.getChannel(),
                            created.payment().getPaymentNo(), created.paymentOrder().getAmount()));
            markPayingTx(created.payment().getPaymentNo(), result.getChannelTransactionNo(), result.getPayUrl());
            return PayResponse.builder()
                    .paymentOrderNo(created.paymentOrder().getPaymentOrderNo())
                    .paymentNo(created.payment().getPaymentNo())
                    .orderNo(created.paymentOrder().getOrderNo())
                    .channel(created.paymentOrder().getChannel())
                    .status(PaymentOrderStatus.PAYING.name())
                    .payUrl(result.getPayUrl())
                    .build();
        } catch (Exception e) {
            log.error("渠道创建支付失败 paymentNo={}, orderNo={}", created.payment().getPaymentNo(),
                    created.paymentOrder().getOrderNo(), e);
            markFailedTx(created.paymentOrder().getPaymentOrderNo(), created.payment().getPaymentNo(), "渠道创建失败");
            throw new BizException(ResultCode.CHANNEL_ERROR, "渠道创建支付失败: " + e.getMessage());
        }
    }

    /**
     * 关闭订单下所有非终态支付意图（订单取消/超时联动，D7）。
     */
    public void closeActivePaymentOrder(String orderNo, CloseType closeType, String operator) {
        // 可关闭状态集由状态机推导，避免手写规则

        //支付单 可以关闭CLOSE 允许转换的规则
        List<String> closableIntents = Arrays.stream(PaymentOrderStatus.values())
                .filter(s -> PaymentOrderStateMachine.canTransition(s, PaymentOrderStatus.CLOSED))
                .map(Enum::name)
                .toList();

        //流水可关闭订单 允许转换的规则
        List<String> closablePayments = Arrays.stream(PaymentStatus.values())
                .filter(s -> PaymentStateMachine.canTransition(s, PaymentStatus.CLOSED))
                .map(Enum::name)
                .toList();
        List<PaymentOrder> actives = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .in(PaymentOrder::getStatus, closableIntents));
        if (CollectionUtils.isEmpty(actives)) {
            return;
        }
        List<String> paymentOrderNos = actives.stream()
                .map(PaymentOrder::getPaymentOrderNo)
                .toList();
        // 关闭前先取在途流水（用于事务外联动关闭渠道，D7）
        List<Payment> inFlight = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .in(Payment::getPaymentOrderNo, paymentOrderNos)
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
        LocalDateTime now = LocalDateTime.now();
        // 批量关闭意图 + 在途流水：状态守卫保证不与支付成功回调竞争（SQL 见 XML）
        paymentOrderMapper.closeIntents(paymentOrderNos, closableIntents, closeType.name(), now);
        paymentMapper.closePayments(paymentOrderNos, closablePayments, now);
        // 事务外逐笔联动关闭渠道（网络调用，不进事务）
        for (Payment payment : inFlight) {
            if (StringUtils.isNotBlank(payment.getChannelTransactionNo())) {
                closeChannelBestEffort(payment);
            }
        }
    }

    public PaymentQueryResponse queryPayment(String paymentNo) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND);
        }
        return PaymentQueryResponse.builder()
                .paymentNo(payment.getPaymentNo())
                .paymentOrderNo(payment.getPaymentOrderNo())
                .orderNo(payment.getOrderNo())
                .channel(payment.getChannel())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .channelTransactionNo(payment.getChannelTransactionNo())
                .successTime(payment.getSuccessTime())
                .failTime(payment.getFailTime())
                .closeTime(payment.getCloseTime())
                .build();
    }

    /**
     * 创建意图事务：锁订单行串行化，防止同一订单并发创建多个进行中意图。
     */
    @Transactional
    public IntentCreated createIntentTx(String orderNo, Channel channel) {
        Order order = orderMapper.lockByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            if (OrderStatus.PAID.name().equals(order.getStatus())) {
                throw new BizException(ResultCode.ORDER_ALREADY_PAID);
            }
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
        if (order.getExpiredTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.ORDER_EXPIRED);
        }
        PaymentOrder success = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.SUCCESS.name()));
        if (success != null) {
            throw new BizException(ResultCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime now = LocalDateTime.now();
        String paymentOrderNo = BizNoGenerator.paymentOrderNo();
        String paymentNo = BizNoGenerator.paymentNo();

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentOrderNo(paymentOrderNo);
        paymentOrder.setOrderNo(orderNo);
        paymentOrder.setUserId(order.getUserId());
        paymentOrder.setChannel(channel.name());
        paymentOrder.setAmount(order.getTotalAmount());
        paymentOrder.setStatus(PaymentOrderStatus.PAYING.name());
        paymentOrder.setRefundedAmount(0L);
        paymentOrder.setExpiredTime(now.plusMinutes(expireMinutes));
        paymentOrderMapper.insert(paymentOrder);

        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setPaymentOrderNo(paymentOrderNo);
        payment.setOrderNo(orderNo);
        payment.setChannel(channel.name());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PAYING.name());
        paymentMapper.insert(payment);

        outboxService.record("payment", MqEventType.PAYMENT_INITIATED, Map.of(
                "paymentOrderNo", paymentOrderNo, "paymentNo", paymentNo, "orderNo", orderNo));
        return new IntentCreated(paymentOrder, payment);
    }

    @Transactional
    public void markPayingTx(String paymentNo, String channelTransactionNo, String payUrl) {
        paymentMapper.markPaying(paymentNo, channelTransactionNo, payUrl);
    }

    @Transactional
    public void markFailedTx(String paymentOrderNo, String paymentNo, String reason) {
        paymentMapper.markPayFailed(paymentNo, LocalDateTime.now(), PaymentStatus.PAYING.name());
        paymentOrderMapper.markFailed(paymentOrderNo,
                List.of(PaymentOrderStatus.CREATED.name(), PaymentOrderStatus.PAYING.name()));
    }

    private void closeChannelBestEffort(Payment payment) {
        try {
            channelGateway.closePayment(new ChannelCloseRequest(
                    com.minipay.common.enums.Channel.valueOf(payment.getChannel()),
                    payment.getChannelTransactionNo()));
        } catch (Exception e) {
            log.error("[告警] 渠道关闭支付失败 paymentNo={}, txn={}，需查单兜底",
                    payment.getPaymentNo(), payment.getChannelTransactionNo(), e);
        }
    }

    private PaymentOrder findActiveByOrderNo(String orderNo) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAYING.name())
                .last("LIMIT 1"));
        return CollectionUtils.isEmpty(list) ? null : list.get(0);
    }

    private Payment findLatestPayment(String paymentOrderNo) {
        List<Payment> list = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentOrderNo, paymentOrderNo)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        return CollectionUtils.isEmpty(list) ? null : list.get(0);
    }

}
