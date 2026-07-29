package com.vanyiung.quantguard.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopDataDirectoriesTest {

    @TempDir
    Path tempDirectory;

    @Test
    void overrideTakesPriorityAndCreatesRequiredDirectories() {
        Path override = tempDirectory.resolve("custom-data");

        DesktopDataDirectories directories = DesktopDataDirectories.resolve(
                override.toString(),
                tempDirectory.resolve("appdata").toString(),
                tempDirectory.resolve("home").toString()
        );
        directories.createAndVerify();

        assertEquals(override.toAbsolutePath().normalize(), directories.root());
        assertTrue(Files.isDirectory(directories.data()));
        assertTrue(Files.isDirectory(directories.logs()));
        assertTrue(Files.isDirectory(directories.config()));
        assertTrue(Files.isDirectory(directories.runtime()));
        assertEquals(directories.data().resolve("quantguard"), directories.database());
    }

    @Test
    void appDataAndUserHomeProvideSafeFallbacks() {
        Path appData = tempDirectory.resolve("appdata");
        Path userHome = tempDirectory.resolve("home");

        DesktopDataDirectories appDataDirectories =
                DesktopDataDirectories.resolve(null, appData.toString(), userHome.toString());
        DesktopDataDirectories userHomeDirectories =
                DesktopDataDirectories.resolve(null, null, userHome.toString());

        assertEquals(appData.resolve("QuantGuard").toAbsolutePath(), appDataDirectories.root());
        assertEquals(userHome.resolve("QuantGuard").toAbsolutePath(), userHomeDirectories.root());
    }

    @Test
    void unavailableOrUnwritableRootFailsClearly() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> DesktopDataDirectories.resolve(null, null, null));

        Path blocked = tempDirectory.resolve("not-a-directory");
        Files.writeString(blocked, "blocked");
        DesktopDataDirectories directories =
                DesktopDataDirectories.resolve(blocked.toString(), null, null);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, directories::createAndVerify);
        assertTrue(exception.getMessage().contains("not writable"));
    }
}
