package com.minipay.channel.mock;

import com.minipay.channel.ChannelGateway;
import com.minipay.common.enums.Channel;
import com.minipay.common.util.BizNoGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mock 渠道：内存模拟微信/支付宝的支付创建、关闭、查询、退款。
 * 回调由 MockChannelController 触发（模拟渠道异步通知）。
 */
@Slf4j
@Component
public class MockChannelGateway implements ChannelGateway {

    private final Map<String, MockTxn> paymentStore = new ConcurrentHashMap<>();
    private final Map<String, MockTxn> refundStore = new ConcurrentHashMap<>();

    @Override
    public ChannelCreateResult createPayment(ChannelCreateRequest request) {
        String txn = "CH" + request.getChannel() + BizNoGenerator.eventId();
        paymentStore.put(txn, new MockTxn(txn, request.getAmount(), "CREATED"));
        String payUrl = "mock://" + request.getChannel().name().toLowerCase() + "/pay?txn=" + txn;
        log.info("[mock渠道] 创建支付 paymentNo={}, txn={}", request.getPaymentNo(), txn);
        return new ChannelCreateResult(txn, payUrl);
    }

    @Override
    public ChannelCloseResult closePayment(ChannelCloseRequest request) {
        MockTxn txn = paymentStore.get(request.getChannelTransactionNo());
        if (txn != null) {
            txn.setStatus("CLOSED");
            log.info("[mock渠道] 关闭支付 txn={}", request.getChannelTransactionNo());
            return new ChannelCloseResult(true, "ok");
        }
        return new ChannelCloseResult(false, "txn not found");
    }

    @Override
    public ChannelQueryResult queryPayment(ChannelQueryRequest request) {
        MockTxn txn = paymentStore.get(request.getChannelTransactionNo());
        if (txn == null) {
            return new ChannelQueryResult(request.getChannelTransactionNo(), "NOT_FOUND", null);
        }
        return new ChannelQueryResult(txn.getTxnNo(), txn.getStatus(), txn.getAmount());
    }

    @Override
    public ChannelRefundResult createRefund(ChannelRefundRequest request) {
        String refundTxn = "RCH" + request.getChannel() + BizNoGenerator.eventId();
        refundStore.put(refundTxn, new MockTxn(refundTxn, request.getAmount(), "CREATED"));
        log.info("[mock渠道] 创建退款 refundNo={}, txn={}", request.getRefundNo(), refundTxn);
        return new ChannelRefundResult(refundTxn);
    }

    @Override
    public ChannelQueryResult queryRefund(ChannelQueryRequest request) {
        MockTxn txn = refundStore.get(request.getChannelTransactionNo());
        if (txn == null) {
            return new ChannelQueryResult(request.getChannelTransactionNo(), "NOT_FOUND", null);
        }
        return new ChannelQueryResult(txn.getTxnNo(), txn.getStatus(), txn.getAmount());
    }

    /**
     * 导出指定渠道的成功支付流水，供对账模块生成"渠道账单"。
     */
    public List<BillRecord> snapshotSuccessfulPayments(Channel channel) {
        String prefix = "CH" + channel;
        return paymentStore.values().stream()
                .filter(t -> "SUCCESS".equals(t.getStatus()))
                .filter(t -> t.getTxnNo().startsWith(prefix))
                .map(t -> new BillRecord(t.getTxnNo(), t.getAmount()))
                .toList();
    }

    public void markPaymentSuccess(String channelTransactionNo) {
        MockTxn txn = paymentStore.get(channelTransactionNo);
        if (txn != null) {
            txn.setStatus("SUCCESS");
        }
    }

    public void markPaymentFail(String channelTransactionNo) {
        MockTxn txn = paymentStore.get(channelTransactionNo);
        if (txn != null) {
            txn.setStatus("FAILED");
        }
    }

    public void markRefundSuccess(String channelRefundNo) {
        MockTxn txn = refundStore.get(channelRefundNo);
        if (txn != null) {
            txn.setStatus("SUCCESS");
        }
    }

    public void markRefundFail(String channelRefundNo) {
        MockTxn txn = refundStore.get(channelRefundNo);
        if (txn != null) {
            txn.setStatus("FAILED");
        }
    }

    @Data
    private static class MockTxn {
        private final String txnNo;
        private final Long amount;
        private volatile String status;

        MockTxn(String txnNo, Long amount, String status) {
            this.txnNo = txnNo;
            this.amount = amount;
            this.status = status;
        }
    }

    public record BillRecord(String channelTransactionNo, Long amount) {
    }
}
