package pl.yourserver.bloodChestPlugin.loot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.yourserver.bloodChestPlugin.util.ConfigUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LootLoader {

    public LootRegistry load(File file) throws IOException, InvalidConfigurationException {
        if (!file.exists()) {
            throw new IOException("Missing loot configuration " + file.getAbsolutePath());
        }
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);

        ConfigurationSection defaults = configuration.getConfigurationSection("defaults");
        int defaultMin = defaults == null ? 1 : defaults.getInt("min-amount", 1);
        int defaultMax = defaults == null ? 1 : defaults.getInt("max-amount", defaultMin);
        double defaultWeight = defaults == null ? 1.0 : defaults.getDouble("weight", 1.0D);
        int defaultRolls = defaults == null ? 1 : defaults.getInt("rolls", 1);
        double defaultPityWeight = defaults == null ? 1.0 : defaults.getDouble("pity-weight", 1.0D);

        ConfigurationSection lootSection = configuration.getConfigurationSection("loot");
        if (lootSection == null) {
            throw new InvalidConfigurationException("items.yml is missing the loot section");
        }

        List<LootItemDefinition> items = new ArrayList<>();
        for (String categoryKey : lootSection.getKeys(false)) {
            ConfigurationSection categorySection = lootSection.getConfigurationSection(categoryKey);
            if (categorySection == null) {
                continue;
            }
            String category = categoryKey.toUpperCase(Locale.ROOT);
            for (String id : categorySection.getKeys(false)) {
                ConfigurationSection itemSection = categorySection.getConfigurationSection(id);
                if (itemSection == null) {
                    continue;
                }
                Material material = ConfigUtil.readMaterial(itemSection.getString("material"), Material.PAPER);
                String name = itemSection.getString("display-name", id);
                List<String> lore = itemSection.getStringList("lore");
                if (lore.isEmpty()) {
                    String loreLine = itemSection.getString("lore-line");
                    if (loreLine != null) {
                        lore = List.of(loreLine);
                    }
                }
                int min = itemSection.getInt("min-amount", defaultMin);
                int max = itemSection.getInt("max-amount", defaultMax);
                double weight = itemSection.getDouble("weight", defaultWeight);
                int rolls = itemSection.getInt("rolls", defaultRolls);
                double pityWeight = itemSection.getDouble("pity-weight", defaultPityWeight);

                ConfigurationSection optionsSection = itemSection.getConfigurationSection("options");
                boolean hideFlags = readBooleanOption(optionsSection, "hideflags");
                boolean hideAttributes = readBooleanOption(optionsSection, "hideattributes");
                boolean unbreakable = readBooleanOption(optionsSection, "unbreakable");

                Map<Enchantment, Integer> enchantments = parseEnchantments(itemSection.getStringList("enchantments"));

                items.add(new LootItemDefinition(id,
                        material,
                        name,
                        category,
                        lore,
                        min,
                        max,
                        weight,
                        rolls,
                        pityWeight,
                        hideFlags,
                        hideAttributes,
                        unbreakable,
                        enchantments));
            }
        }

        Collection<String> pityPool = configuration.getStringList("pity-pool");

        return new LootRegistry(items, pityPool);
    }

    private boolean readBooleanOption(ConfigurationSection section, String key) {
        if (section == null) {
            return false;
        }
        for (String optionKey : section.getKeys(false)) {
            if (optionKey != null && optionKey.replace("-", "").equalsIgnoreCase(key)) {
                return section.getBoolean(optionKey);
            }
        }
        return false;
    }

    private Map<Enchantment, Integer> parseEnchantments(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":", 2);
            String name = parts[0].trim();
            if (name.isEmpty()) {
                continue;
            }
            int level = 1;
            if (parts.length > 1) {
                String levelPart = parts[1].trim();
                if (!levelPart.isEmpty()) {
                    try {
                        level = Math.max(1, Integer.parseInt(levelPart));
                    } catch (NumberFormatException ex) {
                        level = 1;
                    }
                }
            }
            Enchantment enchantment = resolveEnchantment(name);
            if (enchantment == null) {
                Bukkit.getLogger().warning(String.format(Locale.ROOT,
                        "[BloodChest] Unknown enchantment '%s' in items.yml", name));
                continue;
            }
            enchantments.put(enchantment, level);
        }
        return enchantments.isEmpty() ? Map.of() : Map.copyOf(enchantments);
    }

    private Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        NamespacedKey namespacedKey = NamespacedKey.fromString(normalized);
        Enchantment enchantment = namespacedKey != null ? Enchantment.getByKey(namespacedKey) : null;
        if (enchantment != null) {
            return enchantment;
        }
        return Enchantment.getByName(normalized.toUpperCase(Locale.ROOT));
    }
}
