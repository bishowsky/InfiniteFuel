package pl.puffmc.infinitefuel.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import pl.puffmc.infinitefuel.managers.ConfigManager;
import pl.puffmc.infinitefuel.managers.ItemFactory;
import pl.puffmc.infinitefuel.managers.MessageManager;
import pl.puffmc.infinitefuel.utils.MaterialValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for InfiniteFuel plugin.
 * Supports: /infinitefuel reload, /infinitefuel give <player> <material> [amount]
 */
public class InfiniteFuelCommand implements CommandExecutor, TabCompleter {
    
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ItemFactory itemFactory;
    private final MaterialValidator materialValidator;
    private List<Material> validMaterials;
    
    /**
     * Creates a new InfiniteFuelCommand.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager
     * @param messageManager The message manager
     * @param itemFactory The item factory
     * @param materialValidator The material validator
     * @param validMaterials List of valid fuel materials
     */
    public InfiniteFuelCommand(Plugin plugin, ConfigManager configManager, 
                              MessageManager messageManager, ItemFactory itemFactory,
                              MaterialValidator materialValidator, List<Material> validMaterials) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.itemFactory = itemFactory;
        this.materialValidator = materialValidator;
        this.validMaterials = validMaterials;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments - show help
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            
            case "give":
                return handleGive(sender, args);
            
            case "help":
                sendHelp(sender);
                return true;
            
            default:
                sender.sendMessage(messageManager.getMessageWithPrefix("messages.unknown-command"));
                sendHelp(sender);
                return true;
        }
    }
    
    /**
     * Handles the reload subcommand.
     * Reloads configuration and messages, validates materials.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("infinitefuel.reload")) {
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.no-permission"));
            return true;
        }
        
        try {
            // Reload config
            configManager.reload();
            
            // Reload messages
            messageManager.reload();
            
            // Re-validate materials
            List<String> materialNames = configManager.getAllowedMaterialNames();
            validMaterials = materialValidator.validateMaterials(materialNames);
            
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.reload-success"));
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.reload-failed"));
            plugin.getLogger().severe("Błąd podczas przeładowania konfiguracji: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    /**
     * Handles the give subcommand.
     * Gives infinite fuel item to a player.
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infinitefuel.give")) {
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.no-permission"));
            return true;
        }
        
        // Usage: /infinitefuel give <player> <material> [amount]
        if (args.length < 3) {
            sender.sendMessage(messageManager.getMessageWithPrefix("commands.usage-give"));
            return true;
        }
        
        // Get target player
        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null || !target.isOnline()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", playerName);
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.player-not-found", placeholders));
            return true;
        }
        
        // Get material
        String materialName = args[2].toUpperCase();
        Material material;
        
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("material", materialName);
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.invalid-material", placeholders));
            return true;
        }
        
        // Validate material is in allowed list
        if (!materialValidator.isValidFuelMaterial(material, validMaterials)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("material", materialName);
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.invalid-material", placeholders));
            return true;
        }
        
        // Get amount (default 1)
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(messageManager.getMessageWithPrefix("messages.invalid-amount"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(messageManager.getMessageWithPrefix("messages.invalid-amount"));
                return true;
            }
        }
        
        // Create infinite fuel item
        ItemStack item = itemFactory.createInfiniteFuel(material, amount);
        
        if (item == null) {
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.item-creation-failed"));
            return true;
        }
        
        // Check if player has inventory space
        if (target.getInventory().firstEmpty() == -1) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            sender.sendMessage(messageManager.getMessageWithPrefix("messages.inventory-full", placeholders));
            return true;
        }
        
        // Give item to player
        target.getInventory().addItem(item);
        
        // Send success messages
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("material", materialName);
        placeholders.put("amount", String.valueOf(amount));
        
        sender.sendMessage(messageManager.getMessageWithPrefix("messages.item-given", placeholders));
        
        // Notify target player
        target.sendMessage(messageManager.getMessageWithPrefix("messages.item-received", placeholders));
        
        return true;
    }
    
    /**
     * Sends help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("commands.help-header"));
        
        if (sender.hasPermission("infinitefuel.reload")) {
            sender.sendMessage(messageManager.getMessage("commands.help-reload"));
        }
        
        if (sender.hasPermission("infinitefuel.give")) {
            sender.sendMessage(messageManager.getMessage("commands.help-give"));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - subcommands with permission filtering
            if (sender.hasPermission("infinitefuel.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("infinitefuel.give")) {
                completions.add("give");
            }
            completions.add("help");
            
            return filterCompletions(completions, args[0]);
            
        } else if (args.length == 2) {
            // Second argument - player names for "give" command
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") && sender.hasPermission("infinitefuel.give")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
                return filterCompletions(completions, args[1]);
            }
            
        } else if (args.length == 3) {
            // Third argument - material names for "give" command
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") && sender.hasPermission("infinitefuel.give")) {
                completions = materialValidator.getMaterialNames(validMaterials);
                return filterCompletions(completions, args[2]);
            }
            
        } else if (args.length == 4) {
            // Fourth argument - amount suggestions for "give" command
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") && sender.hasPermission("infinitefuel.give")) {
                completions.add("1");
                completions.add("8");
                completions.add("16");
                completions.add("32");
                completions.add("64");
                return filterCompletions(completions, args[3]);
            }
        }
        
        return completions;
    }
    
    /**
     * Filters completions based on partial user input (case-insensitive).
     * 
     * @param completions List of all possible completions
     * @param partial The partial input from user
     * @return Filtered and sorted list of completions
     */
    private List<String> filterCompletions(List<String> completions, String partial) {
        String partialLower = partial.toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partialLower))
                .sorted()
                .toList();
    }
    
    /**
     * Updates the valid materials list (called after reload).
     * 
     * @param newValidMaterials The new list of valid materials
     */
    public void updateValidMaterials(List<Material> newValidMaterials) {
        this.validMaterials = newValidMaterials;
    }
}
