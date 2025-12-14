package pl.puffmc.infinitefuel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.puffmc.infinitefuel.commands.InfiniteFuelCommand;
import pl.puffmc.infinitefuel.listeners.CraftingListener;
import pl.puffmc.infinitefuel.listeners.FurnaceListener;
import pl.puffmc.infinitefuel.listeners.InventoryListener;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.managers.ItemFactory;
import pl.puffmc.infinitefuel.managers.MessageManager;
import pl.puffmc.infinitefuel.utils.ItemUtils;
import pl.puffmc.infinitefuel.utils.MaterialValidator;

import java.util.List;

/**
 * InfiniteFuel - Main plugin class
 * 
 * Provides infinite fuel items for Minecraft furnaces (Furnace, Blast Furnace, Smoker).
 * 
 * Features:
 * - Infinite coal that never gets consumed
 * - Configurable allowed fuel materials
 * - Prevention of crafting with infinite fuel
 * - Hopper automation control
 * - Multi-stack prevention
 * - Bilingual support (Polish/English)
 * 
 * Compatible with:
 * - Paper 1.21 - 1.21.11
 * - Folia 1.21.8
 * 
 * @author PuffMC
 * @version 1.0.0
 */
public final class InfiniteFuel extends JavaPlugin implements Listener {
    
    // Core managers
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ItemFactory itemFactory;
    
    // Utilities
    private ItemUtils itemUtils;
    private MaterialValidator materialValidator;
    
    // Validated materials
    private List<Material> validMaterials;
    
    // Command handler
    private InfiniteFuelCommand commandHandler;
    
    @Override
    public void onEnable() {
        // 1. Save default configuration (without overwriting existing values)
        saveDefaultConfig();
        
        // 2. Update config with new keys if they don't exist
        updateConfig();
        
        // 3. Initialize managers
        initializeManagers();
        
        // 4. Validate materials from config
        validateMaterials();
        
        // 5. Register event listeners
        registerListeners();
        
        // 6. Register this plugin as listener for ServerLoadEvent (Paper 1.21+ command registration)
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // 7. Log startup message
        getLogger().info("===========================================");
        getLogger().info("InfiniteFuel enabled successfully!");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Author: Bishyy");
        getLogger().info("Language: " + configManager.getLocale());
        getLogger().info("Loaded " + validMaterials.size() + " fuel materials");
        getLogger().info("===========================================");
    }
    
    /**
     * Updates existing config.yml with new keys from defaults without overwriting existing values.
     * This allows seamless updates when new config options are added in plugin updates.
     */
    private void updateConfig() {
        boolean configUpdated = false;
        
        // Add new keys if they don't exist
        if (!getConfig().contains("debug.enabled")) {
            getConfig().set("debug.enabled", false);
            configUpdated = true;
        }
        
        // Save if any updates were made
        if (configUpdated) {
            saveConfig();
            getLogger().info("Configuration updated with new options!");
        }
    }
    
    @Override
    public void onDisable() {
        // Log shutdown message
        getLogger().info("===========================================");
        getLogger().info("InfiniteFuel disabled!");
        getLogger().info("Goodbye!");
        getLogger().info("===========================================");
    }
    
    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // Config manager
        configManager = new ConfigManager(this);
        
        // Message manager (depends on config manager for locale)
        messageManager = new MessageManager(this, configManager);
        
        // Item utils
        itemUtils = new ItemUtils(this);
        
        // Material validator
        materialValidator = new MaterialValidator(this);
        
        // Item factory (depends on config, message, and item utils)
        itemFactory = new ItemFactory(this, configManager, messageManager, itemUtils);
        
        getLogger().info("Managers initialized successfully.");
    }
    
    /**
     * Validates materials from configuration.
     */
    private void validateMaterials() {
        List<String> materialNames = configManager.getAllowedMaterialNames();
        validMaterials = materialValidator.validateMaterials(materialNames);
        
        if (validMaterials.isEmpty()) {
            getLogger().warning("WARNING: No valid fuel materials found!");
            getLogger().warning("Plugin may not work correctly.");
        }
    }
    
    /**
     * Registers event listeners.
     */
    private void registerListeners() {
        // Furnace listener - handles fuel burning
        FurnaceListener furnaceListener = new FurnaceListener(this, configManager, itemUtils);
        Bukkit.getPluginManager().registerEvents(furnaceListener, this);
        getLogger().info("Registered FurnaceListener");
        
        // Crafting listener - prevents crafting with infinite fuel
        CraftingListener craftingListener = new CraftingListener(this, configManager, messageManager, itemUtils);
        Bukkit.getPluginManager().registerEvents(craftingListener, this);
        getLogger().info("Registered CraftingListener");
        
        // Inventory listener - hopper automation and multi-stack prevention
        InventoryListener inventoryListener = new InventoryListener(this, configManager, messageManager, itemUtils);
        Bukkit.getPluginManager().registerEvents(inventoryListener, this);
        getLogger().info("Registered InventoryListener");
    }
    
    /**
     * Registers commands after server loads.
     * This is required for Paper 1.21+ compatibility.
     * Commands cannot be registered in onEnable() due to Paper API restrictions.
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        try {
            // Create command handler
            commandHandler = new InfiniteFuelCommand(
                this,
                configManager,
                messageManager,
                itemFactory,
                materialValidator,
                validMaterials
            );
            
            // Get and register command
            org.bukkit.command.PluginCommand command = this.getCommand("infinitefuel");
            
            if (command != null) {
                command.setExecutor(commandHandler);
                command.setTabCompleter(commandHandler);
                getLogger().info("Command /infinitefuel registered successfully!");
            } else {
                getLogger().severe("ERROR: Command 'infinitefuel' not found in plugin.yml!");
                getLogger().severe("Plugin will not have available commands!");
            }
            
        } catch (Exception e) {
            getLogger().severe("ERROR during command registration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Public getters for accessing managers from other classes if needed
    
    /**
     * Gets the config manager instance.
     * 
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the message manager instance.
     * 
     * @return The message manager
     */
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    /**
     * Gets the item factory instance.
     * 
     * @return The item factory
     */
    public ItemFactory getItemFactory() {
        return itemFactory;
    }
    
    /**
     * Gets the item utils instance.
     * 
     * @return The item utils
     */
    public ItemUtils getItemUtils() {
        return itemUtils;
    }
    
    /**
     * Gets the material validator instance.
     * 
     * @return The material validator
     */
    public MaterialValidator getMaterialValidator() {
        return materialValidator;
    }
    
    /**
     * Gets the list of valid fuel materials.
     * 
     * @return List of valid materials
     */
    public List<Material> getValidMaterials() {
        return validMaterials;
    }
}
