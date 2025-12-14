package pl.puffmc.infinitefuel.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.utils.ItemUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles furnace-related events to implement infinite fuel mechanics.
 * Listens for fuel burn events and prevents consumption of infinite fuel items.
 * 
 * CRITICAL: This listener ensures that infinite fuel NEVER runs out by:
 * 1. Setting extremely long burn time
 * 2. Immediately restoring fuel after consumption
 * 3. Continuously monitoring fuel slots
 * 4. Preventing manual removal during burning
 */
public class FurnaceListener implements Listener {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ItemUtils itemUtils;
    
    // Track active infinite fuel locations for continuous monitoring
    private final Map<String, ItemStack> activeFurnaces = new ConcurrentHashMap<>();
    
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
     * Handles furnace burn events to implement TRUE infinite fuel.
     * 
     * This event fires when the furnace's burn time reaches 0 and it needs new fuel.
     * - We set burn time to Short.MAX_VALUE (32767 ticks = ~27 minutes)
     * - We immediately restore the fuel item so it's never consumed
     * - Smelting process works normally - items cook, burn time decreases
     * - When burn time reaches 0 again, this event fires and cycle repeats
     * 
     * Priority: LOWEST to let other plugins process first, then we override
     * 
     * FOLIA COMPATIBLE: Uses region scheduler instead of Bukkit scheduler
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
        
        Block block = event.getBlock();
        String locationKey = getLocationKey(block);
        ItemStack fuelCopy = fuel.clone();
        
        // CRITICAL: Set burn time to maximum possible value for short type
        // Short.MAX_VALUE = 32767 ticks = ~27 minutes of continuous burning
        event.setBurnTime(Short.MAX_VALUE);
        event.setBurning(true);
        
        // Track this furnace as active
        activeFurnaces.put(locationKey, fuelCopy);
        
        // CRITICAL FIX: The fuel item will be consumed after this event
        // We MUST restore it on the next tick AND start monitoring
        // FOLIA-SAFE: Use region scheduler for block operations
        try {
            // Try Folia's region scheduler first
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), scheduledTask -> {
                restoreFuelToFurnace(block, fuelCopy);
                startFurnaceMonitoring(block, fuelCopy);
            });
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Folia API not available, fall back to Paper scheduler
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    restoreFuelToFurnace(block, fuelCopy);
                    startFurnaceMonitoring(block, fuelCopy);
                });
            } catch (UnsupportedOperationException ex) {
                // On Folia without region scheduler - direct restore
                restoreFuelToFurnace(block, fuelCopy);
                startFurnaceMonitoring(block, fuelCopy);
            }
        }
        
        // Debug logging
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Infinite fuel activated at " + locationKey + 
                                  " (burn time: " + event.getBurnTime() + " ticks)");
        }
    }
    
    /**
     * Starts periodic monitoring of a furnace to ensure infinite fuel.
     * Checks every 20 ticks (1 second) if burn time is getting low.
     * 
     * @param block The furnace block
     * @param fuel The infinite fuel item
     */
    private void startFurnaceMonitoring(Block block, ItemStack fuel) {
        String locationKey = getLocationKey(block);
        
        // Schedule repeating task to monitor this furnace
        // FOLIA-SAFE: Use region scheduler
        try {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, block.getLocation(), scheduledTask -> {
                BlockState state = block.getState();
                if (!(state instanceof Furnace)) {
                    scheduledTask.cancel();
                    activeFurnaces.remove(locationKey);
                    return;
                }
                
                Furnace furnace = (Furnace) state;
                FurnaceInventory inventory = furnace.getInventory();
                ItemStack currentFuel = inventory.getFuel();
                
                // Check if furnace still has infinite fuel
                if (currentFuel == null || !itemUtils.hasInfiniteFuelTag(currentFuel)) {
                    // Restore fuel if missing
                    inventory.setFuel(fuel.clone());
                    furnace.update(true);
                    
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("[DEBUG] Restored missing fuel at " + locationKey);
                    }
                }
                
                // CRITICAL: Manage burn time carefully to not break smelting
                short burnTime = furnace.getBurnTime();
                short cookTime = furnace.getCookTime();
                int cookTimeTotal = furnace.getCookTimeTotal();
                
                // Calculate how many ticks needed to finish current item
                int ticksNeededToFinish = cookTimeTotal - cookTime;
                
                // If burn time is too low to finish cooking, add more
                // But DON'T reset to max - that breaks the cooking process!
                // Buffer = 100 ticks to account for 20-tick scheduler delay
                if (burnTime > 0 && burnTime < ticksNeededToFinish + 100) {
                    // Add just enough to finish + small buffer (200 ticks = 10 seconds)
                    short newBurnTime = (short) Math.min(ticksNeededToFinish + 200, Short.MAX_VALUE);
                    furnace.setBurnTime(newBurnTime);
                    furnace.update(true);
                    
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("[DEBUG] Added burn time at " + locationKey + 
                                              " (was: " + burnTime + ", now: " + newBurnTime + 
                                              ", needed: " + ticksNeededToFinish + ")");
                    }
                }
                
                // If furnace is not burning and has no items, stop monitoring
                if (burnTime <= 0 && (inventory.getSmelting() == null || inventory.getSmelting().getType().isAir())) {
                    scheduledTask.cancel();
                    activeFurnaces.remove(locationKey);
                    
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("[DEBUG] Stopped monitoring idle furnace at " + locationKey);
                    }
                }
            }, 1, 20); // Start after 1 tick, repeat every 20 ticks (1 second)
            
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Folia not available, use Bukkit scheduler
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                BlockState state = block.getState();
                if (!(state instanceof Furnace)) {
                    activeFurnaces.remove(locationKey);
                    return;
                }
                
                Furnace furnace = (Furnace) state;
                FurnaceInventory inventory = furnace.getInventory();
                ItemStack currentFuel = inventory.getFuel();
                
                // Restore fuel if missing
                if (currentFuel == null || !itemUtils.hasInfiniteFuelTag(currentFuel)) {
                    inventory.setFuel(fuel.clone());
                    furnace.update(true);
                }
                
                // Smart burn time management
                short burnTime = furnace.getBurnTime();
                short cookTime = furnace.getCookTime();
                int cookTimeTotal = furnace.getCookTimeTotal();
                int ticksNeededToFinish = cookTimeTotal - cookTime;
                
                if (burnTime > 0 && burnTime < ticksNeededToFinish + 100) {
                    short newBurnTime = (short) Math.min(ticksNeededToFinish + 200, Short.MAX_VALUE);
                    furnace.setBurnTime(newBurnTime);
                    furnace.update(true);
                }
            }, 1L, 20L);
        }
    }
    
    /**
     * Restores infinite fuel to a furnace.
     * Helper method to ensure fuel is properly set and updated.
     * 
     * @param block The furnace block
     * @param fuel The fuel item to restore
     */
    private void restoreFuelToFurnace(Block block, ItemStack fuel) {
        BlockState state = block.getState();
        if (state instanceof Furnace) {
            Furnace furnace = (Furnace) state;
            FurnaceInventory inventory = furnace.getInventory();
            
            // Set the fuel in the fuel slot
            inventory.setFuel(fuel.clone());
            
            // Update the furnace state to persist changes
            furnace.update(true); // Force update with physics
        }
    }
    
    /**
     * Generates a unique location key for tracking furnaces.
     * 
     * @param block The block to generate key for
     * @return String key in format "world:x:y:z"
     */
    private String getLocationKey(Block block) {
        return block.getWorld().getName() + ":" + 
               block.getX() + ":" + 
               block.getY() + ":" + 
               block.getZ();
    }
    
    /**
     * Handles furnace break events to clean up tracking.
     * Prevents memory leaks by removing broken furnaces from active tracking.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        
        if (type == Material.FURNACE || 
            type == Material.BLAST_FURNACE || 
            type == Material.SMOKER) {
            
            String locationKey = getLocationKey(block);
            activeFurnaces.remove(locationKey);
            
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Furnace destroyed, stopped monitoring: " + locationKey);
            }
        }
    }
    
    /**
     * CRITICAL: Prevents infinite fuel from being taken out of furnace while burning.
     * This ensures the fuel item stays in the furnace at all times.
     * 
     * Priority: HIGHEST to block before any other plugin
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!configManager.isEnabled()) {
            return;
        }
        
        Inventory inventory = event.getInventory();
        
        // Only handle furnace inventories
        if (inventory.getType() != InventoryType.FURNACE &&
            inventory.getType() != InventoryType.BLAST_FURNACE &&
            inventory.getType() != InventoryType.SMOKER) {
            return;
        }
        
        // Check if clicking the fuel slot (slot 1 in furnace inventory)
        if (event.getRawSlot() != 1) {
            return;
        }
        
        ItemStack currentItem = event.getCurrentItem();
        
        // If current item in fuel slot has infinite fuel tag
        if (currentItem != null && itemUtils.hasInfiniteFuelTag(currentItem)) {
            
            Location furnaceLocation = inventory.getLocation();
            if (furnaceLocation == null) {
                return;
            }
            
            Block block = furnaceLocation.getBlock();
            BlockState state = block.getState();
            
            if (!(state instanceof Furnace)) {
                return;
            }
            
            Furnace furnace = (Furnace) state;
            
            // If furnace is currently burning, prevent removal
            if (furnace.getBurnTime() > 0) {
                event.setCancelled(true);
                
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("[DEBUG] Blocked attempt to remove infinite fuel from furnace");
                }
                
                // Restore fuel just in case
                String locationKey = getLocationKey(block);
                if (activeFurnaces.containsKey(locationKey)) {
                    ItemStack trackedFuel = activeFurnaces.get(locationKey);
                    
                    // Schedule restore on next tick
                    try {
                        Bukkit.getRegionScheduler().run(plugin, block.getLocation(), scheduledTask -> {
                            restoreFuelToFurnace(block, trackedFuel);
                        });
                    } catch (NoSuchMethodError | UnsupportedOperationException e) {
                        try {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                restoreFuelToFurnace(block, trackedFuel);
                            });
                        } catch (UnsupportedOperationException ex) {
                            restoreFuelToFurnace(block, trackedFuel);
                        }
                    }
                }
            }
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
