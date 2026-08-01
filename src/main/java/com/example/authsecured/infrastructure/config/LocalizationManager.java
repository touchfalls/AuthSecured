package com.example.authsecured.infrastructure.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class LocalizationManager {

    private final File dataFolder;
    private final Logger logger;
    private Map<String, Object> defaultMessagesMap = Collections.emptyMap();
    private Map<String, Object> activeMessagesMap = Collections.emptyMap();
    private String currentLanguage = "en";

    public LocalizationManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void init(String langCode) {
        File langDir = new File(dataFolder, "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        saveDefaultIfNotExists("lang/messages_en.yml", new File(langDir, "messages_en.yml"));
        saveDefaultIfNotExists("lang/messages_ru.yml", new File(langDir, "messages_ru.yml"));
        saveDefaultIfNotExists("lang/messages_es.yml", new File(langDir, "messages_es.yml"));
        saveDefaultIfNotExists("lang/messages_it.yml", new File(langDir, "messages_it.yml"));
        saveDefaultIfNotExists("lang/messages_fr.yml", new File(langDir, "messages_fr.yml"));

        this.currentLanguage = (langCode != null && !langCode.isBlank()) ? langCode.toLowerCase() : "en";

        Yaml yaml = new Yaml();
        File defaultFile = new File(langDir, "messages_en.yml");
        if (defaultFile.exists()) {
            try (InputStream is = new FileInputStream(defaultFile)) {
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null) this.defaultMessagesMap = loaded;
            } catch (Exception e) {
                logger.severe("Failed to load default localization (messages_en.yml): " + e.getMessage());
            }
        }

        File activeFile = new File(langDir, "messages_" + currentLanguage + ".yml");
        if (!activeFile.exists()) {
            activeFile = new File(dataFolder, "messages.yml"); // Backward compatibility
        }

        if (activeFile.exists()) {
            try (InputStream is = new FileInputStream(activeFile)) {
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null) this.activeMessagesMap = loaded;
            } catch (Exception e) {
                logger.warning("Failed to load active localization (" + activeFile.getName() + "), falling back to default: " + e.getMessage());
                this.activeMessagesMap = this.defaultMessagesMap;
            }
        } else {
            this.activeMessagesMap = this.defaultMessagesMap;
        }
    }

    public String getMessage(String path, String defaultValue) {
        return getMessage(path, defaultValue, null);
    }

    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        Object val = getNestedValue(activeMessagesMap, path);
        if (val == null) {
            val = getNestedValue(defaultMessagesMap, path);
        }

        String rawStr = (val instanceof String s) ? s : defaultValue;
        if (rawStr == null) rawStr = "";

        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rawStr = rawStr.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return colorize(rawStr);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    private String colorize(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }

    private void saveDefaultIfNotExists(String resourcePath, File targetFile) {
        if (!targetFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Files.copy(in, targetFile.toPath());
                } else {
                    targetFile.createNewFile();
                }
            } catch (Exception e) {
                logger.warning("Could not extract localization resource: " + resourcePath);
            }
        }
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
}
