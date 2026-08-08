package com.minipay.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private String adminAddresses;
    private String accessToken = "";
    private String appname;
    private String address;
    private String ip;
    private int port = 9998;
    private String logPath = "./logs/xxl-job/jobhandler/";
    private int logRetentionDays = 30;
}
