package com.vanyiung.quantguard.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMarketDataProviderConfigRequest(
        @JsonAlias({"tushareToken", "tushare_token"})
        @NotBlank(message = "token is required")
        @Size(max = 256, message = "token must be at most 256 characters")
        String token
) {
}
