package pl.puffmc.infinitefuel.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.utils.ItemUtils;

/**
 * Handles furnace-related events to implement infinite fuel mechanics.
 * Listens for fuel burn events and prevents consumption of infinite fuel items.
 */
public class FurnaceListener implements Listener {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ItemUtils itemUtils;
    
    /**
     * Creates a new FurnaceListener.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     * @param itemUtils The item utilities for tag checking
     */
    public FurnaceListener(Plugin plugin, ConfigManager configManager, ItemUtils itemUtils) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemUtils = itemUtils;
    }
    
    /**
     * Handles furnace burn events to implement infinite fuel.
     * When infinite fuel is detected:
     * - Sets burn time to extremely high value (Integer.MAX_VALUE / 2)
     * - Fuel item is never consumed due to the extended burn time
     * 
     * Priority: HIGH to ensure we process before other plugins
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        // Check if infinite fuel is enabled
        if (!configManager.isEnabled()) {
            return;
        }
        
        ItemStack fuel = event.getFuel();
        
        // Validate fuel item
        if (fuel == null || fuel.getType().isAir()) {
            return;
        }
        
        // Check if this fuel has the infinite fuel tag
        if (!itemUtils.hasInfiniteFuelTag(fuel)) {
            return;
        }
        
        // Set extremely high burn time for infinite burning
        // Using Integer.MAX_VALUE / 2 to avoid potential overflow issues
        // This effectively makes the fuel last forever
        event.setBurnTime(Integer.MAX_VALUE / 2);
        
        // The fuel item will not be consumed because the burn time is so high
        // that it won't decrement to 0 in any reasonable timeframe
        
        // Optional: Log for debugging (can be removed in production)
        if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
            Block block = event.getBlock();
            plugin.getLogger().fine("Nieskończone paliwo aktywowane w piecu na " + 
                block.getX() + ", " + block.getY() + ", " + block.getZ());
        }
    }
    
    /**
     * Handles furnace smelt events.
     * This can be used for additional logic if needed in the future,
     * such as tracking smelting operations or preventing specific smelts.
     * 
     * Currently monitors for potential issues but doesn't modify behavior.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // Check if infinite fuel is enabled
        if (!configManager.isEnabled()) {
            return;
        }
        
        Block block = event.getBlock();
        BlockState state = block.getState();
        
        if (!(state instanceof Furnace)) {
            return;
        }
        
        Furnace furnace = (Furnace) state;
        FurnaceInventory inventory = furnace.getInventory();
        ItemStack fuel = inventory.getFuel();
        
        // Check if furnace is using infinite fuel
        if (fuel != null && itemUtils.hasInfiniteFuelTag(fuel)) {
            // Infinite fuel is being used for smelting
            // No action needed - just monitoring
            
            // Optional: Add custom logic here if needed
            // For example: bonus items, special effects, etc.
        }
    }
    
    /**
     * Validates that furnace types are supported.
     * Includes: FURNACE, BLAST_FURNACE, SMOKER
     * 
     * @param material The block material to check
     * @return true if the material is a supported furnace type
     */
    private boolean isSupportedFurnaceType(Material material) {
        return material == Material.FURNACE || 
               material == Material.BLAST_FURNACE || 
               material == Material.SMOKER;
    }
}
