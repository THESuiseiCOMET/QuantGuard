package com.vanyiung.quantguard.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DesktopDataDirectories(
        Path root,
        Path data,
        Path logs,
        Path config,
        Path runtime,
        Path database
) {

    static DesktopDataDirectories resolve(String override, String appData, String userHome) {
        Path rootDirectory;
        if (hasText(override)) {
            rootDirectory = Path.of(override);
        } else if (hasText(appData)) {
            rootDirectory = Path.of(appData).resolve("QuantGuard");
        } else if (hasText(userHome)) {
            rootDirectory = Path.of(userHome).resolve("QuantGuard");
        } else {
            throw new IllegalStateException(
                    "Desktop data directory cannot be resolved: QUANTGUARD_DATA_DIR, APPDATA, and user.home are unavailable");
        }

        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        return new DesktopDataDirectories(
                normalizedRoot,
                normalizedRoot.resolve("data"),
                normalizedRoot.resolve("logs"),
                normalizedRoot.resolve("config"),
                normalizedRoot.resolve("runtime"),
                normalizedRoot.resolve("data").resolve("quantguard")
        );
    }

    void createAndVerify() {
        try {
            Files.createDirectories(data);
            Files.createDirectories(logs);
            Files.createDirectories(config);
            Files.createDirectories(runtime);
            Path probe = Files.createTempFile(root, ".quantguard-write-", ".tmp");
            Files.delete(probe);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "QuantGuard desktop data directory is not writable: " + root, ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
