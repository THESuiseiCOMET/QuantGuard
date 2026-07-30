package com.vanyiung.quantguard.desktop;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("desktop")
public class DesktopShutdownCoordinator {

    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public DesktopShutdownCoordinator(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void requestShutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        Thread.ofPlatform()
                .name("quantguard-desktop-shutdown")
                .daemon(false)
                .start(() -> {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        applicationContext.close();
                    }
                });
    }
}
