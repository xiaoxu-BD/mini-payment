package com.minipay.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelCloseResult {

    private boolean success;
    private String message;
}
