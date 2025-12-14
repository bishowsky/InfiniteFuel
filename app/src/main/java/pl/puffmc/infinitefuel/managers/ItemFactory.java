package pl.puffmc.infinitefuel.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.utils.ItemUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory class for creating infinite fuel items.
 * Handles item creation with proper metadata, lore, and persistent data tagging.
 */
public class ItemFactory {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ItemUtils itemUtils;
    
    /**
     * Creates a new ItemFactory instance.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     * @param messageManager The message manager
     * @param itemUtils The item utilities for tagging
     */
    public ItemFactory(Plugin plugin, ConfigManager configManager, 
                       MessageManager messageManager, ItemUtils itemUtils) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.itemUtils = itemUtils;
    }
    
    /**
     * Creates an infinite fuel item from the specified material.
     * 
     * @param material The base material for the fuel
     * @param amount The stack size (1-64)
     * @return The created infinite fuel ItemStack
     */
    public ItemStack createInfiniteFuel(Material material, int amount) {
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Próba utworzenia nieskończonego paliwa z nieprawidłowego materiału: " + material);
            return null;
        }
        
        // Ensure amount is valid
        amount = Math.max(1, Math.min(64, amount));
        
        // Create base item
        ItemStack item = new ItemStack(material, amount);
        
        // Get metadata
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.getLogger().warning("Nie można uzyskać ItemMeta dla materiału: " + material);
            return null;
        }
        
        // Set display name with placeholder replacement
        String displayName = configManager.getItemName();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", formatMaterialName(material));
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            displayName = displayName.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        // Use modern Component API instead of deprecated setDisplayName
        Component displayNameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
        meta.displayName(displayNameComponent);
        
        // Set lore with placeholder replacement
        List<String> loreTemplate = configManager.getItemLore();
        List<Component> lore = new ArrayList<>();
        
        for (String line : loreTemplate) {
            String formattedLine = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formattedLine = formattedLine.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            Component loreComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(formattedLine);
            lore.add(loreComponent);
        }
        
        // Use modern Component API instead of deprecated setLore
        meta.lore(lore);
        
        // Apply metadata to item
        item.setItemMeta(meta);
        
        // Add persistent data tag for identification
        item = itemUtils.addInfiniteFuelTag(item);
        
        return item;
    }
    
    /**
     * Creates an infinite fuel item with default amount of 1.
     * 
     * @param material The base material for the fuel
     * @return The created infinite fuel ItemStack
     */
    public ItemStack createInfiniteFuel(Material material) {
        return createInfiniteFuel(material, 1);
    }
    
    /**
     * Formats a material name for display (removes underscores, capitalizes).
     * 
     * @param material The material to format
     * @return Formatted material name
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        
        // Capitalize first letter of each word
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1));
                }
                formatted.append(" ");
            }
        }
        
        return formatted.toString().trim();
    }
    

    
    /**
     * Checks if an item is a valid infinite fuel item.
     * Validates both the persistent data tag and the material.
     * 
     * @param item The item to check
     * @param validMaterials List of valid fuel materials
     * @return true if the item is valid infinite fuel
     */
    public boolean isValidInfiniteFuel(ItemStack item, List<Material> validMaterials) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        
        // Must have the infinite fuel tag
        if (!itemUtils.hasInfiniteFuelTag(item)) {
            return false;
        }
        
        // Must be one of the allowed materials
        return validMaterials.contains(item.getType());
    }
}
