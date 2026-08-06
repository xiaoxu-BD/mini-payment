package com.minipay.recon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_recon_task")
public class ReconTask extends BaseEntity {

    private String taskNo;
    private String channel;
    private LocalDate billDate;
    private String billFile;
    private Integer totalCount;
    private Integer matchedCount;
    private Integer diffCount;
    private String status;
    private LocalDateTime finishedAt;
}
