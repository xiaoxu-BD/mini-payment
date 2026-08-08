package com.minipay.infra.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {

    /**
     * 批量插入本地消息：MyBatis foreach。
     */
    int insertBatch(@Param("items") List<Outbox> items);
}
