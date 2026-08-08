package com.minipay.common.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 业务号生成：前缀 + 雪花ID，保证全局唯一且可读。
 */
public final class BizNoGenerator {

    private BizNoGenerator() {
    }

    public static String orderNo() {
        return "O" + IdWorker.getIdStr();
    }

    public static String paymentOrderNo() {
        return "PO" + IdWorker.getIdStr();
    }

    public static String paymentNo() {
        return "P" + IdWorker.getIdStr();
    }

    public static String refundNo() {
        return "R" + IdWorker.getIdStr();
    }

    public static String taskNo() {
        return "T" + IdWorker.getIdStr();
    }

    public static String differenceNo() {
        return "D" + IdWorker.getIdStr();
    }

    public static String eventId() {
        return IdWorker.getIdStr();
    }

    public static Long id() {
        return IdWorker.getId();
    }
}
