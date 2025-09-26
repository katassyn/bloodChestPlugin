package pl.yourserver.bloodChestPlugin.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ArenaSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ArenaSlot;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ChestSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.GuiSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.KeyRequirement;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MenuButton;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MobSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MobSettings.SpawnMode;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.PitySettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.RewardSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.SchematicSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.SpawnLocation;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.TimeThreshold;
import pl.yourserver.bloodChestPlugin.util.ConfigUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ConfigurationLoader {

    public PluginConfiguration load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");

        String permission = config.getString("permission", "bloodchest.use");

        GuiSettings guiSettings = readGuiSettings(config.getConfigurationSection("gui"));
        KeyRequirement keyRequirement = readKeyRequirement(config.getConfigurationSection("key"));
        PitySettings pitySettings = readPitySettings(config.getConfigurationSection("pity"));
        ArenaSettings arenaSettings = readArenaSettings(config.getConfigurationSection("arena"));
        RewardSettings rewardSettings = readRewardSettings(config.getConfigurationSection("rewards"));

        return new PluginConfiguration(permission, guiSettings, keyRequirement, pitySettings, arenaSettings, rewardSettings);
    }

    private GuiSettings readGuiSettings(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalStateException("Missing gui section in configuration");
        }

        String title = section.getString("title", "&8Blood Chest");
        List<String> instructions = section.getStringList("instructions");

        MenuButton startButton = readMenuButton(section.getConfigurationSection("start-button"), 13, Material.NETHER_STAR,
                "&cStart Blood Chest", List.of("&7Consumes the required key"));
        MenuButton lootButton = readMenuButton(section.getConfigurationSection("loot-button"), 15, Material.CHEST,
                "&6Possible rewards", List.of("&7View the available loot"));
        MenuButton closeButton = readMenuButton(section.getConfigurationSection("close-button"), 26, Material.BARRIER,
                "&cClose", List.of("&7Close the menu"));
        MenuButton backButton = readMenuButton(section.getConfigurationSection("back-button"), 45, Material.ARROW,
                "&eBack", List.of("&7Return to the previous view"));

        return new GuiSettings(title, instructions, startButton, lootButton, closeButton, backButton);
    }

    private MenuButton readMenuButton(ConfigurationSection section, int defaultSlot, Material defaultMaterial,
                                       String defaultName, List<String> defaultLore) {
        if (section == null) {
            return new MenuButton(defaultSlot, defaultMaterial, defaultName, defaultLore);
        }
        int slot = section.getInt("slot", defaultSlot);
        Material material = ConfigUtil.readMaterial(section.getString("material"), defaultMaterial);
        String name = section.getString("name", defaultName);
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : defaultLore;
        return new MenuButton(slot, material, name, lore);
    }

    private KeyRequirement readKeyRequirement(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalStateException("Missing key section in configuration");
        }
        Material material = ConfigUtil.readMaterial(section.getString("material"), Material.IRON_NUGGET);
        String displayName = section.getString("display-name", "&4Fragment of Infernal Passage");
        int amount = section.getInt("amount", 25);
        String pouchItemId = section.getString("pouch-item-id");
        if (pouchItemId != null && pouchItemId.isBlank()) {
            pouchItemId = null;
        }
        return new KeyRequirement(material, displayName, amount, pouchItemId);
    }

    private PitySettings readPitySettings(ConfigurationSection section) {
        if (section == null) {
            return new PitySettings(25, Collections.emptyList());
        }
        int threshold = Math.max(1, section.getInt("threshold", 25));
        List<String> pool = section.getStringList("pool");
        return new PitySettings(threshold, pool);
    }

    private ArenaSettings readArenaSettings(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalStateException("Missing arena section in configuration");
        }
        String worldName = section.getString("world", "world");

        SpawnLocation returnLocation = readSpawnLocation(section.getConfigurationSection("return-location"), worldName);
        Vector playerSpawnOffset = ConfigUtil.readVector(section.getConfigurationSection("player-spawn-offset"));
        Vector pasteOffset = ConfigUtil.readVector(section.getConfigurationSection("paste-offset"));
        Vector regionSize = ConfigUtil.readVector(section.getConfigurationSection("region-size"));

        if (regionSize == null) {
            regionSize = new Vector(48, 32, 48);
        }

        ConfigurationSection markersSection = section.getConfigurationSection("markers");
        Material mobMarker = markersSection == null ? Material.OBSIDIAN :
                ConfigUtil.readMaterial(markersSection.getString("mob"), Material.OBSIDIAN);
        Material chestMarker = markersSection == null ? Material.BARREL :
                ConfigUtil.readMaterial(markersSection.getString("chest"), Material.BARREL);

        List<ArenaSlot> slots = new ArrayList<>();
        for (ConfigurationSection slotSection : ConfigUtil.children(section.getConfigurationSection("slots"))) {
            String id = slotSection.getName();
            ConfigurationSection originSection = slotSection.getConfigurationSection("origin");
            if (originSection == null) {
                throw new IllegalStateException("Slot " + id + " is missing origin");
            }
            SpawnLocation origin = readSpawnLocation(originSection, originSection.getString("world", worldName));
            slots.add(new ArenaSlot(id, origin));
        }
        if (slots.isEmpty()) {
            throw new IllegalStateException("No arena slots configured");
        }

        MobSettings mobSettings = readMobSettings(section.getConfigurationSection("mob"));
        ChestSettings chestSettings = readChestSettings(section.getConfigurationSection("chest"));
        SchematicSettings schematicSettings = readSchematicSettings(section.getConfigurationSection("schematic"));

        return new ArenaSettings(worldName, returnLocation, playerSpawnOffset, pasteOffset, regionSize,
                mobMarker, chestMarker, slots, mobSettings, chestSettings, schematicSettings);
    }

    private SpawnLocation readSpawnLocation(ConfigurationSection section, String defaultWorld) {
        if (section == null) {
            return new SpawnLocation(defaultWorld, 0.5, 80.0, 0.5, 0f, 0f);
        }
        String world = section.getString("world", defaultWorld);
        double x = section.getDouble("x", 0.5);
        double y = section.getDouble("y", 80.0);
        double z = section.getDouble("z", 0.5);
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        return new SpawnLocation(world, x, y, z, yaw, pitch);
    }

    private MobSettings readMobSettings(ConfigurationSection section) {
        if (section == null) {
            return new MobSettings(SpawnMode.VANILLA, null, null, "MythicMob", EntityType.HUSK, 1);
        }
        String spawnModeRaw = section.getString("spawn-mode", "VANILLA");
        SpawnMode spawnMode;
        try {
            spawnMode = SpawnMode.valueOf(spawnModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            spawnMode = SpawnMode.VANILLA;
        }

        String mythicId = section.getString("mythic-id");
        String command = section.getString("spawn-command", "mm mobs spawn {id} {world} {x} {y} {z}");
        String metadataKey = section.getString("metadata-key", "MythicMob");
        EntityType fallbackEntity = ConfigUtil.readEntityType(section.getString("fallback-entity"), EntityType.HUSK);
        int spawnYOffset = section.getInt("y-offset", 1);

        return new MobSettings(spawnMode, mythicId, command, metadataKey, fallbackEntity, spawnYOffset);
    }

    private ChestSettings readChestSettings(ConfigurationSection section) {
        Material material = Material.CHEST;
        String name = "&4Blood Chest";
        List<String> lore = List.of("&7Open to claim your rewards");
        if (section != null) {
            material = ConfigUtil.readMaterial(section.getString("material"), material);
            name = section.getString("name", name);
            lore = section.contains("lore") ? section.getStringList("lore") : lore;
        }
        return new ChestSettings(material, name, lore);
    }

    private SchematicSettings readSchematicSettings(ConfigurationSection section) {
        if (section == null) {
            return new SchematicSettings("schematics/blood_chest", "arena.schem");
        }
        String folder = section.getString("folder", "schematics/blood_chest");
        String file = section.getString("file", "arena.schem");
        return new SchematicSettings(folder, file);
    }

    private RewardSettings readRewardSettings(ConfigurationSection section) {
        List<TimeThreshold> thresholds = new ArrayList<>();
        if (section != null) {
            for (ConfigurationSection thresholdSection : ConfigUtil.children(section.getConfigurationSection("thresholds"))) {
                int chestCount = thresholdSection.getInt("chest-count", 1);
                int maxSeconds = thresholdSection.getInt("max-seconds", Integer.MAX_VALUE);
                thresholds.add(new TimeThreshold(chestCount, maxSeconds));
            }
        }
        if (thresholds.isEmpty()) {
            thresholds.add(new TimeThreshold(5, 30));
            thresholds.add(new TimeThreshold(4, 60));
            thresholds.add(new TimeThreshold(3, 120));
            thresholds.add(new TimeThreshold(2, 300));
            thresholds.add(new TimeThreshold(1, Integer.MAX_VALUE));
        }
        thresholds.sort((a, b) -> Integer.compare(a.getMaxSeconds(), b.getMaxSeconds()));
        int rollsPerChest = section == null ? 3 : Math.max(1, section.getInt("rolls-per-chest", 3));
        int exitCountdown = section == null ? 60 : Math.max(1, section.getInt("exit-countdown-seconds", 60));
        return new RewardSettings(thresholds, rollsPerChest, exitCountdown);
    }
}
