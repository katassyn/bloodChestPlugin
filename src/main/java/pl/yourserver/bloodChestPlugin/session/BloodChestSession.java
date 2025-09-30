package pl.yourserver.bloodChestPlugin.session;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.MetadataValue;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ArenaSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ChestSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MobSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MobSettings.MinorMobSpawn;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.MobSettings.SpawnMode;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.RewardSettings;
import pl.yourserver.bloodChestPlugin.loot.LootResult;
import pl.yourserver.bloodChestPlugin.loot.LootService;
import pl.yourserver.bloodChestPlugin.session.PityManager;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public class BloodChestSession {

    private static final String MOB_TAG = "blood_chest_mob";
    private static final String PRIMARY_MOB_TAG = "blood_chest_primary";
    private static final Pattern LEGACY_LOCATION_PATTERN =
            Pattern.compile("\\{world\\}\\s*,\\s*\\{x\\}\\s*,\\s*\\{y\\}\\s*,\\s*\\{z\\}");

    private final Plugin plugin;
    private final PluginConfiguration configuration;
    private final SchematicHandler schematicHandler;
    private final LootService lootService;
    private final PityManager pityManager;
    private final ArenaSettings arenaSettings;
    private final RewardSettings rewardSettings;
    private final ArenaSlotInstance slot;
    private final Location returnLocation;
    private final File schematicFile;
    private final SessionManager manager;

    private Player player;
    private Location pasteOrigin;
    private final List<Location> mobSpawnLocations = new ArrayList<>();
    private final List<Location> minorMobSpawnLocations = new ArrayList<>();
    private final List<Location> chestLocations = new ArrayList<>();
    private final Map<Location, Boolean> spawnedChests = new HashMap<>();
    private final Map<Location, List<Component>> chestLoreByLocation = new HashMap<>();
    private final Map<UUID, SpawnType> activeMobs = new HashMap<>();
    private final Set<UUID> processedDeaths = new HashSet<>();
    private final List<SpawnAssignment> pendingSpawnAssignments = new ArrayList<>();
    private String primaryMythicMobName;
    private Set<String> additionalMythicMobNames = Set.of();
    private Location playerSpawnLocation;
    private ArenaBounds arenaBounds;
    private int defeatedPrimaryCount;
    private int requiredPrimaryCount;
    private long startTimeMillis;
    private boolean mobsDefeated;
    private boolean finished;
    private BukkitRunnable exitCountdownTask;
    private int exitCountdownSeconds;
    private BossBar progressBar;
    private BukkitRunnable bossBarTask;

    public BloodChestSession(Plugin plugin,
                             PluginConfiguration configuration,
                             SchematicHandler schematicHandler,
                             LootService lootService,
                             PityManager pityManager,
                             ArenaSlotInstance slot,
                             Location returnLocation,
                             File schematicFile,
                             SessionManager manager) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.schematicHandler = schematicHandler;
        this.lootService = lootService;
        this.pityManager = pityManager;
        this.arenaSettings = configuration.getArenaSettings();
        this.rewardSettings = configuration.getRewardSettings();
        this.slot = slot;
        this.returnLocation = returnLocation;
        this.schematicFile = schematicFile;
        this.manager = manager;
    }

    public UUID getPlayerId() {
        return player.getUniqueId();
    }

    public ArenaSlotInstance getSlot() {
        return slot;
    }

    public void start(Player player) throws Exception {
        this.player = player;
        ensureSchematicExists();
        this.pasteOrigin = slot.getOrigin().clone().add(arenaSettings.getPasteOffset());
        World world = pasteOrigin.getWorld();
        if (world == null) {
            throw new IllegalStateException("World is not loaded for arena slot " + slot.getId());
        }

        boolean setupComplete = false;
        this.arenaBounds = null;
        try {
            SchematicHandler.PasteResult pasteResult = schematicHandler.pasteSchematic(
                    schematicFile,
                    world,
                    pasteOrigin,
                    new SchematicHandler.MarkerConfiguration(
                            arenaSettings.getMobMarkerMaterial(),
                            arenaSettings.getChestMarkerMaterial(),
                            arenaSettings.getMinorMobMarkerMaterial()));
            Vector appliedOffset = pasteResult.appliedOffset();
            if (appliedOffset != null) {
                pasteOrigin.add(appliedOffset);
            }
            this.arenaBounds = resolveArenaBounds(pasteOrigin, arenaSettings.getRegionSize(), pasteResult);
            scanMarkers(world, arenaBounds, pasteResult);
            teleportPlayerToArena();
            this.startTimeMillis = System.currentTimeMillis();
            this.defeatedPrimaryCount = 0;
            this.requiredPrimaryCount = 0;
            this.activeMobs.clear();
            this.processedDeaths.clear();
            this.pendingSpawnAssignments.clear();
            MobSettings mobSettings = arenaSettings.getMobSettings();
            this.primaryMythicMobName = normalizeMythicName(mobSettings.getMythicMobId());
            this.additionalMythicMobNames = computeAdditionalMythicMobNames(mobSettings, primaryMythicMobName);
            spawnMobs(world);
            if (requiredPrimaryCount > 0) {
                initializeBossBar();
            }
            player.sendMessage(color("&7Defeat all &cBlood Sludges &7as quickly as possible!"));
            setupComplete = true;
        } finally {
            if (!setupComplete) {
                clearArenaRegion();
                mobSpawnLocations.clear();
                minorMobSpawnLocations.clear();
                chestLocations.clear();
                spawnedChests.clear();
                chestLoreByLocation.clear();
                activeMobs.clear();
                pendingSpawnAssignments.clear();
                primaryMythicMobName = null;
                additionalMythicMobNames = Set.of();
                playerSpawnLocation = null;
                arenaBounds = null;
                clearBossBar();
            }
        }
    }

    private void ensureSchematicExists() throws IOException {
        if (!schematicFile.exists()) {
            throw new IOException("Arena schematic not found: " + schematicFile.getAbsolutePath());
        }
    }

    private void scanMarkers(World world, ArenaBounds bounds, SchematicHandler.PasteResult pasteResult) {
        mobSpawnLocations.clear();
        minorMobSpawnLocations.clear();
        chestLocations.clear();

        Location slotOrigin = slot.getOrigin();
        playerSpawnLocation = slotOrigin.clone().add(arenaSettings.getPlayerSpawnOffset());
        playerSpawnLocation.setYaw(slotOrigin.getYaw());
        playerSpawnLocation.setPitch(slotOrigin.getPitch());

        boolean spawnMarkerFound = applyMarkersFromPasteResult(pasteResult, slotOrigin);

        boolean needMobMarkers = mobSpawnLocations.isEmpty();
        boolean needChestMarkers = chestLocations.isEmpty();
        boolean needMinorMarkers = arenaSettings.getMinorMobMarkerMaterial()
                .map(material -> minorMobSpawnLocations.isEmpty())
                .orElse(false);
        boolean needPlayerMarker = !spawnMarkerFound;

        if (needMobMarkers || needChestMarkers || needMinorMarkers || needPlayerMarker) {
            spawnMarkerFound = scanMarkersInWorld(world, bounds, slotOrigin,
                    needMobMarkers,
                    needChestMarkers,
                    needMinorMarkers,
                    needPlayerMarker,
                    spawnMarkerFound);
        }

        if (!spawnMarkerFound) {
            plugin.getLogger().warning("Schematic is missing a gold block player spawn marker. Using configured offset instead.");
        }
        if (chestLocations.isEmpty()) {
            throw new IllegalStateException("Schematic has no chest spawn markers");
        }
    }

    private boolean applyMarkersFromPasteResult(SchematicHandler.PasteResult pasteResult, Location slotOrigin) {
        if (pasteResult == null) {
            return false;
        }
        MobSettings mobSettings = arenaSettings.getMobSettings();
        for (SchematicHandler.BlockOffset offset : pasteResult.mobMarkerOffsets()) {
            Location blockLocation = toWorldBlockLocation(offset);
            Location spawnLocation = blockLocation.add(0.5, 1 + mobSettings.getSpawnYOffset(), 0.5);
            mobSpawnLocations.add(spawnLocation);
        }
        for (SchematicHandler.BlockOffset offset : pasteResult.chestMarkerOffsets()) {
            chestLocations.add(toWorldBlockLocation(offset));
        }
        for (SchematicHandler.BlockOffset offset : pasteResult.minorMobMarkerOffsets()) {
            Location blockLocation = toWorldBlockLocation(offset);
            Location spawnLocation = blockLocation.add(0.5, 1 + mobSettings.getSpawnYOffset(), 0.5);
            minorMobSpawnLocations.add(spawnLocation);
        }

        boolean spawnMarkerFound = false;
        if (!pasteResult.playerSpawnMarkerOffsets().isEmpty()) {
            for (SchematicHandler.BlockOffset offset : pasteResult.playerSpawnMarkerOffsets()) {
                Location spawnLocation = toWorldBlockLocation(offset).add(0.5, 1.0, 0.5);
                spawnLocation.setYaw(slotOrigin.getYaw());
                spawnLocation.setPitch(slotOrigin.getPitch());
                if (spawnMarkerFound) {
                    plugin.getLogger().warning("Multiple player spawn markers found for blood chest session");
                    continue;
                }
                playerSpawnLocation = spawnLocation;
                spawnMarkerFound = true;
            }
        }
        return spawnMarkerFound;
    }

    private boolean scanMarkersInWorld(World world,
                                       ArenaBounds bounds,
                                       Location slotOrigin,
                                       boolean scanMobMarkers,
                                       boolean scanChestMarkers,
                                       boolean scanMinorMarkers,
                                       boolean scanPlayerMarker,
                                       boolean spawnMarkerFound) {
        MobSettings mobSettings = arenaSettings.getMobSettings();
        Material minorMobMarker = arenaSettings.getMinorMobMarkerMaterial().orElse(null);
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (scanMobMarkers && block.getType() == arenaSettings.getMobMarkerMaterial()) {
                        Location spawnLocation = block.getLocation().add(0.5, 1 + mobSettings.getSpawnYOffset(), 0.5);
                        mobSpawnLocations.add(spawnLocation);
                    } else if (scanChestMarkers && block.getType() == arenaSettings.getChestMarkerMaterial()) {
                        chestLocations.add(block.getLocation());
                    } else if (scanMinorMarkers && minorMobMarker != null && block.getType() == minorMobMarker) {
                        Location spawnLocation = block.getLocation().add(0.5, 1 + mobSettings.getSpawnYOffset(), 0.5);
                        minorMobSpawnLocations.add(spawnLocation);
                    } else if (scanPlayerMarker && block.getType() == Material.GOLD_BLOCK) {
                        Location spawnLocation = block.getLocation().add(0.5, 1.0, 0.5);
                        spawnLocation.setYaw(slotOrigin.getYaw());
                        spawnLocation.setPitch(slotOrigin.getPitch());
                        if (spawnMarkerFound) {
                            plugin.getLogger().warning("Multiple player spawn markers found for blood chest session");
                        }
                        playerSpawnLocation = spawnLocation;
                        spawnMarkerFound = true;
                        scanPlayerMarker = false;
                    }
                }
            }
        }
        return spawnMarkerFound;
    }

    private Location toWorldBlockLocation(SchematicHandler.BlockOffset offset) {
        if (pasteOrigin == null) {
            throw new IllegalStateException("Paste origin was not initialized");
        }
        World world = pasteOrigin.getWorld();
        if (world == null) {
            throw new IllegalStateException("World is not available for paste origin");
        }
        int x = pasteOrigin.getBlockX() + offset.x();
        int y = pasteOrigin.getBlockY() + offset.y();
        int z = pasteOrigin.getBlockZ() + offset.z();
        return new Location(world, x, y, z);
    }

    private ArenaBounds resolveArenaBounds(Location origin,
                                           Vector configSize,
                                           SchematicHandler.PasteResult pasteResult) {
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        int configWidth = configSize != null ? Math.max(1, (int) Math.ceil(configSize.getX())) : 1;
        int configHeight = configSize != null ? Math.max(1, (int) Math.ceil(configSize.getY())) : 1;
        int configDepth = configSize != null ? Math.max(1, (int) Math.ceil(configSize.getZ())) : 1;
        int configMinX = originX;
        int configMinY = originY;
        int configMinZ = originZ;
        int configMaxX = originX + configWidth - 1;
        int configMaxY = originY + configHeight - 1;
        int configMaxZ = originZ + configDepth - 1;
        int minX = configMinX;
        int minY = configMinY;
        int minZ = configMinZ;
        int maxX = configMaxX;
        int maxY = configMaxY;
        int maxZ = configMaxZ;
        if (pasteResult != null) {
            Vector minOffset = pasteResult.minimumOffset();
            if (minOffset != null) {
                minX = Math.min(minX, originX + (int) Math.floor(minOffset.getX()));
                minY = Math.min(minY, originY + (int) Math.floor(minOffset.getY()));
                minZ = Math.min(minZ, originZ + (int) Math.floor(minOffset.getZ()));
            }
            Vector maxOffset = pasteResult.maximumOffset();
            if (maxOffset != null) {
                maxX = Math.max(maxX, originX + (int) Math.floor(maxOffset.getX()));
                maxY = Math.max(maxY, originY + (int) Math.floor(maxOffset.getY()));
                maxZ = Math.max(maxZ, originZ + (int) Math.floor(maxOffset.getZ()));
            }
        }
        int width = Math.max(1, (maxX - minX) + 1);
        int height = Math.max(1, (maxY - minY) + 1);
        int depth = Math.max(1, (maxZ - minZ) + 1);
        boolean changed = minX != configMinX || minY != configMinY || minZ != configMinZ
                || maxX != configMaxX || maxY != configMaxY || maxZ != configMaxZ;
        if (changed) {
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "[BloodChest] Resolved arena bounds to (%d,%d,%d)->(%d,%d,%d) (size %dx%dx%d, config %dx%dx%d)",
                    minX, minY, minZ, maxX, maxY, maxZ,
                    width, height, depth,
                    configWidth, configHeight, configDepth));
        }
        return new ArenaBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void teleportPlayerToArena() {
        if (playerSpawnLocation == null) {
            throw new IllegalStateException("Player spawn location was not initialized");
        }
        Location destination = playerSpawnLocation.clone();
        player.teleport(destination);
    }

    private void spawnMobs(World world) {
        MobSettings mobSettings = arenaSettings.getMobSettings();
        requiredPrimaryCount = Math.min(mobSettings.getPrimaryMobCount(), mobSpawnLocations.size());
        if (requiredPrimaryCount <= 0) {
            plugin.getLogger().warning("No mob spawn markers found for blood chest session");
            return;
        }
        int index = 0;
        for (; index < requiredPrimaryCount; index++) {
            Location spawnLocation = mobSpawnLocations.get(index);
            spawnMob(world, mobSettings, spawnLocation, mobSettings.getMythicMobId(), SpawnType.PRIMARY);
        }

        List<String> additionalIds = mobSettings.getAdditionalMythicMobIds();
        if (additionalIds.isEmpty()) {
            return;
        }
        int additionalIndex = 0;
        for (; index < mobSpawnLocations.size(); index++) {
            Location spawnLocation = mobSpawnLocations.get(index);
            String mythicId = additionalIds.get(additionalIndex % additionalIds.size());
            additionalIndex++;
            spawnMob(world, mobSettings, spawnLocation, mythicId, SpawnType.ADDITIONAL);
        }

        List<MinorMobSpawn> minorMobSpawns = mobSettings.getMinorMobSpawns();
        if (!minorMobSpawns.isEmpty() && !minorMobSpawnLocations.isEmpty()) {
            for (Location spawnLocation : minorMobSpawnLocations) {
                spawnMinorMobGroup(world, mobSettings, spawnLocation, minorMobSpawns);
            }
        }
    }

    private void spawnMinorMobGroup(World world,
                                    MobSettings mobSettings,
                                    Location markerLocation,
                                    List<MinorMobSpawn> groupDefinitions) {
        int totalCount = groupDefinitions.stream()
                .mapToInt(MinorMobSpawn::getCount)
                .sum();
        if (totalCount <= 0) {
            return;
        }
        int index = 0;
        for (MinorMobSpawn spawn : groupDefinitions) {
            for (int i = 0; i < spawn.getCount(); i++) {
                Location spreadLocation = computeSpreadLocation(markerLocation, index, totalCount);
                spawnMob(world, mobSettings, spreadLocation, spawn.getMythicMobId(), SpawnType.ADDITIONAL);
                index++;
            }
        }
    }

    private Location computeSpreadLocation(Location center, int index, int total) {
        if (total <= 1) {
            return center.clone();
        }
        int ringCapacity = 8;
        int ring = index / ringCapacity;
        int remaining = Math.max(1, total - (ring * ringCapacity));
        int slotsInRing = Math.min(ringCapacity, remaining);
        int positionInRing = index - (ring * ringCapacity);
        if (positionInRing >= slotsInRing) {
            positionInRing %= slotsInRing;
        }
        double angle = (Math.PI * 2.0 / slotsInRing) * positionInRing;
        double radius = 1.25 + (ring * 1.5);
        double x = center.getX() + Math.cos(angle) * radius;
        double z = center.getZ() + Math.sin(angle) * radius;
        return new Location(center.getWorld(), x, center.getY(), z);
    }

    private void spawnMob(World world,
                          MobSettings mobSettings,
                          Location spawnLocation,
                          String mythicId,
                          SpawnType type) {
        Location adjustedLocation = applySpawnOffset(spawnLocation, mobSettings);
        if (mobSettings.getSpawnMode() == SpawnMode.MYTHIC_COMMAND) {
            String normalizedId = normalizeMythicName(mythicId);
            pendingSpawnAssignments.add(new SpawnAssignment(adjustedLocation.clone(), type, normalizedId));
            String command = buildSpawnCommand(mythicId, mobSettings, adjustedLocation);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            EntityType fallback = mobSettings.getFallbackEntityType();
            Entity entity = world.spawnEntity(adjustedLocation, fallback);
            if (entity instanceof LivingEntity livingEntity) {
                trackSpawnedEntity(livingEntity, type);
            }
        }
    }

    private void trackSpawnedEntity(LivingEntity entity, SpawnType type) {
        entity.addScoreboardTag(MOB_TAG);
        if (type == SpawnType.PRIMARY) {
            entity.addScoreboardTag(PRIMARY_MOB_TAG);
        }
        activeMobs.put(entity.getUniqueId(), type);
    }

    private boolean hasRequiredMetadata(LivingEntity entity, MobSettings mobSettings) {
        String metadataKey = mobSettings.getMetadataKey();
        return metadataKey == null || metadataKey.isEmpty() || entity.hasMetadata(metadataKey);
    }

    private String resolveMythicMobName(LivingEntity entity,
                                        MobSettings mobSettings,
                                        String providedName) {
        String normalized = normalizeMythicName(providedName);
        if (normalized != null) {
            return normalized;
        }
        try {
            Optional<ActiveMob> activeMobOptional = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            if (activeMobOptional.isPresent()) {
                ActiveMob activeMob = activeMobOptional.get();
                normalized = normalizeMythicName(activeMob.getType().getInternalName());
                if (normalized != null) {
                    return normalized;
                }
            }
        } catch (Exception ignored) {
            // MythicMobs may not be available in edge cases; ignore and fall back to metadata
        }
        String metadataKey = mobSettings.getMetadataKey();
        if (metadataKey == null || metadataKey.isEmpty() || !entity.hasMetadata(metadataKey)) {
            return null;
        }
        List<MetadataValue> metadataValues = entity.getMetadata(metadataKey);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue == null) {
                continue;
            }
            normalized = normalizeMythicName(metadataValue.asString());
            if (normalized != null) {
                return normalized;
            }
            Object rawValue = metadataValue.value();
            if (rawValue instanceof String rawString) {
                normalized = normalizeMythicName(rawString);
                if (normalized != null) {
                    return normalized;
                }
            } else if (rawValue instanceof ActiveMob activeMob) {
                normalized = normalizeMythicName(activeMob.getType().getInternalName());
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private SpawnType resolveSpawnType(LivingEntity entity, String normalizedMobName) {
        if (entity.getScoreboardTags().contains(PRIMARY_MOB_TAG) || isPrimaryMythicMob(normalizedMobName)) {
            return SpawnType.PRIMARY;
        }
        if (entity.getScoreboardTags().contains(MOB_TAG) || isAdditionalMythicMob(normalizedMobName)) {
            return SpawnType.ADDITIONAL;
        }
        return null;
    }

    private boolean isPrimaryMythicMob(String normalizedMobName) {
        return normalizedMobName != null && normalizedMobName.equals(primaryMythicMobName);
    }

    private boolean isAdditionalMythicMob(String normalizedMobName) {
        return normalizedMobName != null && additionalMythicMobNames.contains(normalizedMobName);
    }

    private Location applySpawnOffset(Location location, MobSettings mobSettings) {
        Location clone = location.clone();
        clone.add(0.0, mobSettings.getSpawnYOffset(), 0.0);
        return clone;
    }

    private String buildSpawnCommand(String mobId, MobSettings mobSettings, Location location) {
        String command = mobSettings.getSpawnCommand();
        if (command == null || command.isBlank()) {
            command = "mm m spawn {id} {amount} {location}";
        }
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Spawn location world is not available");
        }
        String id = mobId != null ? mobId : "";
        String worldName = world.getName();
        String x = formatCoordinate(location.getX());
        String y = formatCoordinate(location.getY());
        String z = formatCoordinate(location.getZ());
        String yaw = formatCoordinate(location.getYaw());
        String pitch = formatCoordinate(location.getPitch());
        String amount = "1";
        String locationToken = String.join(",", worldName, x, y, z);
        String locationSpaceToken = String.join(" ", worldName, x, y, z);
        String locationWithAnglesToken = String.join(" ", worldName, x, y, z, yaw, pitch);
        command = LEGACY_LOCATION_PATTERN.matcher(command).replaceAll("{location}");
        return command
                .replace("{location_with_yaw_pitch}", locationWithAnglesToken)
                .replace("{location_space}", locationSpaceToken)
                .replace("{location}", locationToken)
                .replace("{amount}", amount)
                .replace("{id}", id)
                .replace("{world}", worldName)
                .replace("{x}", x)
                .replace("{y}", y)
                .replace("{z}", z)
                .replace("{yaw}", yaw)
                .replace("{pitch}", pitch);
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public void handleEntitySpawn(LivingEntity entity, String mythicMobName) {
        if (finished) {
            return;
        }
        MobSettings mobSettings = arenaSettings.getMobSettings();
        if (mobSettings.getSpawnMode() != SpawnMode.MYTHIC_COMMAND) {
            return;
        }
        if (pendingSpawnAssignments.isEmpty()) {
            return;
        }
        if (!hasRequiredMetadata(entity, mobSettings)) {
            return;
        }
        String normalizedName = resolveMythicMobName(entity, mobSettings, mythicMobName);
        Location actualLocation = entity.getLocation();
        Iterator<SpawnAssignment> iterator = pendingSpawnAssignments.iterator();
        while (iterator.hasNext()) {
            SpawnAssignment assignment = iterator.next();
            if (assignment.matches(actualLocation, normalizedName)) {
                iterator.remove();
                trackSpawnedEntity(entity, assignment.type());
                break;
            }
        }
    }

    public void handleEntityDeath(LivingEntity entity, String mythicMobName) {
        if (finished) {
            return;
        }
        if (pasteOrigin == null || !entity.getWorld().equals(pasteOrigin.getWorld())) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        SpawnType type = activeMobs.remove(uuid);
        if (type != null) {
            processedDeaths.add(uuid);
            if (type == SpawnType.PRIMARY) {
                defeatedPrimaryCount++;
                updateBossBar();
            }
            checkMobsDefeated();
            return;
        }
        MobSettings mobSettings = arenaSettings.getMobSettings();
        if (mobSettings.getSpawnMode() != SpawnMode.MYTHIC_COMMAND) {
            return;
        }
        if (!hasRequiredMetadata(entity, mobSettings)) {
            return;
        }
        if (processedDeaths.contains(uuid)) {
            return;
        }
        if (!isWithinArena(entity.getLocation())) {
            return;
        }
        processedDeaths.add(uuid);
        String normalizedName = resolveMythicMobName(entity, mobSettings, mythicMobName);
        SpawnType resolvedType = resolveSpawnType(entity, normalizedName);
        if (resolvedType == SpawnType.PRIMARY) {
            defeatedPrimaryCount++;
            updateBossBar();
        }
        if (resolvedType != null) {
            checkMobsDefeated();
        }
    }

    private boolean isWithinArena(Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(pasteOrigin.getWorld())) {
            return false;
        }
        if (arenaBounds == null) {
            return false;
        }
        return arenaBounds.contains(location);
    }

    private void checkMobsDefeated() {
        if (mobsDefeated) {
            return;
        }
        if (requiredPrimaryCount <= 0) {
            return;
        }
        boolean anyPrimaryActive = activeMobs.values().stream().anyMatch(type -> type == SpawnType.PRIMARY);
        if (defeatedPrimaryCount >= requiredPrimaryCount && !anyPrimaryActive) {
            mobsDefeated = true;
            onMobsDefeated();
        }
    }

    private void onMobsDefeated() {
        long elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000L;
        int chestCount = determineChestCount((int) elapsedSeconds);
        removeAdditionalMobs();
        player.sendMessage(color("&7All sludges were defeated in &e" + elapsedSeconds + "s&7!"));
        showVictoryOnBossBar(elapsedSeconds, chestCount);
        spawnChests(chestCount);
    }

    private int determineChestCount(int elapsedSeconds) {
        return rewardSettings.getThresholds().stream()
                .filter(threshold -> elapsedSeconds <= threshold.getMaxSeconds())
                .findFirst()
                .map(PluginConfiguration.TimeThreshold::getChestCount)
                .orElse(1);
    }

    private void spawnChests(int chestCount) {
        ChestSettings chestSettings = arenaSettings.getChestSettings();
        int available = Math.min(chestCount, chestLocations.size());
        spawnedChests.clear();
        for (int i = 0; i < available; i++) {
            Location location = chestLocations.get(i);
            Block block = location.getBlock();
            block.setType(chestSettings.getChestMaterial(), false);
            if (block.getState() instanceof TileState tileState) {
                if (tileState instanceof Nameable nameable) {
                    nameable.customName(Component.text(ChatColor.translateAlternateColorCodes('&', chestSettings.getDisplayName())));
                }
                List<Component> lore = chestSettings.getLore().stream()
                        .map(line -> (Component) Component.text(ChatColor.translateAlternateColorCodes('&', line)))
                        .collect(Collectors.toList());
                if (!lore.isEmpty()) {
                    chestLoreByLocation.put(location, lore);
                }
                tileState.update();
            }
            spawnedChests.put(location, Boolean.FALSE);
        }
        player.sendMessage(color("&c" + available + " Blood Chests have appeared!"));
    }

    public boolean handleChestInteraction(Player clicker, Block block) {
        if (finished || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        Location location = block.getLocation();
        Boolean opened = spawnedChests.get(location);
        if (opened == null || opened) {
            return false;
        }
        spawnedChests.put(location, Boolean.TRUE);
        List<Component> lore = chestLoreByLocation.remove(location);
        if (lore != null && !lore.isEmpty()) {
            lore.forEach(player::sendMessage);
        }
        block.setType(Material.AIR, false);
        LootResult result = lootService.generateLoot(player.getUniqueId(), rewardSettings.getRollsPerChest(), pityManager);
        dropItems(location, result);
        if (result.isPityGranted()) {
            player.sendMessage(color("&6A pity drop has been granted!"));
        }
        if (spawnedChests.values().stream().allMatch(Boolean::booleanValue)) {
            startExitCountdown();
        }
        return true;
    }

    private void removeAdditionalMobs() {
        Iterator<Map.Entry<UUID, SpawnType>> iterator = activeMobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnType> entry = iterator.next();
            if (entry.getValue() == SpawnType.ADDITIONAL) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity != null) {
                    entity.remove();
                }
                iterator.remove();
            }
        }
    }

    private void removeTrackedMobs() {
        Iterator<UUID> iterator = activeMobs.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
            iterator.remove();
        }
    }

    private Set<String> computeAdditionalMythicMobNames(MobSettings mobSettings, String primaryName) {
        Set<String> names = new HashSet<>();
        for (String id : mobSettings.getAdditionalMythicMobIds()) {
            String normalized = normalizeMythicName(id);
            if (normalized != null) {
                names.add(normalized);
            }
        }
        for (MinorMobSpawn spawn : mobSettings.getMinorMobSpawns()) {
            String normalized = normalizeMythicName(spawn.getMythicMobId());
            if (normalized != null) {
                names.add(normalized);
            }
        }
        if (primaryName != null) {
            names.remove(primaryName);
        }
        return names.isEmpty() ? Set.of() : Set.copyOf(names);
    }

    private String normalizeMythicName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void dropItems(Location location, LootResult result) {
        Location dropLocation = location.clone().add(0.5, 0.8, 0.5);
        List<org.bukkit.inventory.ItemStack> items = result.getItems();
        if (items.isEmpty()) {
            return;
        }
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= items.size()) {
                    cancel();
                    return;
                }
                org.bukkit.inventory.ItemStack item = items.get(index++);
                org.bukkit.entity.Item dropped = dropLocation.getWorld().dropItem(dropLocation, item);
                Vector velocity = new Vector(ThreadLocalRandom.current().nextGaussian() * 0.05,
                        0.35, ThreadLocalRandom.current().nextGaussian() * 0.05);
                dropped.setVelocity(velocity);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void startExitCountdown() {
        if (exitCountdownTask != null) {
            return;
        }
        exitCountdownSeconds = rewardSettings.getExitCountdownSeconds();
        exitCountdownTask = new BukkitRunnable() {
            int remaining = exitCountdownSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    cancel();
                    exitCountdownTask = null;
                    finishSession();
                    return;
                }
                Title title = Title.title(Component.text(ChatColor.RED + "Returning in"),
                        Component.text(ChatColor.GOLD + String.valueOf(remaining) + ChatColor.GRAY + " s"),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
                player.showTitle(title);
                remaining--;
            }
        };
        exitCountdownTask.runTaskTimer(plugin, 0L, 20L);
        player.sendMessage(color("&7You will be teleported in &e" + exitCountdownSeconds + "s"));
    }

    private void finishSession() {
        if (finished) {
            return;
        }
        if (player.isOnline() && !player.isDead()) {
            player.sendMessage(color("&7The Blood Chest challenge has ended."));
        }
        stopSession(true, false);
    }

    public void handlePlayerQuit() {
        forceStop();
    }

    public void forceStop() {
        stopSession(true, false);
    }

    public void handlePlayerDeath() {
        if (finished) {
            return;
        }
        player.sendMessage(color("&cYou died! The arena has been closed."));
        stopSession(false, true);
    }

    private static final class ArenaBounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private ArenaBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        int minX() {
            return minX;
        }

        int minY() {
            return minY;
        }

        int minZ() {
            return minZ;
        }

        int maxX() {
            return maxX;
        }

        int maxY() {
            return maxY;
        }

        int maxZ() {
            return maxZ;
        }

        Vector sizeVector() {
            return new Vector(width(), height(), depth());
        }

        Location minLocation(World world) {
            return new Location(world, minX, minY, minZ);
        }

        int width() {
            return (maxX - minX) + 1;
        }

        int height() {
            return (maxY - minY) + 1;
        }

        int depth() {
            return (maxZ - minZ) + 1;
        }

        boolean contains(Location location) {
            if (location == null) {
                return false;
            }
            return contains(location.getX(), location.getY(), location.getZ());
        }

        boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1
                    && y >= minY && y < maxY + 1
                    && z >= minZ && z < maxZ + 1;
        }
    }

    private enum SpawnType {
        PRIMARY,
        ADDITIONAL
    }

    private record SpawnAssignment(Location location, SpawnType type, String mythicMobName) {
        boolean matches(Location actualLocation, String actualMobName) {
            if (location.getWorld() == null || actualLocation.getWorld() == null) {
                return false;
            }
            if (!location.getWorld().equals(actualLocation.getWorld())) {
                return false;
            }
            if (location.distanceSquared(actualLocation) > 4.0) {
                return false;
            }
            if (mythicMobName != null && actualMobName != null && !mythicMobName.equals(actualMobName)) {
                return false;
            }
            return true;
        }
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void cancelExitCountdown() {
        if (exitCountdownTask != null) {
            exitCountdownTask.cancel();
            exitCountdownTask = null;
        }
    }

    private void initializeBossBar() {
        clearBossBar();
        progressBar = Bukkit.createBossBar(color("&4Blood Chests"), BarColor.RED, BarStyle.SOLID);
        progressBar.setProgress(0.0);
        progressBar.addPlayer(player);
        bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateBossBar();
            }
        };
        bossBarTask.runTaskTimer(plugin, 0L, 20L);
        updateBossBar();
    }

    private void updateBossBar() {
        if (progressBar == null || player == null || startTimeMillis <= 0L) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (!progressBar.getPlayers().contains(player)) {
            progressBar.addPlayer(player);
        }
        double elapsedSeconds = Math.max(0.0, (System.currentTimeMillis() - startTimeMillis) / 1000.0);
        TimeStage stage = resolveCurrentStage(elapsedSeconds);
        double stageDuration = stage.maxSeconds() == Integer.MAX_VALUE
                ? 30.0
                : Math.max(1.0, stage.durationSeconds());
        double stageElapsed = Math.max(0.0, elapsedSeconds - stage.minSeconds());
        double progress = Math.min(1.0, stageElapsed / stageDuration);
        progressBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        String elapsedFormatted = formatSeconds(elapsedSeconds);
        String stageLimit = stage.maxSeconds() == Integer.MAX_VALUE
                ? "∞"
                : stage.maxSeconds() + "s";
        String title = String.format(Locale.ROOT,
                "&4Blood Chests &7| &c%d&7/&c%d &7Blood Sludges | &e%ss &7→ &6%s",
                defeatedPrimaryCount,
                Math.max(requiredPrimaryCount, defeatedPrimaryCount),
                elapsedFormatted,
                stageLimit);
        progressBar.setTitle(color(title));
    }

    private void showVictoryOnBossBar(long elapsedSeconds, int chestCount) {
        if (progressBar == null) {
            return;
        }
        stopBossBarTask();
        progressBar.setColor(BarColor.GREEN);
        progressBar.setProgress(1.0);
        String title = String.format(Locale.ROOT,
                "&2Blood Chests &7| &c%d&7/&c%d &7Blood Sludges | &e%ds &7→ &6%d skrzynek",
                defeatedPrimaryCount,
                Math.max(requiredPrimaryCount, defeatedPrimaryCount),
                Math.max(0L, elapsedSeconds),
                chestCount);
        progressBar.setTitle(color(title));
    }

    private void stopBossBarTask() {
        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }
    }

    private void clearBossBar() {
        stopBossBarTask();
        if (progressBar != null) {
            progressBar.removeAll();
            progressBar = null;
        }
    }

    private TimeStage resolveCurrentStage(double elapsedSeconds) {
        List<PluginConfiguration.TimeThreshold> thresholds = rewardSettings.getThresholds();
        if (thresholds.isEmpty()) {
            return new TimeStage(0.0, 30.0, 1);
        }
        double previousMax = 0.0;
        PluginConfiguration.TimeThreshold lastThreshold = thresholds.get(thresholds.size() - 1);
        for (PluginConfiguration.TimeThreshold threshold : thresholds) {
            double maxSeconds = threshold.getMaxSeconds();
            if (elapsedSeconds <= maxSeconds) {
                return new TimeStage(previousMax, maxSeconds, threshold.getChestCount());
            }
            previousMax = maxSeconds;
        }
        return new TimeStage(previousMax, lastThreshold.getMaxSeconds(), lastThreshold.getChestCount());
    }

    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private record TimeStage(double minSeconds, double maxSeconds, int chestCount) {
        double durationSeconds() {
            return Math.max(1.0, maxSeconds - minSeconds);
        }
    }

    private void clearArenaRegion() {
        if (pasteOrigin == null || arenaBounds == null) {
            return;
        }
        World world = pasteOrigin.getWorld();
        if (world == null) {
            return;
        }
        try {
            schematicHandler.clearRegion(world, arenaBounds.minLocation(world), arenaBounds.sizeVector());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "[BloodChest] Failed to clear arena region: " + ex.getMessage(), ex);
        }
    }

    private void stopSession(boolean teleportNow, boolean teleportOnRespawn) {
        if (finished) {
            return;
        }
        finished = true;
        cancelExitCountdown();
        clearBossBar();
        removeTrackedMobs();
        boolean canTeleportNow = teleportNow && player.isOnline() && !player.isDead();
        if (!teleportOnRespawn && canTeleportNow) {
            player.teleport(returnLocation);
        }
        clearArenaRegion();
        arenaBounds = null;
        UUID playerId = player.getUniqueId();
        if (teleportOnRespawn) {
            manager.markPendingReturn(playerId);
        } else {
            manager.clearPendingReturn(playerId);
        }
        manager.endSession(this);
    }
}
