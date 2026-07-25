package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @NotNull(message = "status must not be null")
        AccountStatus status
) {
}
