package pl.puffmc.infinitefuel.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages multi-language message files for the plugin.
 * Supports locale-based message loading with fallback to default language.
 * Handles color code translation and placeholder replacement.
 */
public class MessageManager {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private File messagesFile;
    private FileConfiguration messages;
    private String currentLocale;
    private final Map<String, String> messageCache;
    
    /**
     * Creates a new MessageManager instance.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager for locale settings
     */
    public MessageManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageCache = new HashMap<>();
        loadMessages();
    }
    
    /**
     * Loads messages from the appropriate locale file.
     * Falls back to pl_PL if the specified locale doesn't exist.
     */
    public void loadMessages() {
        messageCache.clear();
        currentLocale = configManager.getLocale();
        
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Save default language files if they don't exist
        saveDefaultLanguageFile("pl_PL.yml");
        saveDefaultLanguageFile("en_US.yml");
        
        messagesFile = new File(langDir, currentLocale + ".yml");
        
        // Fallback to pl_PL if specified locale doesn't exist
        if (!messagesFile.exists()) {
            plugin.getLogger().warning("Plik językowy " + currentLocale + ".yml nie istnieje. Używanie pl_PL.yml");
            currentLocale = "pl_PL";
            messagesFile = new File(langDir, "pl_PL.yml");
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Załadowano wiadomości z języka: " + currentLocale);
    }
    
    /**
     * Saves a default language file from resources if it doesn't exist.
     * 
     * @param fileName The language file name (e.g., "pl_PL.yml")
     */
    private void saveDefaultLanguageFile(String fileName) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }
    
    /**
     * Reloads messages from disk.
     */
    public void reload() {
        loadMessages();
    }
    
    /**
     * Gets a message by key with color code translation.
     * 
     * @param key The message key (e.g., "messages.reload-success")
     * @return The formatted message, or the key if not found
     */
    public String getMessage(String key) {
        // Check cache first
        if (messageCache.containsKey(key)) {
            return messageCache.get(key);
        }
        
        String message = messages.getString(key);
        
        if (message == null) {
            plugin.getLogger().warning("Brakująca wiadomość w pliku językowym: " + key);
            return key;
        }
        
        // Translate color codes (& to §)
        String formatted = translateColorCodes(message);
        messageCache.put(key, formatted);
        
        return formatted;
    }
    
    /**
     * Gets a message with placeholder replacement.
     * 
     * @param key The message key
     * @param placeholders Map of placeholder keys to replacement values
     * @return The formatted message with replaced placeholders
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Gets a message with a single placeholder.
     * 
     * @param key The message key
     * @param placeholder The placeholder name
     * @param value The replacement value
     * @return The formatted message
     */
    public String getMessage(String key, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(key, placeholders);
    }
    
    /**
     * Gets a message with prefix prepended.
     * 
     * @param key The message key
     * @return The message with plugin prefix
     */
    public String getMessageWithPrefix(String key) {
        String prefix = getMessage("messages.prefix");
        String message = getMessage(key);
        return prefix + message;
    }
    
    /**
     * Gets a message with prefix and placeholders.
     * 
     * @param key The message key
     * @param placeholders Map of placeholder replacements
     * @return The message with prefix and replaced placeholders
     */
    public String getMessageWithPrefix(String key, Map<String, String> placeholders) {
        String prefix = getMessage("messages.prefix");
        String message = getMessage(key, placeholders);
        return prefix + message;
    }
    
    /**
     * Translates & color codes to § section signs.
     * 
     * @param text The text with & color codes
     * @return The text with § color codes
     */
    private String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "§");
    }
    
    /**
     * Gets the current locale.
     * 
     * @return The current locale string
     */
    public String getCurrentLocale() {
        return currentLocale;
    }
}
