package com.vanyiung.quantguard.desktop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.util.LinkedHashMap;
import java.util.Map;

public class DesktopEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "quantGuardDesktopRuntime";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("desktop"))) {
            return;
        }

        DesktopDataDirectories directories = DesktopDataDirectories.resolve(
                environment.getProperty("QUANTGUARD_DATA_DIR"),
                environment.getProperty("APPDATA"),
                environment.getProperty("user.home")
        );
        directories.createAndVerify();

        String databasePath = directories.database().toString().replace('\\', '/');
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("quantguard.desktop.root-directory", directories.root().toString());
        properties.put("quantguard.desktop.data-directory", directories.data().toString());
        properties.put("quantguard.desktop.logs-directory", directories.logs().toString());
        properties.put("quantguard.desktop.config-directory", directories.config().toString());
        properties.put("quantguard.desktop.runtime-directory", directories.runtime().toString());
        properties.put("spring.datasource.url", h2Url(databasePath));
        if (environment.getProperty("QUANTGUARD_DESKTOP_FILE_LOGGING_ENABLED", Boolean.class, true)) {
            properties.put("logging.file.name", directories.logs().resolve("quantguard.log").toString());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private String h2Url(String databasePath) {
        return "jdbc:h2:file:" + databasePath
                + ";MODE=MySQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";DEFAULT_NULL_ORDERING=HIGH"
                + ";DB_CLOSE_ON_EXIT=FALSE"
                + ";AUTO_SERVER=FALSE"
                + ";FILE_LOCK=FS"
                + ";LOCK_TIMEOUT=10000";
    }
}
