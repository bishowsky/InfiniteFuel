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
import org.bukkit.event.inventory.FurnaceSmeltEvent;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    
    // Folia-compatible scheduler for periodic tasks
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> monitoringTask;
    
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
        
        // Create daemon thread for Folia-compatible background tasks
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "InfiniteFuel-FurnaceMonitor");
            thread.setDaemon(true);
            return thread;
        });
        
        // Start global monitoring task that checks ALL tracked furnaces every tick
        startGlobalMonitoring();
    }
    
    /**
     * CRITICAL: Global monitoring task that runs EVERY TICK
     * This ensures burn time NEVER reaches 0 and fuel is NEVER consumed
     * FOLIA COMPATIBLE: Uses ScheduledExecutorService instead of BukkitScheduler
     */
    private void startGlobalMonitoring() {
        // Schedule at fixed rate: 50ms = 1 tick (20 times per second)
        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            if (!configManager.isEnabled()) {
                return;
            }
            
            // Check all tracked furnaces
            for (Map.Entry<String, ItemStack> entry : activeFurnaces.entrySet()) {
                String locationKey = entry.getKey();
                ItemStack trackedFuel = entry.getValue();
                
                // Parse location from key
                String[] parts = locationKey.split(":");
                if (parts.length != 4) continue;
                
                try {
                    org.bukkit.World world = Bukkit.getWorld(parts[0]);
                    if (world == null) continue;
                    
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    
                    Location loc = new Location(world, x, y, z);
                    
                    // Schedule sync task on region thread for this location
                    try {
                        Bukkit.getRegionScheduler().run(plugin, loc, scheduledTask -> {
                            checkAndFixFurnace(loc, locationKey, trackedFuel);
                        });
                    } catch (NoSuchMethodError | UnsupportedOperationException e) {
                        // Folia not available, use global scheduler
                        try {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                checkAndFixFurnace(loc, locationKey, trackedFuel);
                            });
                        } catch (UnsupportedOperationException ex) {
                            // Both failed, run directly (not recommended but fallback)
                            checkAndFixFurnace(loc, locationKey, trackedFuel);
                        }
                    }
                } catch (Exception e) {
                    // Invalid location, skip
                }
            }
        }, 50, 50, TimeUnit.MILLISECONDS); // Run every 50ms = 1 tick
    }
    
    /**
     * Checks and fixes a furnace's fuel and burn time
     * Must be called on region thread for the furnace location
     */
    private void checkAndFixFurnace(Location loc, String locationKey, ItemStack trackedFuel) {
        Block block = loc.getBlock();
        BlockState state = block.getState();
        
        if (!(state instanceof Furnace)) {
            return;
        }
        
        Furnace furnace = (Furnace) state;
        FurnaceInventory inventory = furnace.getInventory();
        
        // SAFETY CHECK 1: Restore fuel if missing
        ItemStack currentFuel = inventory.getFuel();
        if (currentFuel == null || currentFuel.getAmount() == 0 || !itemUtils.hasInfiniteFuelTag(currentFuel)) {
            inventory.setFuel(trackedFuel.clone());
            furnace.update(true);
            
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("[DEBUG] CRITICAL: Restored missing fuel at " + locationKey);
            }
        }
        
        // SAFETY CHECK 2: Keep burn time ALWAYS high
        short burnTime = furnace.getBurnTime();
        if (burnTime > 0 && burnTime < 1000) { // Less than 50 seconds
            furnace.setBurnTime(Short.MAX_VALUE);
            furnace.update(true);
            
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] URGENT: Reset burn time at " + locationKey + 
                                      " (was dangerously low: " + burnTime + ")");
            }
        }
    }
    
    /**
     * Handles furnace burn events to implement TRUE infinite fuel.
     * 
     * CRITICAL: This event fires when furnace tries to CONSUME fuel
     * We MUST CANCEL to prevent item consumption, then manually set burn time
     * 
     * Priority: HIGHEST to cancel before fuel is consumed
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
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
        
        // CRITICAL FIX: CANCEL the event to prevent fuel consumption
        // Minecraft will NOT decrease item amount if we cancel
        event.setCancelled(true);
        
        // Track this furnace as active
        activeFurnaces.put(locationKey, fuelCopy);
        
        // Manually start burning by setting burn time on the furnace block
        try {
            Bukkit.getRegionScheduler().run(plugin, block.getLocation(), scheduledTask -> {
                startManualBurning(block, fuelCopy);
            });
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    startManualBurning(block, fuelCopy);
                });
            } catch (UnsupportedOperationException ex) {
                startManualBurning(block, fuelCopy);
            }
        }
        
        // Debug logging
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Infinite fuel activated at " + locationKey + 
                                  " (event cancelled, manual burning started)");
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
     * Manually starts burning in a furnace without consuming fuel item.
     * Called after cancelling FurnaceBurnEvent to prevent fuel consumption.
     * 
     * @param block The furnace block
     * @param fuel The infinite fuel item
     */
    private void startManualBurning(Block block, ItemStack fuel) {
        BlockState state = block.getState();
        if (!(state instanceof Furnace)) {
            return;
        }
        
        Furnace furnace = (Furnace) state;
        FurnaceInventory inventory = furnace.getInventory();
        
        // Ensure fuel is in slot (should be there, but double-check)
        ItemStack currentFuel = inventory.getFuel();
        if (currentFuel == null || currentFuel.getAmount() == 0 || !itemUtils.hasInfiniteFuelTag(currentFuel)) {
            inventory.setFuel(fuel.clone());
        }
        
        // Set maximum burn time to start burning
        furnace.setBurnTime(Short.MAX_VALUE);
        
        // Update the furnace state
        furnace.update(true);
        
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("[DEBUG] Manual burning started at " + getLocationKey(block) + 
                                  " (burn time set to: " + Short.MAX_VALUE + ")");
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
     * CRITICAL: Monitor burn time on every smelt operation
     * FurnaceSmeltEvent fires every time an item finishes cooking
     * This ensures burn time NEVER drops low enough to consume the fuel item
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
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
        
        // Check if this furnace has infinite fuel
        if (fuel == null || !itemUtils.hasInfiniteFuelTag(fuel)) {
            return;
        }
        
        String locationKey = getLocationKey(block);
        
        // Track this furnace
        if (!activeFurnaces.containsKey(locationKey)) {
            activeFurnaces.put(locationKey, fuel.clone());
        }
        
        // CRITICAL SAFETY NET #1: Restore fuel if it's missing
        // This catches any edge case where fuel was consumed
        if (fuel == null || fuel.getAmount() == 0) {
            ItemStack trackedFuel = activeFurnaces.get(locationKey);
            if (trackedFuel != null) {
                inventory.setFuel(trackedFuel.clone());
                furnace.update(true);
                
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("[DEBUG] EMERGENCY: Restored consumed fuel at " + locationKey);
                }
            }
        }
        
        // CRITICAL SAFETY NET #2: ALWAYS reset burn time to maximum after each smelt
        // This prevents burn time from EVER reaching 0 and consuming the fuel
        short currentBurnTime = furnace.getBurnTime();
        
        // Reset to max if burn time is getting low (below 1000 = ~50 seconds)
        // VERY LOW threshold = ultra-frequent resets = fuel ABSOLUTELY NEVER consumed
        if (currentBurnTime < 1000) {
            furnace.setBurnTime(Short.MAX_VALUE);
            furnace.update(true);
            
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("[DEBUG] Reset burn time after smelt at " + locationKey + 
                                      " (was: " + currentBurnTime + ", now: " + Short.MAX_VALUE + ")");
            }
        }
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
    
    /**
     * Shuts down the monitoring task and executor service.
     * CRITICAL: Must be called in plugin onDisable() to prevent memory leaks.
     */
    public void shutdown() {
        // Cancel monitoring task
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel(false);
        }
        
        // Shutdown executor service
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear tracking
        activeFurnaces.clear();
    }
}
