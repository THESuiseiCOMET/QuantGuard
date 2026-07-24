package com.chenbo.quantguard.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountType {
    LONG_TERM("LONG_TERM"),
    SHORT_TERM("SHORT_TERM");

    @EnumValue
    private final String value;

    AccountType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
