package pl.yourserver.bloodChestPlugin.session;

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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BloodChestSession {

    private static final String MOB_TAG = "blood_chest_mob";
    private static final String PRIMARY_MOB_TAG = "blood_chest_primary";

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
    private Location playerSpawnLocation;
    private int defeatedPrimaryCount;
    private int requiredPrimaryCount;
    private long startTimeMillis;
    private boolean mobsDefeated;
    private boolean finished;
    private BukkitRunnable exitCountdownTask;
    private int exitCountdownSeconds;

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

        schematicHandler.pasteSchematic(schematicFile, world, pasteOrigin);
        scanMarkers(world);
        teleportPlayerToArena();
        this.startTimeMillis = System.currentTimeMillis();
        this.defeatedPrimaryCount = 0;
        this.requiredPrimaryCount = 0;
        this.activeMobs.clear();
        this.processedDeaths.clear();
        this.pendingSpawnAssignments.clear();
        spawnMobs(world);
        player.sendMessage(color("&7Defeat all &cBlood Sludges &7as quickly as possible!"));
    }

    private void ensureSchematicExists() throws IOException {
        if (!schematicFile.exists()) {
            throw new IOException("Arena schematic not found: " + schematicFile.getAbsolutePath());
        }
    }

    private void scanMarkers(World world) {
        mobSpawnLocations.clear();
        minorMobSpawnLocations.clear();
        chestLocations.clear();
        playerSpawnLocation = null;
        Vector size = arenaSettings.getRegionSize();
        int baseX = pasteOrigin.getBlockX();
        int baseY = pasteOrigin.getBlockY();
        int baseZ = pasteOrigin.getBlockZ();
        Material minorMobMarker = arenaSettings.getMinorMobMarkerMaterial().orElse(null);
        Location slotOrigin = slot.getOrigin();

        for (int x = 0; x < (int) size.getX(); x++) {
            for (int y = 0; y < (int) size.getY(); y++) {
                for (int z = 0; z < (int) size.getZ(); z++) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    if (block.getType() == arenaSettings.getMobMarkerMaterial()) {
                        Location spawnLocation = block.getLocation().add(0.5, 1 + arenaSettings.getMobSettings().getSpawnYOffset(), 0.5);
                        mobSpawnLocations.add(spawnLocation);
                        block.setType(Material.AIR, false);
                    } else if (block.getType() == arenaSettings.getChestMarkerMaterial()) {
                        chestLocations.add(block.getLocation());
                        block.setType(Material.AIR, false);
                    } else if (minorMobMarker != null && block.getType() == minorMobMarker) {
                        Location spawnLocation = block.getLocation().add(0.5,
                                1 + arenaSettings.getMobSettings().getSpawnYOffset(), 0.5);
                        minorMobSpawnLocations.add(spawnLocation);
                        block.setType(Material.AIR, false);
                    } else if (block.getType() == Material.GOLD_BLOCK) {
                        Location spawnLocation = block.getLocation().add(0.5, 1.0, 0.5);
                        spawnLocation.setYaw(slotOrigin.getYaw());
                        spawnLocation.setPitch(slotOrigin.getPitch());
                        if (playerSpawnLocation == null) {
                            playerSpawnLocation = spawnLocation;
                        } else {
                            plugin.getLogger().warning("Multiple player spawn markers found for blood chest session");
                        }
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        if (playerSpawnLocation == null) {
            throw new IllegalStateException("Schematic is missing a gold block player spawn marker");
        }
        if (chestLocations.isEmpty()) {
            throw new IllegalStateException("Schematic has no chest spawn markers");
        }
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
        if (mobSettings.getSpawnMode() == SpawnMode.MYTHIC_COMMAND) {
            pendingSpawnAssignments.add(new SpawnAssignment(spawnLocation.clone(), type));
            String command = buildSpawnCommand(mythicId, mobSettings, spawnLocation);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            EntityType fallback = mobSettings.getFallbackEntityType();
            Entity entity = world.spawnEntity(spawnLocation, fallback);
            entity.addScoreboardTag(MOB_TAG);
            if (type == SpawnType.PRIMARY) {
                entity.addScoreboardTag(PRIMARY_MOB_TAG);
            }
            activeMobs.put(entity.getUniqueId(), type);
        }
    }

    private String buildSpawnCommand(String mobId, MobSettings mobSettings, Location location) {
        String command = mobSettings.getSpawnCommand();
        if (command == null || command.isEmpty()) {
            command = "mm mobs spawn {id} {world} {x} {y} {z}";
        }
        String id = mobId != null ? mobId : "";
        return command
                .replace("{id}", id)
                .replace("{world}", location.getWorld().getName())
                .replace("{x}", String.format(java.util.Locale.ROOT, "%.2f", location.getX()))
                .replace("{y}", String.format(java.util.Locale.ROOT, "%.2f", location.getY()))
                .replace("{z}", String.format(java.util.Locale.ROOT, "%.2f", location.getZ()));
    }

    public void handleEntitySpawn(Entity entity) {
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
        if (mobSettings.getMetadataKey() != null && !mobSettings.getMetadataKey().isEmpty()) {
            if (!entity.hasMetadata(mobSettings.getMetadataKey())) {
                return;
            }
        }
        Iterator<SpawnAssignment> iterator = pendingSpawnAssignments.iterator();
        while (iterator.hasNext()) {
            SpawnAssignment assignment = iterator.next();
            Location expected = assignment.location();
            if (expected.getWorld() == entity.getWorld() && expected.distanceSquared(entity.getLocation()) <= 4.0) {
                iterator.remove();
                activeMobs.put(entity.getUniqueId(), assignment.type());
                entity.addScoreboardTag(MOB_TAG);
                if (assignment.type() == SpawnType.PRIMARY) {
                    entity.addScoreboardTag(PRIMARY_MOB_TAG);
                }
                break;
            }
        }
    }

    public void handleEntityDeath(Entity entity) {
        if (finished) {
            return;
        }
        if (!entity.getWorld().equals(pasteOrigin.getWorld())) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        SpawnType type = activeMobs.remove(uuid);
        if (type != null) {
            processedDeaths.add(uuid);
            if (type == SpawnType.PRIMARY) {
                defeatedPrimaryCount++;
            }
            checkMobsDefeated();
            return;
        }
        MobSettings mobSettings = arenaSettings.getMobSettings();
        if (mobSettings.getSpawnMode() == SpawnMode.MYTHIC_COMMAND) {
            if (mobSettings.getMetadataKey() != null && !mobSettings.getMetadataKey().isEmpty()) {
                if (!entity.hasMetadata(mobSettings.getMetadataKey())) {
                    return;
                }
            }
            if (processedDeaths.contains(uuid)) {
                return;
            }
            if (isWithinArena(entity.getLocation())) {
                processedDeaths.add(uuid);
                if (entity.getScoreboardTags().contains(PRIMARY_MOB_TAG)) {
                    defeatedPrimaryCount++;
                }
                checkMobsDefeated();
            }
        }
    }

    private boolean isWithinArena(Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(pasteOrigin.getWorld())) {
            return false;
        }
        Vector size = arenaSettings.getRegionSize();
        double minX = pasteOrigin.getBlockX();
        double minY = pasteOrigin.getBlockY();
        double minZ = pasteOrigin.getBlockZ();
        return location.getX() >= minX && location.getX() <= minX + size.getX()
                && location.getY() >= minY && location.getY() <= minY + size.getY()
                && location.getZ() >= minZ && location.getZ() <= minZ + size.getZ();
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
        finished = true;
        if (exitCountdownTask != null) {
            exitCountdownTask.cancel();
            exitCountdownTask = null;
        }
        if (player.isOnline()) {
            player.teleport(returnLocation);
            player.sendMessage(color("&7The Blood Chest challenge has ended."));
        }
        removeTrackedMobs();
        if (pasteOrigin != null && pasteOrigin.getWorld() != null) {
            schematicHandler.clearRegion(pasteOrigin.getWorld(), pasteOrigin, arenaSettings.getRegionSize());
        }
        manager.endSession(this);
    }

    public void handlePlayerQuit() {
        forceStop();
    }

    public void forceStop() {
        if (finished) {
            return;
        }
        finished = true;
        if (exitCountdownTask != null) {
            exitCountdownTask.cancel();
        }
        removeTrackedMobs();
        if (pasteOrigin != null && pasteOrigin.getWorld() != null) {
            schematicHandler.clearRegion(pasteOrigin.getWorld(), pasteOrigin, arenaSettings.getRegionSize());
        }
        manager.endSession(this);
    }

    private enum SpawnType {
        PRIMARY,
        ADDITIONAL
    }

    private record SpawnAssignment(Location location, SpawnType type) {
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
