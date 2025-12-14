package pl.puffmc.infinitefuel.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.managers.MessageManager;
import pl.puffmc.infinitefuel.utils.ItemUtils;

/**
 * Handles inventory-related events for infinite fuel.
 * Controls hopper automation and prevents multi-stacking of infinite fuel items.
 */
public class InventoryListener implements Listener {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ItemUtils itemUtils;
    
    /**
     * Creates a new InventoryListener.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     * @param messageManager The message manager
     * @param itemUtils The item utilities for tag checking
     */
    public InventoryListener(Plugin plugin, ConfigManager configManager, 
                            MessageManager messageManager, ItemUtils itemUtils) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.itemUtils = itemUtils;
    }
    
    /**
     * Handles hopper item movement events.
     * Prevents hoppers from moving infinite fuel items if automation is disabled in config.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Check if hopper automation is allowed
        if (configManager.isHopperAutomationAllowed()) {
            return;
        }
        
        ItemStack item = event.getItem();
        
        // Check if the item being moved is infinite fuel
        if (item != null && itemUtils.hasInfiniteFuelTag(item)) {
            // Cancel the movement
            event.setCancelled(true);
            
            // Optional: Log for debugging
            if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
                plugin.getLogger().fine("Zablokowano przeniesienie nieskończonego paliwa przez hopper");
            }
        }
    }
    
    /**
     * Handles inventory click events for furnace fuel slots.
     * Enforces stack size limit of 1 for infinite fuel items when multi-stack prevention is enabled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if multi-stack prevention is enabled
        if (!configManager.isPreventMultiStack()) {
            return;
        }
        
        // Only process clicks in furnace-type inventories
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }
        
        InventoryType type = clickedInventory.getType();
        if (type != InventoryType.FURNACE && 
            type != InventoryType.BLAST_FURNACE && 
            type != InventoryType.SMOKER) {
            return;
        }
        
        // Check the item being placed
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        int slot = event.getSlot();
        
        // Furnace fuel slot is slot 1 (0=smelting, 1=fuel, 2=result)
        if (slot == 1) {
            handleFuelSlotClick(event, cursor, current);
        }
        
        // Also handle shift-click from player inventory
        if (event.isShiftClick() && type != event.getInventory().getType()) {
            handleShiftClickToFurnace(event);
        }
    }
    
    /**
     * Handles direct clicks on the fuel slot.
     * 
     * @param event The click event
     * @param cursor The item on the cursor
     * @param current The item currently in the slot
     */
    private void handleFuelSlotClick(InventoryClickEvent event, ItemStack cursor, ItemStack current) {
        boolean cursorIsInfinite = cursor != null && itemUtils.hasInfiniteFuelTag(cursor);
        boolean currentIsInfinite = current != null && itemUtils.hasInfiniteFuelTag(current);
        
        // Prevent stacking infinite fuel
        if (cursorIsInfinite && currentIsInfinite) {
            event.setCancelled(true);
            sendMessage(event, "messages.multi-stack-prevented");
            return;
        }
        
        // Limit stack size to 1 when placing infinite fuel
        if (cursorIsInfinite && cursor.getAmount() > 1) {
            // Allow placing but only 1 item
            event.setCancelled(true);
            
            // Create a single-item stack
            ItemStack singleStack = cursor.clone();
            singleStack.setAmount(1);
            
            // Place single item in slot
            event.getClickedInventory().setItem(event.getSlot(), singleStack);
            
            // Reduce cursor amount by 1
            cursor.setAmount(cursor.getAmount() - 1);
            event.getWhoClicked().setItemOnCursor(cursor);
            
            sendMessage(event, "messages.multi-stack-prevented");
        }
        
        // Prevent increasing stack size of infinite fuel already in slot
        if (currentIsInfinite && cursorIsInfinite) {
            event.setCancelled(true);
            sendMessage(event, "messages.multi-stack-prevented");
        }
    }
    
    /**
     * Handles shift-click operations that move items to furnace.
     * 
     * @param event The click event
     */
    private void handleShiftClickToFurnace(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null || !itemUtils.hasInfiniteFuelTag(clicked)) {
            return;
        }
        
        // Check if furnace fuel slot already has infinite fuel
        Inventory furnaceInv = event.getInventory();
        ItemStack fuelSlot = furnaceInv.getItem(1);
        
        if (fuelSlot != null && itemUtils.hasInfiniteFuelTag(fuelSlot)) {
            // Already has infinite fuel, prevent adding more
            event.setCancelled(true);
            sendMessage(event, "messages.multi-stack-prevented");
            return;
        }
        
        // Allow shift-click but ensure only 1 item is moved
        // We'll handle this in a follow-up tick to allow normal processing
        if (clicked.getAmount() > 1) {
            event.setCancelled(true);
            
            // Manually place 1 item in fuel slot
            ItemStack singleStack = clicked.clone();
            singleStack.setAmount(1);
            furnaceInv.setItem(1, singleStack);
            
            // Reduce source stack
            clicked.setAmount(clicked.getAmount() - 1);
            
            sendMessage(event, "messages.multi-stack-prevented");
        }
    }
    
    /**
     * Sends a message to the player who clicked.
     * 
     * @param event The click event
     * @param messageKey The message key
     */
    private void sendMessage(InventoryClickEvent event, String messageKey) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            player.sendMessage(messageManager.getMessageWithPrefix(messageKey));
        }
    }
}
