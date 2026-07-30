package com.vanyiung.quantguard.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopCorsConfigurationTest {

    @Test
    void allowsDesktopAndLoopbackDevelopmentOrigins() {
        CorsConfiguration configuration = desktopCorsConfiguration();

        assertThat(configuration.checkOrigin("tauri://localhost")).isEqualTo("tauri://localhost");
        assertThat(configuration.checkOrigin("http://tauri.localhost"))
                .isEqualTo("http://tauri.localhost");
        assertThat(configuration.checkOrigin("http://127.0.0.1:5173"))
                .isEqualTo("http://127.0.0.1:5173");
        assertThat(configuration.checkOrigin("http://localhost:4173"))
                .isEqualTo("http://localhost:4173");
    }

    @Test
    void rejectsNonLoopbackWebOrigin() {
        assertThat(desktopCorsConfiguration().checkOrigin("https://example.com")).isNull();
    }

    private CorsConfiguration desktopCorsConfiguration() {
        TestCorsRegistry registry = new TestCorsRegistry();
        new DesktopCorsConfiguration().addCorsMappings(registry);
        return registry.configurations().get("/api/**");
    }

    private static final class TestCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
