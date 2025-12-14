# InfiniteFuel - Technical Documentation

## Architecture Overview

### Package Structure

```
pl.puffmc.infinitefuel/
├── InfiniteFuel.java          # Main plugin class
├── commands/
│   └── InfiniteFuelCommand.java
├── listeners/
│   ├── FurnaceListener.java
│   ├── CraftingListener.java
│   └── InventoryListener.java
├── managers/
│   ├── ConfigManager.java
│   ├── MessageManager.java
│   └── ItemFactory.java
└── utils/
    ├── ItemUtils.java
    └── MaterialValidator.java
```

## Core Components

### 1. Main Plugin Class (`InfiniteFuel.java`)

**Responsibilities:**
- Plugin lifecycle management (onEnable/onDisable)
- Manager initialization
- Listener registration
- Command registration via ServerLoadEvent

**Key Features:**
- Implements `Listener` for ServerLoadEvent (Paper 1.21+ requirement)
- Registers commands AFTER server loads to comply with Paper API restrictions
- Provides public getters for managers (dependency injection pattern)

**Initialization Order:**
```java
1. Save default config
2. Initialize managers (ConfigManager, MessageManager, ItemUtils, etc.)
3. Validate materials from config
4. Register event listeners
5. Register self as listener for ServerLoadEvent
6. Command registration happens in ServerLoadEvent handler
```

### 2. Managers

#### ConfigManager
- Loads and manages `config.yml`
- Provides convenience methods for common config values
- Handles config reloading

**Thread Safety:** Not thread-safe, should only be accessed from main thread

#### MessageManager
- Multi-language support (pl_PL, en_US)
- Message loading with locale fallback
- Color code translation (`&` → `§`)
- Placeholder replacement system
- Message caching for performance

**Placeholder Support:**
- `{material}` - Material name
- `{player}` - Player name
- `{amount}` - Item amount

#### ItemFactory
- Creates infinite fuel items with proper metadata
- Applies PersistentDataContainer tags
- Formats material names for display
- Validates item creation

### 3. Utilities

#### ItemUtils
- PersistentDataContainer operations
- Infinite fuel tag management
- Uses `NamespacedKey("infinitefuel", "infinite_fuel")`

**Tag Structure:**
```java
Key: "infinitefuel:infinite_fuel"
Type: PersistentDataType.STRING
Value: "true"
```

**Why PersistentDataContainer?**
- Survives server restarts
- Persists through chunk unloads
- Works with item transfers (hoppers, chests, etc.)
- No reliance on item name/lore (players can rename with anvil if allowed)

#### MaterialValidator
- Validates material names from config against Material enum
- Provides fallback to default materials
- Logs warnings for invalid materials
- Generates material name lists for tab completion

### 4. Event Listeners

#### FurnaceListener
**Events Handled:**
- `FurnaceBurnEvent` (HIGH priority)
- `FurnaceSmeltEvent` (MONITOR priority)

**Burn Mechanics:**
```java
// When infinite fuel is detected:
event.setBurnTime(Integer.MAX_VALUE / 2);

// Why MAX_VALUE / 2?
// - Integer.MAX_VALUE = 2,147,483,647 ticks
// - At 20 ticks/second = ~3.4 years of burn time
// - Divided by 2 to avoid potential overflow in Minecraft internals
// - Effectively infinite for gameplay purposes
```

**Supported Furnace Types:**
- `Material.FURNACE`
- `Material.BLAST_FURNACE`
- `Material.SMOKER`

#### CraftingListener
**Purpose:** Prevent infinite fuel from being used in any crafting/modification interface

**Events Handled:**
- `PrepareItemCraftEvent` - Crafting table
- `PrepareAnvilEvent` - Anvil
- `PrepareSmithingEvent` - Smithing table (1.19 & 1.20+ compatible)
- `PrepareGrindstoneEvent` - Grindstone
- `InventoryClickEvent` - Stonecutter, Loom, Cartography table
- `BrewEvent` - Brewing stand

**Cancellation Method:**
```java
// For PrepareXEvent:
event.setResult(null); // or new ItemStack(Material.AIR)

// For InventoryClickEvent:
event.setCancelled(true);

// For BrewEvent:
event.setCancelled(true);
```

#### InventoryListener
**Purpose:** Control hopper automation and prevent multi-stacking

**Events Handled:**
- `InventoryMoveItemEvent` - Hopper transfers
- `InventoryClickEvent` - Furnace fuel slot clicks

**Multi-Stack Prevention Logic:**
```java
// Furnace slots: 0=input, 1=fuel, 2=result

if (slot == 1 && itemIsInfiniteFuel) {
    if (amount > 1) {
        // Place only 1 item
        // Reduce cursor/source by 1
        // Notify player
    }
}
```

**Shift-Click Handling:**
- Detects shift-click from player inventory to furnace
- Manually places 1 item in fuel slot
- Reduces source stack by 1
- Better UX than blocking entirely

### 5. Commands

#### InfiniteFuelCommand
**Implements:** `CommandExecutor`, `TabCompleter`

**Subcommands:**
1. **reload** - Reloads config, messages, re-validates materials
2. **give** - Creates infinite fuel item and gives to player
3. **help** - Shows command help

**Tab Completion Features:**
- Permission-filtered suggestions (only shows allowed commands)
- Online player name completion
- Material name completion from validated list
- Amount suggestions (1, 8, 16, 32, 64)
- Case-insensitive partial matching
- Alphabetically sorted results

**Validation Chain (give command):**
```
1. Check permission → infinitefuel.give
2. Validate player exists and is online
3. Validate material name against Material enum
4. Validate material is in allowed-materials list
5. Validate amount (1-64)
6. Check player inventory has space
7. Create item via ItemFactory
8. Add to player inventory
9. Send confirmation messages
```

## Configuration System

### config.yml Schema

```yaml
language:
  locale: string (pl_PL | en_US)

infinite-fuel:
  enabled: boolean
  prevent-crafting: boolean
  allow-hopper-automation: boolean
  prevent-multi-stack: boolean
  allowed-materials: string[]

item:
  name: string (with & color codes)
  lore: string[] (with & color codes, supports {material} placeholder)
```

### Message Files Schema

```yaml
messages:
  prefix: string
  reload-success: string
  # ... all message keys

commands:
  help-header: string
  help-reload: string
  help-give: string
  usage-reload: string
  usage-give: string
```

## Technical Decisions

### Why PersistentDataContainer?
- **Persistent:** Survives restarts, chunk unloads
- **Reliable:** Official Bukkit API, well-tested
- **Flexible:** Can store various data types
- **Safe:** Doesn't rely on names/lore that players can modify

### Why Integer.MAX_VALUE / 2 for burn time?
- **Effectively infinite:** ~3.4 years real-time
- **Overflow safe:** Avoids potential integer overflow bugs
- **Server-friendly:** Doesn't trigger special handling for MAX_VALUE

### Why ServerLoadEvent for command registration?
Paper 1.21+ restriction:
```java
// ❌ WRONG - Throws error in onEnable()
@Override
public void onEnable() {
    getCommand("infinitefuel").setExecutor(...);
}

// ✅ CORRECT - Use ServerLoadEvent
@EventHandler
public void onServerLoad(ServerLoadEvent event) {
    getCommand("infinitefuel").setExecutor(...);
}
```

### Why separate listeners instead of one big class?
- **Single Responsibility Principle:** Each listener has one job
- **Easier testing:** Can test furnace mechanics independently from crafting prevention
- **Better organization:** Clear separation of concerns
- **Performance:** Only relevant events are checked

## Performance Considerations

### Message Caching
```java
private final Map<String, String> messageCache;

// Messages are cached after first access
// Cleared on reload
```

### Material Validation at Startup
```java
// Materials validated once at startup
// Cached in List<Material>
// Re-validated on reload
```

### Event Priority
```java
@EventHandler(priority = EventPriority.HIGH)
// Processes before most other plugins
// Ensures infinite fuel logic runs early
```

### Minimized Database Operations
- No database needed - all data stored in PersistentDataContainer
- No async operations required (except future extensions)

## Error Handling

### Material Validation
```java
try {
    Material.valueOf(materialName);
} catch (IllegalArgumentException e) {
    // Log warning
    // Add to invalid list
    // Continue validation
}

// If all invalid → use defaults
```

### Config Reload
```java
try {
    configManager.reload();
    messageManager.reload();
    // Re-validate materials
} catch (Exception e) {
    // Log error
    // Keep old config
    // Notify sender
}
```

### Item Creation
```java
ItemStack item = itemFactory.createInfiniteFuel(material, amount);
if (item == null) {
    // Log error
    // Notify sender
    // Return early
}
```

## Compatibility Notes

### Paper 1.21+
- Uses `ServerLoadEvent` for command registration
- Modern Material enum (no legacy materials)
- PersistentDataContainer API

### Folia 1.21.8
- No async scheduler usage (all operations are sync)
- No world access from async context
- Event-driven architecture (no scheduled tasks)

### Version Range (1.21 - 1.21.11)
- API-version: 1.21 in plugin.yml
- No version-specific code required
- Material enum is stable across minor versions

## Extension Points

### Adding New Fuel Types
1. Add material to `allowed-materials` in config.yml
2. Reload config with `/ifuel reload`
3. Material validator will automatically validate

### Adding New Languages
1. Create `lang/xx_YY.yml` (e.g., `de_DE.yml`)
2. Copy structure from `pl_PL.yml`
3. Translate all message keys
4. Set `language.locale: de_DE` in config.yml

### Adding Custom Events
Example: Bonus items when smelting with infinite fuel
```java
@EventHandler
public void onFurnaceSmelt(FurnaceSmeltEvent event) {
    // Check if using infinite fuel
    // Add bonus items to result
}
```

### Adding Permissions for Specific Materials
```java
// In InfiniteFuelCommand.java, modify handleGive():
if (!sender.hasPermission("infinitefuel.give." + material.name().toLowerCase())) {
    // Deny
}
```

## Build System

### Gradle Configuration
```kotlin
plugins {
    `java-library`  // No Shadow needed (no heavy dependencies)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // No runtime dependencies - keeps JAR small
}

tasks {
    jar {
        archiveFileName.set("InfiniteFuel.jar")  // PuffMC standard
    }
}
```

### Why No Shadow Plugin?
- No HikariCP, H2, or other heavy dependencies
- Only uses Paper API (compile-only)
- Smaller JAR size (~50 KB vs 15+ MB)
- Faster build times
- Simpler configuration

## Testing Checklist

### Functional Tests
- [ ] Infinite fuel burns indefinitely in furnace
- [ ] Infinite fuel burns indefinitely in blast furnace
- [ ] Infinite fuel burns indefinitely in smoker
- [ ] Crafting with infinite fuel is blocked
- [ ] Anvil usage with infinite fuel is blocked
- [ ] Smithing with infinite fuel is blocked
- [ ] Grindstone usage is blocked
- [ ] Hopper automation respects config setting
- [ ] Multi-stack prevention works in furnace fuel slot
- [ ] Shift-click places only 1 item

### Command Tests
- [ ] `/ifuel help` shows correct messages
- [ ] `/ifuel reload` reloads config and messages
- [ ] `/ifuel give` creates correct items
- [ ] Tab completion shows only permitted options
- [ ] Permission checks work correctly

### Configuration Tests
- [ ] Invalid materials are logged and skipped
- [ ] Default materials are used if all invalid
- [ ] Locale switching works (pl_PL ↔ en_US)
- [ ] Config reload updates all settings

### Compatibility Tests
- [ ] Works on Paper 1.21.4
- [ ] Works on Paper 1.21.11 (if available)
- [ ] Works on Folia 1.21.8
- [ ] PersistentData survives server restart
- [ ] PersistentData survives chunk unload

## Known Limitations

1. **No multi-version support** - Only supports 1.21+
2. **No database** - All data in item metadata (scales well for this use case)
3. **No API for other plugins** - Self-contained (can be extended if needed)
4. **English messages less detailed** - Polish is primary language

## Future Enhancement Ideas

- [ ] Statistics tracking (total smelts, total fuel saved)
- [ ] Custom burn times per material
- [ ] Bonus item drops when using infinite fuel
- [ ] Integration with economy plugins (cost to create infinite fuel)
- [ ] GUI for giving infinite fuel
- [ ] Infinite fuel durability (consumes after X uses)
- [ ] Per-world enable/disable

---

**Last Updated:** 2025-12-14  
**Plugin Version:** 1.0.0  
**Target Minecraft:** 1.21 - 1.21.11 (Paper & Folia)
