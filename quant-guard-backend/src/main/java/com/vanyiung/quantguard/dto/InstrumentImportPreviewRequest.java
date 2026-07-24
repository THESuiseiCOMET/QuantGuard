package com.vanyiung.quantguard.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InstrumentImportPreviewRequest(
        @NotEmpty(message = "codes must not be empty")
        List<String> codes
) {
}
