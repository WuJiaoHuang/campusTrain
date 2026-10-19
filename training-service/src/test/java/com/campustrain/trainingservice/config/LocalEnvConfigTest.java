package com.campustrain.trainingservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LocalEnvConfigTest {
    @TempDir
    Path directory;

    private StandardEnvironment load(Path file, Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testOverrides", overrides));
        environment.getPropertySources().addLast(new MapPropertySource("testImport", Map.of(
                "spring.config.import", "optional:" + file.toUri() + "[.properties]",
                "spring.config.location", "optional:" + directory.resolve("missing.properties").toUri())));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test
    void loadsExtensionlessPropertiesAndResolvesPlaceholders() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "LOCAL_OSS_SECRET=file-value\nLOCAL_MYSQL_PASSWORD=file-password\n");
        StandardEnvironment environment = load(file, Map.of());
        assertEquals("file-value", environment.resolvePlaceholders("${LOCAL_OSS_SECRET:missing}"));
        assertEquals("file-password", environment.resolvePlaceholders("${LOCAL_MYSQL_PASSWORD:missing}"));
    }

    @Test
    void higherPriorityEnvironmentOverridesFile() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "LOCAL_OSS_SECRET=file-value\n");
        assertEquals("environment-value", load(file, Map.of("LOCAL_OSS_SECRET", "environment-value"))
                .getProperty("LOCAL_OSS_SECRET"));
    }

    @Test
    void missingOptionalFileDoesNotFail() {
        assertNull(load(directory.resolve(".env"), Map.of()).getProperty("LOCAL_OSS_SECRET"));
    }

    @Test
    void importLocationsAreResolvedBeforeLocalFileValues() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "LOCAL_CONFIG_STAGE=selected\n");
        Files.writeString(directory.resolve("selected.properties"), "local.test.selection=selected\n");
        Files.writeString(directory.resolve("default.properties"), "local.test.selection=default\n");
        Path application = directory.resolve("application.properties");
        Files.writeString(application, "spring.config.import=optional:" + file.toUri()
                + "[.properties],optional:file:" + directory.toUri().getPath()
                + "${LOCAL_CONFIG_STAGE:default}.properties\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testLocation", Map.of(
                "spring.config.location", application.toUri().toString())));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertEquals("default", environment.getProperty("local.test.selection"));
    }
}
