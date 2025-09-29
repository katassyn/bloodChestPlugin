package pl.yourserver.bloodChestPlugin.config;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of the plugin configuration.
 */
public class PluginConfiguration {

    private final String permission;
    private final GuiSettings guiSettings;
    private final KeyRequirement keyRequirement;
    private final PitySettings pitySettings;
    private final ArenaSettings arenaSettings;
    private final RewardSettings rewardSettings;

    public PluginConfiguration(
            String permission,
            GuiSettings guiSettings,
            KeyRequirement keyRequirement,
            PitySettings pitySettings,
            ArenaSettings arenaSettings,
            RewardSettings rewardSettings
    ) {
        this.permission = Objects.requireNonNull(permission, "permission");
        this.guiSettings = Objects.requireNonNull(guiSettings, "guiSettings");
        this.keyRequirement = Objects.requireNonNull(keyRequirement, "keyRequirement");
        this.pitySettings = Objects.requireNonNull(pitySettings, "pitySettings");
        this.arenaSettings = Objects.requireNonNull(arenaSettings, "arenaSettings");
        this.rewardSettings = Objects.requireNonNull(rewardSettings, "rewardSettings");
    }

    public String getPermission() {
        return permission;
    }

    public GuiSettings getGuiSettings() {
        return guiSettings;
    }

    public KeyRequirement getKeyRequirement() {
        return keyRequirement;
    }

    public PitySettings getPitySettings() {
        return pitySettings;
    }

    public ArenaSettings getArenaSettings() {
        return arenaSettings;
    }

    public RewardSettings getRewardSettings() {
        return rewardSettings;
    }

    public static final class GuiSettings {
        private final String title;
        private final List<String> instructions;
        private final MenuButton startButton;
        private final MenuButton lootButton;
        private final MenuButton closeButton;
        private final MenuButton backButton;

        public GuiSettings(String title,
                           List<String> instructions,
                           MenuButton startButton,
                           MenuButton lootButton,
                           MenuButton closeButton,
                           MenuButton backButton) {
            this.title = Objects.requireNonNull(title, "title");
            this.instructions = List.copyOf(instructions);
            this.startButton = Objects.requireNonNull(startButton, "startButton");
            this.lootButton = Objects.requireNonNull(lootButton, "lootButton");
            this.closeButton = Objects.requireNonNull(closeButton, "closeButton");
            this.backButton = Objects.requireNonNull(backButton, "backButton");
        }

        public String getTitle() {
            return title;
        }

        public List<String> getInstructions() {
            return instructions;
        }

        public MenuButton getStartButton() {
            return startButton;
        }

        public MenuButton getLootButton() {
            return lootButton;
        }

        public MenuButton getCloseButton() {
            return closeButton;
        }

        public MenuButton getBackButton() {
            return backButton;
        }
    }

    public static final class MenuButton {
        private final int slot;
        private final Material material;
        private final String displayName;
        private final List<String> lore;

        public MenuButton(int slot, Material material, String displayName, List<String> lore) {
            this.slot = slot;
            this.material = Objects.requireNonNull(material, "material");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.lore = Collections.unmodifiableList(lore);
        }

        public int getSlot() {
            return slot;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore;
        }
    }

    public static final class KeyRequirement {
        private final Material material;
        private final String displayName;
        private final int amount;
        private final String pouchItemId;

        public KeyRequirement(Material material, String displayName, int amount, String pouchItemId) {
            this.material = Objects.requireNonNull(material, "material");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.amount = amount;
            this.pouchItemId = pouchItemId;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getAmount() {
            return amount;
        }

        public Optional<String> getPouchItemId() {
            return Optional.ofNullable(pouchItemId);
        }
    }

    public static final class PitySettings {
        private final int threshold;
        private final List<String> pool;

        public PitySettings(int threshold, List<String> pool) {
            this.threshold = threshold;
            this.pool = Collections.unmodifiableList(pool);
        }

        public int getThreshold() {
            return threshold;
        }

        public List<String> getPool() {
            return pool;
        }
    }

    public static final class ArenaSettings {
        private final String worldName;
        private final SpawnLocation returnLocation;
        private final Vector playerSpawnOffset;
        private final Vector pasteOffset;
        private final Vector regionSize;
        private final Material mobMarkerMaterial;
        private final Material chestMarkerMaterial;
        private final Material minorMobMarkerMaterial;
        private final List<ArenaSlot> slots;
        private final MobSettings mobSettings;
        private final ChestSettings chestSettings;
        private final SchematicSettings schematicSettings;

        public ArenaSettings(String worldName,
                             SpawnLocation returnLocation,
                             Vector playerSpawnOffset,
                             Vector pasteOffset,
                             Vector regionSize,
                             Material mobMarkerMaterial,
                             Material chestMarkerMaterial,
                             Material minorMobMarkerMaterial,
                             List<ArenaSlot> slots,
                             MobSettings mobSettings,
                             ChestSettings chestSettings,
                             SchematicSettings schematicSettings) {
            this.worldName = Objects.requireNonNull(worldName, "worldName");
            this.returnLocation = Objects.requireNonNull(returnLocation, "returnLocation");
            this.playerSpawnOffset = playerSpawnOffset == null ? new Vector() : playerSpawnOffset.clone();
            this.pasteOffset = pasteOffset == null ? new Vector() : pasteOffset.clone();
            this.regionSize = Objects.requireNonNull(regionSize, "regionSize").clone();
            this.mobMarkerMaterial = Objects.requireNonNull(mobMarkerMaterial, "mobMarkerMaterial");
            this.chestMarkerMaterial = Objects.requireNonNull(chestMarkerMaterial, "chestMarkerMaterial");
            this.minorMobMarkerMaterial = minorMobMarkerMaterial;
            this.slots = List.copyOf(slots);
            this.mobSettings = Objects.requireNonNull(mobSettings, "mobSettings");
            this.chestSettings = Objects.requireNonNull(chestSettings, "chestSettings");
            this.schematicSettings = Objects.requireNonNull(schematicSettings, "schematicSettings");
        }

        public String getWorldName() {
            return worldName;
        }

        public SpawnLocation getReturnLocation() {
            return returnLocation;
        }

        public Vector getPlayerSpawnOffset() {
            return playerSpawnOffset.clone();
        }

        public Vector getPasteOffset() {
            return pasteOffset.clone();
        }

        public Vector getRegionSize() {
            return regionSize.clone();
        }

        public Material getMobMarkerMaterial() {
            return mobMarkerMaterial;
        }

        public Material getChestMarkerMaterial() {
            return chestMarkerMaterial;
        }

        public Optional<Material> getMinorMobMarkerMaterial() {
            return Optional.ofNullable(minorMobMarkerMaterial);
        }

        public List<ArenaSlot> getSlots() {
            return slots;
        }

        public MobSettings getMobSettings() {
            return mobSettings;
        }

        public ChestSettings getChestSettings() {
            return chestSettings;
        }

        public SchematicSettings getSchematicSettings() {
            return schematicSettings;
        }
    }

    public static final class ArenaSlot {
        private final String id;
        private final SpawnLocation origin;

        public ArenaSlot(String id, SpawnLocation origin) {
            this.id = Objects.requireNonNull(id, "id");
            this.origin = Objects.requireNonNull(origin, "origin");
        }

        public String getId() {
            return id;
        }

        public SpawnLocation getOrigin() {
            return origin;
        }
    }

    public static final class SpawnLocation {
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        public SpawnLocation(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = Objects.requireNonNull(world, "world");
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }
    }

    public static final class MobSettings {
        public enum SpawnMode {
            VANILLA,
            MYTHIC_COMMAND
        }

        private final SpawnMode spawnMode;
        private final String mythicMobId;
        private final String spawnCommand;
        private final String metadataKey;
        private final EntityType fallbackEntityType;
        private final int spawnYOffset;
        private final int primaryMobCount;
        private final List<String> additionalMythicMobIds;
        private final List<MinorMobSpawn> minorMobSpawns;

        public MobSettings(SpawnMode spawnMode,
                           String mythicMobId,
                           String spawnCommand,
                           String metadataKey,
                           EntityType fallbackEntityType,
                           int spawnYOffset,
                           int primaryMobCount,
                           List<String> additionalMythicMobIds,
                           List<MinorMobSpawn> minorMobSpawns) {
            this.spawnMode = Objects.requireNonNull(spawnMode, "spawnMode");
            this.mythicMobId = mythicMobId;
            this.spawnCommand = spawnCommand;
            this.metadataKey = metadataKey;
            this.fallbackEntityType = fallbackEntityType == null ? EntityType.ZOMBIE : fallbackEntityType;
            this.spawnYOffset = spawnYOffset;
            this.primaryMobCount = Math.max(1, primaryMobCount);
            this.additionalMythicMobIds = List.copyOf(additionalMythicMobIds);
            this.minorMobSpawns = List.copyOf(minorMobSpawns);
        }

        public SpawnMode getSpawnMode() {
            return spawnMode;
        }

        public String getMythicMobId() {
            return mythicMobId;
        }

        public String getSpawnCommand() {
            return spawnCommand;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        public EntityType getFallbackEntityType() {
            return fallbackEntityType;
        }

        public int getSpawnYOffset() {
            return spawnYOffset;
        }

        public int getPrimaryMobCount() {
            return primaryMobCount;
        }

        public List<String> getAdditionalMythicMobIds() {
            return additionalMythicMobIds;
        }

        public List<MinorMobSpawn> getMinorMobSpawns() {
            return minorMobSpawns;
        }

        public static final class MinorMobSpawn {
            private final String mythicMobId;
            private final int count;

            public MinorMobSpawn(String mythicMobId, int count) {
                this.mythicMobId = Objects.requireNonNull(mythicMobId, "mythicMobId");
                this.count = Math.max(1, count);
            }

            public String getMythicMobId() {
                return mythicMobId;
            }

            public int getCount() {
                return count;
            }
        }
    }

    public static final class ChestSettings {
        private final Material chestMaterial;
        private final String displayName;
        private final List<String> lore;

        public ChestSettings(Material chestMaterial, String displayName, List<String> lore) {
            this.chestMaterial = Objects.requireNonNull(chestMaterial, "chestMaterial");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.lore = Collections.unmodifiableList(lore);
        }

        public Material getChestMaterial() {
            return chestMaterial;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore;
        }
    }

    public static final class SchematicSettings {
        private final String folder;
        private final String file;

        public SchematicSettings(String folder, String file) {
            this.folder = Objects.requireNonNull(folder, "folder");
            this.file = Objects.requireNonNull(file, "file");
        }

        public String getFolder() {
            return folder;
        }

        public String getFile() {
            return file;
        }
    }

    public static final class RewardSettings {
        private final List<TimeThreshold> thresholds;
        private final int rollsPerChest;
        private final int exitCountdownSeconds;

        public RewardSettings(List<TimeThreshold> thresholds, int rollsPerChest, int exitCountdownSeconds) {
            this.thresholds = List.copyOf(thresholds);
            this.rollsPerChest = rollsPerChest;
            this.exitCountdownSeconds = exitCountdownSeconds;
        }

        public List<TimeThreshold> getThresholds() {
            return thresholds;
        }

        public int getRollsPerChest() {
            return rollsPerChest;
        }

        public int getExitCountdownSeconds() {
            return exitCountdownSeconds;
        }
    }

    public static final class TimeThreshold {
        private final int chestCount;
        private final int maxSeconds;

        public TimeThreshold(int chestCount, int maxSeconds) {
            this.chestCount = chestCount;
            this.maxSeconds = maxSeconds;
        }

        public int getChestCount() {
            return chestCount;
        }

        public int getMaxSeconds() {
            return maxSeconds;
        }
    }
}
