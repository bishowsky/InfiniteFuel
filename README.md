# InfiniteFuel

**Version:** 1.0.0  
**Author:** PuffMC  
**Compatibility:** Paper 1.21 - 1.21.11, Folia 1.21.8

## 📖 Description

InfiniteFuel is a Minecraft plugin that adds special items - **infinite fuel**, which never runs out in furnaces. The perfect solution for players who want to streamline the smelting process without worrying about fuel.

## ✨ Features

### 🔥 Infinite Fuel
- **Never runs out** - place it in a furnace and forget about fuel
- **Works in all furnaces**: Regular Furnace, Blast Furnace, Smoker
- **Configurable materials** - choose which materials can become infinite fuel
- **Persists properties** through server restarts and chunk unloads

### 🛡️ Balance Safeguards
- **Crafting block** - cannot be used in crafting table, anvil, smithing table, etc.
- **Hopper control** - optional blocking of automatic transfer via hoppers
- **Stack prevention** - limit of 1 piece in fuel slot (prevents waste)
- **Material validation** - automatic configuration correctness checking

### 🌍 Multi-language
- **Polish** (default)
- **English** (fallback)
- Easy addition of new languages through YAML files

### ⚙️ Configurability
Everything can be customized in `config.yml`:
- Allowed fuel materials
- Enable/disable features
- Hopper control
- Stack prevention
- Item name and description
- Interface language
- Default material type
## 📦 Installation

1. Download `InfiniteFuel.jar` from `app/build/libs/` folder
2. Place the file in the `plugins/` folder on your server
3. Restart the server or use `/reload confirm`
4. Configure the plugin in `plugins/InfiniteFuel/config.yml`
5. Use `/infinitefuel reload` after changing configuration

## 🎮 Commands

| Command | Alias | Description | Permission |
|---------|-------|-------------|------------|
| `/infinitefuel help` | `/ifuel help` | Shows help | - |
| `/infinitefuel reload` | `/ifuel reload` | Reloads configuration | `infinitefuel.reload` |
| `/infinitefuel give <player> <material> [amount]` | `/ifuel give` | Gives infinite fuel | `infinitefuel.give` |

### Usage Examples:
```
/ifuel give Steve COAL
/ifuel give Alex CHARCOAL 5
/ifuel give Notch COAL_BLOCK 1
/ifuel reload
```

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `infinitefuel.*` | op | Access to all features |
| `infinitefuel.use` | true | Use infinite fuel |
| `infinitefuel.reload` | op | Reload plugin |
| `infinitefuel.give` | op | Give infinite fuel |
| `infinitefuel.give` | op | Dawanie nieskończonego paliwa |

## ⚙️ Configuration

### config.yml

```yaml
language:
  locale: "pl_PL"  # pl_PL or en_US

infinite-fuel:
  enabled: true
  prevent-crafting: true  # Block crafting with infinite fuel
  allow-hopper-automation: false  # Can hoppers transfer?
  prevent-multi-stack: true  # Limit 1 piece in furnace
  default-material: "COAL"  # Default material type
  
  allowed-materials:  # Allowed base materials
    - COAL
    - CHARCOAL
    - COAL_BLOCK
    - DRIED_KELP_BLOCK
    - BLAZE_ROD
    - LAVA_BUCKET
    # ... more

item:
  name: "&6&lInfinite Fuel"
  lore:
    - "&7This magical fuel never runs out!"
    - "&c⚠ Cannot be used in crafting"
```

### Adding Custom Materials

Edit the `allowed-materials` section in `config.yml`:
```yaml
allowed-materials:
  - COAL
  - CHARCOAL
  - STICK  # Add your own
  - OAK_PLANKS  # Add your own
```

**Important:** Material names must be from the [official Material enum list](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html)
## 🔧 How It Works

### How does infinite fuel work?

1. **Item identification** - uses `PersistentDataContainer` (modern Paper API)
2. **Furnace recognition** - plugin listens to `FurnaceBurnEvent`
3. **Burn time setting** - `Integer.MAX_VALUE / 2` ticks (~3.4 years)
4. **No consumption** - item is never removed from the furnace

### Exploit Prevention

- **Crafting** - all types blocked: crafting table, anvil, smithing, grindstone, stonecutter, loom, cartography, brewing
- **Hoppers** - optional transfer blocking (config: `allow-hopper-automation`)
- **Stacking** - automatic limitation to 1 piece in fuel slot
- **Validation** - material correctness checking at server startup

## 🌐 Compatibility

### Supported versions:
- ✅ **Paper 1.21 - 1.21.11**
- ✅ **Folia 1.21.8**

### Tested environments:
- Java 21
- Paper 1.21.4
- Folia 1.21.8
- Gradle 8.14

### Requirements:
- Java 21 or newer
- Paper API 1.21+
- Paper or Folia server
## 📁 File Structure

After installation, the plugin will create the following structure:

```
plugins/
└── InfiniteFuel/
    ├── config.yml           # Main configuration
    └── lang/
        ├── pl_PL.yml        # Polish messages
        └── en_US.yml        # English messages
```

## 🐛 Troubleshooting

### Plugin won't start
- Check server console for errors
- Make sure you're using Java 21+
- Verify you're using Paper 1.21+ or Folia 1.21.8

### Infinite fuel doesn't work
- Use `/ifuel reload` after changing configuration
- Check that `infinite-fuel.enabled: true` in config.yml
- Ensure the item was created by the plugin (command `/ifuel give`)

### Materials not recognized
- Check material names on the [official list](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html)
- Names must be UPPERCASE_WITH_UNDERSCORES
- Plugin will automatically skip invalid materials and use defaults

### Hoppers transfer infinite fuel despite block
- Check setting `allow-hopper-automation: false` in config.yml
- Use `/ifuel reload` after changes

## 🔄 Updates

### How to update the plugin?

1. Stop the server
2. Remove old `InfiniteFuel.jar` from `plugins/` folder
3. Copy new `InfiniteFuel.jar`
4. Start the server
5. Check configuration for new options

**Note:** Configuration and data are preserved.i

## 📝 PuffMC Standards

Plugin created according to PuffMC standards:

- ✅ Polish as default language
- ✅ No emojis in messages
- ✅ JAR without version suffix (`InfiniteFuel.jar`)
- ✅ Package structure: `pl.puffmc.infinitefuel`
- ✅ Tab completion with permission filtering
- ✅ Pre-build validation
- ✅ Folia-safe scheduler patterns
- ✅ Paper 1.21+ command registration via `ServerLoadEvent`
- ✅ Modern Component API (no deprecated methods)

## 🏗️ Building from Source

### Requirements:
- Java 21 JDK
- Gradle 8.14 (wrapper included)

### Steps:

```bash
# Clone project
cd InfiniteFuel

# Build
./gradlew clean build

# Output: app/build/libs/InfiniteFuel.jar
```

## 📄 License

Copyright © 2025 PuffMC. All rights reserved.

---

## 💬 Support

If you have questions or problems:
1. Check the **Troubleshooting** section
2. Read server logs in console
3. Check configuration in `config.yml`
4. Review TECHNICAL.md for implementation details

---

**Created with ❤️ for the PuffMC community**

**Stworzono z ❤️ dla społeczności PuffMC**
