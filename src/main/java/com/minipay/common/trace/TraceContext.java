package com.minipay.common.trace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文：traceId 贯穿 HTTP 请求 → outbox → MQ 消费 → 异步通知。
 */
public final class TraceContext {

    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static String getOrCreate() {
        String traceId = MDC.get(TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID, traceId);
        }
        return traceId;
    }

    public static void set(String traceId) {
        MDC.put(TRACE_ID, StringUtils.isNotBlank(traceId) ? traceId : getOrCreate());
    }

    public static String get() {
        return MDC.get(TRACE_ID);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
