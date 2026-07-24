package com.chenbo.quantguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QuantGuardBackendApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        assertDoesNotThrow(() -> Class.forName("com.chenbo.quantguard.QuantGuardBackendApplication"));
    }
}
