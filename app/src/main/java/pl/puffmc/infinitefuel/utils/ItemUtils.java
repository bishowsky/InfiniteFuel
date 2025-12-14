package pl.puffmc.infinitefuel.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for managing infinite fuel item identification using PersistentDataContainer.
 * Uses modern Paper API (1.21+) for reliable item tagging that persists across server restarts.
 */
public class ItemUtils {
    
    private final Plugin plugin;
    private final NamespacedKey infiniteFuelKey;
    
    /**
     * Creates a new ItemUtils instance.
     * 
     * @param plugin The plugin instance for creating NamespacedKey
     */
    public ItemUtils(Plugin plugin) {
        this.plugin = plugin;
        this.infiniteFuelKey = new NamespacedKey(plugin, "infinite_fuel");
    }
    
    /**
     * Adds the infinite fuel tag to an ItemStack using PersistentDataContainer.
     * This tag persists through server restarts, chunk unloads, and item transfers.
     * 
     * @param item The ItemStack to tag
     * @return The tagged ItemStack, or the original if tagging failed
     */
    public ItemStack addInfiniteFuelTag(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(infiniteFuelKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Checks if an ItemStack has the infinite fuel tag.
     * 
     * @param item The ItemStack to check
     * @return true if the item has the infinite fuel tag, false otherwise
     */
    public boolean hasInfiniteFuelTag(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(infiniteFuelKey, PersistentDataType.STRING);
    }
    
    /**
     * Removes the infinite fuel tag from an ItemStack.
     * Useful for admin tools or debugging.
     * 
     * @param item The ItemStack to untag
     * @return The untagged ItemStack
     */
    public ItemStack removeInfiniteFuelTag(ItemStack item) {
        if (item == null || item.getType().isAir() || !hasInfiniteFuelTag(item)) {
            return item;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(infiniteFuelKey);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Gets the NamespacedKey used for infinite fuel tagging.
     * 
     * @return The NamespacedKey for infinite fuel identification
     */
    public NamespacedKey getInfiniteFuelKey() {
        return infiniteFuelKey;
    }
}
