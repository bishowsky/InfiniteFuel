package pl.puffmc.infinitefuel.listeners;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.managers.MessageManager;
import pl.puffmc.infinitefuel.utils.ItemUtils;

/**
 * Prevents infinite fuel items from being used in crafting and modification interfaces.
 * Blocks usage in: Crafting Table, Anvil, Smithing Table, Grindstone, Stonecutter, 
 * Loom, Cartography Table, and Brewing Stand.
 */
public class CraftingListener implements Listener {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ItemUtils itemUtils;
    
    /**
     * Creates a new CraftingListener.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     * @param messageManager The message manager
     * @param itemUtils The item utilities for tag checking
     */
    public CraftingListener(Plugin plugin, ConfigManager configManager, 
                           MessageManager messageManager, ItemUtils itemUtils) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.itemUtils = itemUtils;
    }
    
    /**
     * Prevents crafting with infinite fuel in crafting tables.
     * Checks all items in the crafting matrix and nullifies the result if infinite fuel is found.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        
        // Check each item in the crafting matrix
        for (ItemStack item : matrix) {
            if (item != null && itemUtils.hasInfiniteFuelTag(item)) {
                // Cancel crafting by setting result to air
                inventory.setResult(new ItemStack(Material.AIR));
                
                // Notify the player
                notifyPlayer(event.getViewers(), "messages.crafting-blocked");
                return;
            }
        }
    }
    
    /**
     * Prevents using infinite fuel in anvils.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        // Check both anvil input slots
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        
        if ((first != null && itemUtils.hasInfiniteFuelTag(first)) ||
            (second != null && itemUtils.hasInfiniteFuelTag(second))) {
            
            // Cancel by setting result to null
            event.setResult(null);
            
            // Notify the player
            notifyPlayer(event.getViewers(), "messages.crafting-blocked");
        }
    }
    
    /**
     * Prevents using infinite fuel in smithing tables.
     * Handles both legacy (1.19-) and modern (1.20+) smithing mechanics.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        // Check all items in smithing inventory
        ItemStack[] contents = event.getInventory().getContents();
        
        for (ItemStack item : contents) {
            if (item != null && itemUtils.hasInfiniteFuelTag(item)) {
                // Cancel by setting result to null
                event.setResult(null);
                
                // Notify the player
                notifyPlayer(event.getViewers(), "messages.crafting-blocked");
                return;
            }
        }
    }
    
    /**
     * Prevents using infinite fuel in grindstones.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        GrindstoneInventory inventory = event.getInventory();
        ItemStack upper = inventory.getUpperItem();
        ItemStack lower = inventory.getLowerItem();
        
        if ((upper != null && itemUtils.hasInfiniteFuelTag(upper)) ||
            (lower != null && itemUtils.hasInfiniteFuelTag(lower))) {
            
            // Cancel by setting result to null
            event.setResult(null);
            
            // Notify the player
            notifyPlayer(event.getViewers(), "messages.crafting-blocked");
        }
    }
    
    /**
     * Prevents using infinite fuel in stonecutters, looms, and cartography tables.
     * These don't have specific PrepareX events, so we use InventoryClickEvent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        // Check if this is a special crafting inventory
        InventoryType type = event.getInventory().getType();
        
        if (type == InventoryType.STONECUTTER || 
            type == InventoryType.LOOM || 
            type == InventoryType.CARTOGRAPHY) {
            
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            
            // Check if player is trying to place infinite fuel
            if ((cursor != null && itemUtils.hasInfiniteFuelTag(cursor)) ||
                (current != null && itemUtils.hasInfiniteFuelTag(current))) {
                
                event.setCancelled(true);
                
                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    player.sendMessage(messageManager.getMessageWithPrefix("messages.crafting-blocked"));
                }
            }
        }
    }
    
    /**
     * Prevents using infinite fuel in brewing stands.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!configManager.isPreventCrafting()) {
            return;
        }
        
        // Check ingredient slot
        ItemStack ingredient = event.getContents().getIngredient();
        
        if (ingredient != null && itemUtils.hasInfiniteFuelTag(ingredient)) {
            event.setCancelled(true);
            
            // Notify nearby players
            // Note: BrewEvent doesn't have getViewers(), so we can't easily notify
            // The cancellation itself is sufficient
        }
    }
    
    /**
     * Helper method to notify players viewing an inventory.
     * 
     * @param viewers List of entities viewing the inventory
     * @param messageKey The message key to send
     */
    private void notifyPlayer(java.util.List<HumanEntity> viewers, String messageKey) {
        if (viewers.isEmpty()) {
            return;
        }
        
        // Send message to first viewer (usually the player who opened it)
        HumanEntity viewer = viewers.get(0);
        if (viewer instanceof Player) {
            Player player = (Player) viewer;
            player.sendMessage(messageManager.getMessageWithPrefix(messageKey));
        }
    }
}
