package com.minipay.channel.mock;

import com.minipay.channel.ChannelNotifyRequest;
import com.minipay.channel.ChannelNotifyService;
import com.minipay.common.api.Result;
import com.minipay.common.enums.Channel;
import com.minipay.common.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * mock 渠道的"异步回调触发器"：测试时手动模拟渠道推送通知（可重复推送验证幂等）。
 */
@RestController
@RequestMapping("/mock-channel")
@RequiredArgsConstructor
public class MockChannelController {

    private final ChannelNotifyService channelNotifyService;

    @PostMapping("/{channel}/notify")
    public Result<Void> triggerNotify(@PathVariable Channel channel,
                                      @RequestBody MockNotifyRequest request) {
        ChannelNotifyRequest notify = new ChannelNotifyRequest();
        notify.setChannel(channel);
        notify.setBizType(request.getBizType());
        notify.setBizNo(request.getBizNo());
        notify.setEventType(request.getEventType());
        notify.setChannelTransactionNo(request.getChannelTransactionNo());
        notify.setAmount(request.getAmount());
        notify.setNotifyId("MOCK" + BizNoGenerator.eventId());
        channelNotifyService.handleNotify(notify);
        return Result.success();
    }
}
