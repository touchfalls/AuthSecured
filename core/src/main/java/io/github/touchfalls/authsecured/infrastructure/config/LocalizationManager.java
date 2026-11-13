package io.github.touchfalls.authsecured.infrastructure.config;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class LocalizationManager {

    private final File dataFolder;
    private final PlatformLogger logger;
    private volatile Map<String, Object> defaultMessagesMap = Collections.emptyMap();
    private volatile Map<String, Object> bundledDefaultMessagesMap = Collections.emptyMap();
    private volatile Map<String, Object> activeMessagesMap = Collections.emptyMap();
    private volatile Map<String, Object> bundledActiveMessagesMap = Collections.emptyMap();
    private String currentLanguage = "en";

    public LocalizationManager(File dataFolder, String langCode, PlatformLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        init(langCode);
    }

    public void init(String langCode) {
        this.defaultMessagesMap = Collections.emptyMap();
        this.activeMessagesMap = Collections.emptyMap();
        File langDir = new File(dataFolder, "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        saveDefaultIfNotExists("lang/messages_en.yml", new File(langDir, "messages_en.yml"));
        saveDefaultIfNotExists("lang/messages_ru.yml", new File(langDir, "messages_ru.yml"));
        saveDefaultIfNotExists("lang/messages_es.yml", new File(langDir, "messages_es.yml"));
        saveDefaultIfNotExists("lang/messages_it.yml", new File(langDir, "messages_it.yml"));
        saveDefaultIfNotExists("lang/messages_fr.yml", new File(langDir, "messages_fr.yml"));

        this.currentLanguage = (langCode != null && !langCode.isBlank())
                ? langCode.toLowerCase(Locale.ROOT) : "en";

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        this.bundledDefaultMessagesMap = loadBundledMessages(yaml, "lang/messages_en.yml");
        this.bundledActiveMessagesMap = loadBundledMessages(
                yaml, "lang/messages_" + currentLanguage + ".yml");
        File defaultFile = new File(langDir, "messages_en.yml");
        if (defaultFile.exists()) {
            try (InputStream is = new FileInputStream(defaultFile)) {
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null) this.defaultMessagesMap = loaded;
            } catch (Exception e) {
                logger.severe("Failed to load default localization messages_en.yml.");
            }
        }

        File activeFile = new File(langDir, "messages_" + currentLanguage + ".yml");
        if (!activeFile.exists()) {
            activeFile = new File(dataFolder, "messages.yml");
        }

        if (activeFile.exists()) {
            try (InputStream is = new FileInputStream(activeFile)) {
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null) this.activeMessagesMap = loaded;
            } catch (Exception e) {
                logger.warning("Failed to load active localization " + activeFile.getName()
                        + "; falling back to the bundled translation.");
                this.activeMessagesMap = Collections.emptyMap();
            }
        } else {
            this.activeMessagesMap = this.defaultMessagesMap;
        }
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public String getMessage(String path, String defaultValue) {
        return getMessage(path, defaultValue, null);
    }

    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        Object val = getNestedValue(activeMessagesMap, path);
        if (val == null) {
            val = getNestedValue(bundledActiveMessagesMap, path);
        }
        if (val == null) {
            val = getNestedValue(defaultMessagesMap, path);
        }
        if (val == null) {
            val = getNestedValue(bundledDefaultMessagesMap, path);
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

    private Map<String, Object> loadBundledMessages(Yaml yaml, String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) return Collections.emptyMap();
            Map<String, Object> loaded = yaml.load(input);
            return loaded == null ? Collections.emptyMap() : loaded;
        } catch (Exception exception) {
            logger.warning("Could not load bundled localization resource: " + resourcePath);
            return Collections.emptyMap();
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
