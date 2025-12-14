package pl.puffmc.infinitefuel.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Manages plugin configuration file (config.yml).
 * Provides centralized access to configuration values with proper reloading.
 */
public class ConfigManager {
    
    private final Plugin plugin;
    private FileConfiguration config;
    
    /**
     * Creates a new ConfigManager instance.
     * 
     * @param plugin The plugin instance
     */
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }
    
    /**
     * Reloads the configuration from disk.
     * Should be called after config.yml is modified or on /reload command.
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        plugin.getLogger().info("Configuration reloaded successfully.");
    }
    
    /**
     * Gets the current FileConfiguration object.
     * 
     * @return The loaded configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Saves the default config.yml if it doesn't exist.
     */
    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }
    
    // Convenience methods for commonly accessed config values
    
    /**
     * Checks if debug logging is enabled.
     * 
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }
    
    /**
     * Checks if infinite fuel functionality is enabled.
     * 
     * @return true if enabled in config
     */
    public boolean isEnabled() {
        return config.getBoolean("infinite-fuel.enabled", true);
    }
    
    /**
     * Checks if crafting with infinite fuel should be prevented.
     * 
     * @return true if crafting prevention is enabled
     */
    public boolean isPreventCrafting() {
        return config.getBoolean("infinite-fuel.prevent-crafting", true);
    }
    
    /**
     * Checks if hopper automation is allowed for infinite fuel.
     * 
     * @return true if hoppers can move infinite fuel
     */
    public boolean isHopperAutomationAllowed() {
        return config.getBoolean("infinite-fuel.allow-hopper-automation", false);
    }
    
    /**
     * Checks if multiple infinite fuel items should be prevented in stacks.
     * 
     * @return true if multi-stack prevention is enabled
     */
    public boolean isPreventMultiStack() {
        return config.getBoolean("infinite-fuel.prevent-multi-stack", true);
    }
    
    /**
     * Gets the configured locale for messages.
     * 
     * @return The locale string (e.g., "pl_PL", "en_US")
     */
    public String getLocale() {
        return config.getString("language.locale", "pl_PL");
    }
    
    /**
     * Gets the configured item display name.
     * 
     * @return The item name with color codes
     */
    public String getItemName() {
        return config.getString("item.name", "&6Nieskończone Paliwo");
    }
    
    /**
     * Gets the configured item lore lines.
     * 
     * @return List of lore lines with color codes
     */
    public java.util.List<String> getItemLore() {
        return config.getStringList("item.lore");
    }
    
    /**
     * Gets the list of allowed material names from config.
     * 
     * @return List of material name strings
     */
    public java.util.List<String> getAllowedMaterialNames() {
        return config.getStringList("infinite-fuel.allowed-materials");
    }
    
    /**
     * Gets the default material type for infinite fuel.
     * 
     * @return The default material name (e.g., "COAL")
     */
    public String getDefaultMaterial() {
        return config.getString("infinite-fuel.default-material", "COAL");
    }
}
