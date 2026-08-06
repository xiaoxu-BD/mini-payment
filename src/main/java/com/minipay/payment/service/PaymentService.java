package com.minipay.payment.service;

import org.apache.commons.collections4.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.minipay.channel.ChannelGateway;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.CloseType;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.enums.PaymentOrderStatus;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.common.exception.BizException;
import com.minipay.common.util.BizNoGenerator;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.entity.Order;
import com.minipay.order.mapper.OrderMapper;
import com.minipay.payment.dto.InitiatePaymentRequest;
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
            ChannelGateway.ChannelCreateResult result = channelGateway.createPayment(
                    new ChannelGateway.ChannelCreateRequest(request.getChannel(),
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
        List<PaymentOrder> actives = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .in(PaymentOrder::getStatus, PaymentOrderStatus.CREATED.name(),
                        PaymentOrderStatus.PAYING.name(), PaymentOrderStatus.FAILED.name()));
        for (PaymentOrder paymentOrder : actives) {
            Payment inFlight = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getPaymentOrderNo, paymentOrder.getPaymentOrderNo())
                    .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
            boolean closed = markClosedTx(paymentOrder.getPaymentOrderNo(), closeType);
            if (closed && inFlight != null && inFlight.getChannelTransactionNo() != null) {
                closeChannelBestEffort(paymentOrder, inFlight);
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
    public IntentCreated createIntentTx(String orderNo, com.minipay.common.enums.Channel channel) {
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
        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getChannelTransactionNo, channelTransactionNo)
                .set(Payment::getChannelPayUrl, payUrl)
                .eq(Payment::getPaymentNo, paymentNo)
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
    }

    @Transactional
    public void markFailedTx(String paymentOrderNo, String paymentNo, String reason) {
        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, PaymentStatus.FAILED.name())
                .set(Payment::getFailTime, LocalDateTime.now())
                .eq(Payment::getPaymentNo, paymentNo)
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrderStatus.FAILED.name())
                .eq(PaymentOrder::getPaymentOrderNo, paymentOrderNo)
                .in(PaymentOrder::getStatus, PaymentOrderStatus.CREATED.name(), PaymentOrderStatus.PAYING.name()));
    }

    @Transactional
    public boolean markClosedTx(String paymentOrderNo, CloseType closeType) {
        LocalDateTime now = LocalDateTime.now();
        int rows = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrderStatus.CLOSED.name())
                .set(PaymentOrder::getCloseType, closeType.name())
                .set(PaymentOrder::getCloseTime, now)
                .eq(PaymentOrder::getPaymentOrderNo, paymentOrderNo)
                .in(PaymentOrder::getStatus, PaymentOrderStatus.CREATED.name(),
                        PaymentOrderStatus.PAYING.name(), PaymentOrderStatus.FAILED.name()));
        if (rows == 0) {
            return false;
        }
        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, PaymentStatus.CLOSED.name())
                .set(Payment::getCloseTime, now)
                .eq(Payment::getPaymentOrderNo, paymentOrderNo)
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
        return true;
    }

    private void closeChannelBestEffort(PaymentOrder paymentOrder, Payment payment) {
        try {
            channelGateway.closePayment(new ChannelGateway.ChannelCloseRequest(
                    com.minipay.common.enums.Channel.valueOf(payment.getChannel()),
                    payment.getChannelTransactionNo()));
        } catch (Exception e) {
            log.error("[告警] 渠道关闭支付失败 paymentOrderNo={}, txn={}，需查单兜底",
                    paymentOrder.getPaymentOrderNo(), payment.getChannelTransactionNo(), e);
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

    public record IntentCreated(PaymentOrder paymentOrder, Payment payment) {
    }
}
