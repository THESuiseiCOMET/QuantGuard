package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vanyiung.quantguard.enums.MarketDataSyncStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("market_data_sync_log")
public class MarketDataSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instrumentId;

    private String provider;

    private String syncType;

    private LocalDate requestedStartDate;

    private LocalDate requestedEndDate;

    private Integer recordsReceived;

    private Integer recordsInserted;

    private Integer recordsUpdated;

    private MarketDataSyncStatus status;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
