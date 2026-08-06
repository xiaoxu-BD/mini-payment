package com.minipay.channel;

import com.minipay.common.enums.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 渠道网关：系统内唯一与 mock 渠道交互的边界（阶段3业务边界）。
 */
public interface ChannelGateway {

    ChannelCreateResult createPayment(ChannelCreateRequest request);

    ChannelCloseResult closePayment(ChannelCloseRequest request);

    ChannelQueryResult queryPayment(ChannelQueryRequest request);

    ChannelRefundResult createRefund(ChannelRefundRequest request);

    ChannelQueryResult queryRefund(ChannelQueryRequest request);

    @Data
    @AllArgsConstructor
    class ChannelCreateRequest {
        private Channel channel;
        private String paymentNo;
        private Long amount;
    }

    @Data
    @AllArgsConstructor
    class ChannelCreateResult {
        private String channelTransactionNo;
        private String payUrl;
    }

    @Data
    @AllArgsConstructor
    class ChannelCloseRequest {
        private Channel channel;
        private String channelTransactionNo;
    }

    @Data
    @AllArgsConstructor
    class ChannelCloseResult {
        private boolean success;
        private String message;
    }

    @Data
    @AllArgsConstructor
    class ChannelQueryRequest {
        private Channel channel;
        private String channelTransactionNo;
    }

    @Data
    @AllArgsConstructor
    class ChannelQueryResult {
        private String channelTransactionNo;
        /** CREATED / SUCCESS / FAILED / CLOSED */
        private String status;
        private Long amount;
    }

    @Data
    @AllArgsConstructor
    class ChannelRefundRequest {
        private Channel channel;
        private String refundNo;
        private String channelTransactionNo;
        private Long amount;
    }

    @Data
    @AllArgsConstructor
    class ChannelRefundResult {
        private String channelRefundNo;
    }
}
