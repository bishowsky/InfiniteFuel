package pl.puffmc.infinitefuel.utils;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Validates material names from configuration against available Minecraft materials.
 * Provides fallback to default materials if configuration contains invalid entries.
 */
public class MaterialValidator {
    
    private final Plugin plugin;
    
    /**
     * Default materials that are valid fuel sources in Minecraft.
     * Used as fallback if configuration validation fails.
     */
    private static final List<String> DEFAULT_MATERIALS = Arrays.asList(
        "COAL",
        "CHARCOAL", 
        "COAL_BLOCK",
        "DRIED_KELP_BLOCK",
        "BLAZE_ROD",
        "LAVA_BUCKET"
    );
    
    /**
     * Creates a new MaterialValidator instance.
     * 
     * @param plugin The plugin instance for logging
     */
    public MaterialValidator(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Validates a list of material names from configuration.
     * Invalid materials are logged as warnings and excluded from the result.
     * If all materials are invalid, returns default materials list.
     * 
     * @param materialNames List of material names from config
     * @return List of valid Material objects
     */
    public List<Material> validateMaterials(List<String> materialNames) {
        if (materialNames == null || materialNames.isEmpty()) {
            plugin.getLogger().warning("Brak materiałów w konfiguracji. Używanie domyślnych materiałów.");
            return getDefaultMaterials();
        }
        
        List<Material> validMaterials = new ArrayList<>();
        List<String> invalidMaterials = new ArrayList<>();
        
        for (String materialName : materialNames) {
            if (materialName == null || materialName.trim().isEmpty()) {
                continue;
            }
            
            String normalized = materialName.trim().toUpperCase();
            
            try {
                Material material = Material.valueOf(normalized);
                
                // Additional validation: check if material exists (not air/legacy)
                if (material.isAir()) {
                    plugin.getLogger().warning("Materiał '" + materialName + "' jest AIR i został pominięty.");
                    invalidMaterials.add(materialName);
                    continue;
                }
                
                validMaterials.add(material);
                
            } catch (IllegalArgumentException e) {
                // Material doesn't exist in current version
                invalidMaterials.add(materialName);
                plugin.getLogger().warning("Nieznany materiał w konfiguracji: '" + materialName + "' - został pominięty.");
            }
        }
        
        // Log summary if there were invalid materials
        if (!invalidMaterials.isEmpty()) {
            plugin.getLogger().warning("Znaleziono " + invalidMaterials.size() + 
                " nieprawidłowych materiałów w konfiguracji: " + String.join(", ", invalidMaterials));
        }
        
        // If no valid materials found, use defaults
        if (validMaterials.isEmpty()) {
            plugin.getLogger().warning("Wszystkie materiały w konfiguracji są nieprawidłowe. Używanie domyślnych materiałów.");
            return getDefaultMaterials();
        }
        
        plugin.getLogger().info("Załadowano " + validMaterials.size() + " prawidłowych materiałów paliwa.");
        return validMaterials;
    }
    
    /**
     * Gets the default list of valid fuel materials.
     * 
     * @return List of default Material objects
     */
    public List<Material> getDefaultMaterials() {
        List<Material> defaults = new ArrayList<>();
        
        for (String materialName : DEFAULT_MATERIALS) {
            try {
                Material material = Material.valueOf(materialName);
                if (!material.isAir()) {
                    defaults.add(material);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Domyślny materiał '" + materialName + "' nie istnieje w tej wersji Minecrafta.", e);
            }
        }
        
        return defaults;
    }
    
    /**
     * Checks if a material is in the validated list.
     * 
     * @param material The material to check
     * @param validMaterials The list of valid materials
     * @return true if the material is valid for infinite fuel
     */
    public boolean isValidFuelMaterial(Material material, List<Material> validMaterials) {
        return material != null && validMaterials.contains(material);
    }
    
    /**
     * Gets a list of material names as strings for display/tab completion.
     * 
     * @param materials List of Material objects
     * @return List of material names as strings
     */
    public List<String> getMaterialNames(List<Material> materials) {
        List<String> names = new ArrayList<>();
        for (Material material : materials) {
            names.add(material.name());
        }
        return names;
    }
}
