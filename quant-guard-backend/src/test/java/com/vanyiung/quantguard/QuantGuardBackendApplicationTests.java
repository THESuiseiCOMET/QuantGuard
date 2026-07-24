package com.vanyiung.quantguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QuantGuardBackendApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        assertDoesNotThrow(() -> Class.forName("com.vanyiung.quantguard.QuantGuardBackendApplication"));
    }
}
