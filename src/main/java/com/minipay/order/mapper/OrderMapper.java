package com.minipay.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按订单号加行锁：串行化"同一订单创建支付意图"，防止并发创建多个进行中意图。
     */
    Order lockByOrderNo(String orderNo);

    int cancelByOrderNo(@Param("orderNo") String orderNo,
                        @Param("cancelType") String cancelType,
                        @Param("cancelTime") LocalDateTime cancelTime);

    int markPaid(@Param("orderNo") String orderNo,
                 @Param("paidTime") LocalDateTime paidTime,
                 @Param("fromStatus") String fromStatus);

    int markRefunding(@Param("orderNo") String orderNo,
                      @Param("refundable") List<String> refundable);

    int updateStatusFromRefunding(@Param("orderNo") String orderNo,
                                  @Param("targetStatus") String targetStatus);

    int batchCancel(@Param("orderNos") List<String> orderNos,
                    @Param("cancellable") List<String> cancellable,
                    @Param("cancelTime") LocalDateTime cancelTime);
}
