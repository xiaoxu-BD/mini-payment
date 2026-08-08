package com.minipay.channel;

import com.minipay.channel.dto.ChannelCloseRequest;
import com.minipay.channel.dto.ChannelCloseResult;
import com.minipay.channel.dto.ChannelCreateRequest;
import com.minipay.channel.dto.ChannelCreateResult;
import com.minipay.channel.dto.ChannelQueryRequest;
import com.minipay.channel.dto.ChannelQueryResult;
import com.minipay.channel.dto.ChannelRefundRequest;
import com.minipay.channel.dto.ChannelRefundResult;

/**
 * 渠道网关：系统内唯一与 mock 渠道交互的边界（阶段3业务边界）。
 */
public interface ChannelGateway {

    ChannelCreateResult createPayment(ChannelCreateRequest request);

    ChannelCloseResult closePayment(ChannelCloseRequest request);

    ChannelQueryResult queryPayment(ChannelQueryRequest request);

    ChannelRefundResult createRefund(ChannelRefundRequest request);

    ChannelQueryResult queryRefund(ChannelQueryRequest request);
}
