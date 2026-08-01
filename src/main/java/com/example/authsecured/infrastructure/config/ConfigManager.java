package com.example.authsecured.infrastructure.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {

    private final File dataFolder;
    private final Logger logger;
    private final LocalizationManager localizationManager;
    private Map<String, Object> configMap;

    public ConfigManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.localizationManager = new LocalizationManager(dataFolder, logger);
    }

    @SuppressWarnings("unchecked")
    public void loadConfigurations() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        saveDefaultIfNotExists(configFile, "config.yml");

        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(configFile)) {
            configMap = yaml.load(is);
        } catch (Exception e) {
            logger.severe("Failed to load config.yml: " + e.getMessage());
            configMap = Collections.emptyMap();
        }

        String language = getString("language", "en");
        localizationManager.init(language);
    }

    private void saveDefaultIfNotExists(File targetFile, String resourceName) {
        if (!targetFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (in != null) {
                    Files.copy(in, targetFile.toPath());
                } else {
                    targetFile.createNewFile();
                }
            } catch (Exception e) {
                logger.warning("Could not create default file: " + resourceName);
            }
        }
    }

    public String getString(String path, String defaultValue) {
        Object val = getNestedValue(configMap, path);
        if (val instanceof String str) {
            return resolveEnvVars(str);
        }
        return defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        Object val = getNestedValue(configMap, path);
        if (val instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object val = getNestedValue(configMap, path);
        if (val instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path, List<String> defaultValue) {
        Object val = getNestedValue(configMap, path);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return defaultValue;
    }

    public String getMessage(String path, String defaultValue) {
        return localizationManager.getMessage(path, defaultValue);
    }

    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        return localizationManager.getMessage(path, defaultValue, placeholders);
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object obj = current.get(parts[i]);
            if (obj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) obj;
                current = castMap;
            } else {
                return null;
            }
        }
        return current.get(parts[parts.length - 1]);
    }

    private String resolveEnvVars(String value) {
        if (value == null) return null;
        if (value.startsWith("${ENV:") && value.endsWith("}")) {
            String envVarName = value.substring(6, value.length() - 1);
            String envValue = System.getenv(envVarName);
            return envValue != null ? envValue : "";
        }
        return value;
    }
}
