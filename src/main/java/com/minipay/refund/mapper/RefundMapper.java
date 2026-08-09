package com.minipay.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.refund.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    int markProcessing(@Param("refundNo") String refundNo,
                       @Param("channelRefundNo") String channelRefundNo);

    int markCreatedForRetry(@Param("refundNo") String refundNo,
                            @Param("retryCount") int retryCount,
                            @Param("operator") String operator);

    int markRefundSuccess(@Param("refundNo") String refundNo,
                          @Param("successTime") LocalDateTime successTime,
                          @Param("channelRefundNo") String channelRefundNo,
                          @Param("fromStatus") String fromStatus);

    int markRefundFailed(@Param("refundNo") String refundNo,
                         @Param("failTime") LocalDateTime failTime,
                         @Param("fromStatus") String fromStatus);

    int markFailed(@Param("refundNo") String refundNo,
                   @Param("failTime") LocalDateTime failTime,
                   @Param("fromStatuses") List<String> fromStatuses);
}
