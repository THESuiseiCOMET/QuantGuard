package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.AccountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("simulation_account")
public class SimulationAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountName;

    private AccountType accountType;

    private BigDecimal initialCash;

    private BigDecimal availableCash;

    private BigDecimal frozenCash;

    private AccountStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
