package com.minipay.channel;

import com.minipay.common.api.Result;
import com.minipay.common.enums.Channel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道回调入口：重复回调在此幂等消化，始终返回成功（阶段4场景1）。
 */
@RestController
@RequestMapping("/api/channel/notify")
@RequiredArgsConstructor
public class ChannelNotifyController {

    private final ChannelNotifyService channelNotifyService;

    @PostMapping("/{channel}")
    public Result<Void> notify(@PathVariable Channel channel,
                               @Valid @RequestBody ChannelNotifyRequest request) {
        request.setChannel(channel);
        channelNotifyService.handleNotify(request);
        return Result.success();
    }
}
