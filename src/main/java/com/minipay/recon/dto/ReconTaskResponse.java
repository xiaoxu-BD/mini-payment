package com.minipay.recon.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ReconTaskResponse {

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
