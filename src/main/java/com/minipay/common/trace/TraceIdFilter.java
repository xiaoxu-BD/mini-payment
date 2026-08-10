package com.minipay.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪过滤器：请求头 X-Trace-Id 透传（无则生成），写入 MDC 与响应头，请求结束清理。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TraceContext.set(request.getHeader(TRACE_ID_HEADER));
        response.setHeader(TRACE_ID_HEADER, TraceContext.get());
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
