package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.enums.InstrumentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInstrumentStatusRequest(
        @NotNull InstrumentStatus status
) {
}
