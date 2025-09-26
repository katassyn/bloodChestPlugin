package pl.yourserver.bloodChestPlugin.session;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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

public class BloodChestSession {

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
    private final List<Location> chestLocations = new ArrayList<>();
    private final Map<Location, Boolean> spawnedChests = new HashMap<>();
    private final Set<UUID> activeMobIds = new HashSet<>();
    private final Set<UUID> defeatedMobIds = new HashSet<>();
    private final List<Location> pendingSpawnAssignments = new ArrayList<>();
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
        spawnMobs(world);
        player.sendMessage(color("&7Pokonaj wszystkie &cKrwiste Błotniaki &7jak najszybciej!"));
    }

    private void ensureSchematicExists() throws IOException {
        if (!schematicFile.exists()) {
            throw new IOException("Nie znaleziono schematu areny: " + schematicFile.getAbsolutePath());
        }
    }

    private void scanMarkers(World world) {
        mobSpawnLocations.clear();
        chestLocations.clear();
        Vector size = arenaSettings.getRegionSize();
        int baseX = pasteOrigin.getBlockX();
        int baseY = pasteOrigin.getBlockY();
        int baseZ = pasteOrigin.getBlockZ();

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
                    }
                }
            }
        }
    }

    private void teleportPlayerToArena() {
        Location destination = slot.getOrigin().clone().add(arenaSettings.getPlayerSpawnOffset());
        player.teleport(destination);
    }

    private void spawnMobs(World world) {
        MobSettings mobSettings = arenaSettings.getMobSettings();
        int required = Math.min(5, mobSpawnLocations.size());
        if (required <= 0) {
            plugin.getLogger().warning("No mob spawn markers found for blood chest session");
            return;
        }
        for (int i = 0; i < required; i++) {
            Location spawnLocation = mobSpawnLocations.get(i);
            if (mobSettings.getSpawnMode() == SpawnMode.MYTHIC_COMMAND) {
                pendingSpawnAssignments.add(spawnLocation);
                String command = buildSpawnCommand(mobSettings, spawnLocation);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else {
                EntityType type = mobSettings.getFallbackEntityType();
                Entity entity = world.spawnEntity(spawnLocation, type);
                entity.addScoreboardTag("blood_chest_mob");
                activeMobIds.add(entity.getUniqueId());
            }
        }
    }

    private String buildSpawnCommand(MobSettings mobSettings, Location location) {
        String command = mobSettings.getSpawnCommand();
        if (command == null || command.isEmpty()) {
            command = "mm mobs spawn {id} {world} {x} {y} {z}";
        }
        String id = mobSettings.getMythicMobId() != null ? mobSettings.getMythicMobId() : "";
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
        if (!pendingSpawnAssignments.isEmpty()) {
            if (mobSettings.getMetadataKey() != null && !mobSettings.getMetadataKey().isEmpty()) {
                if (!entity.hasMetadata(mobSettings.getMetadataKey())) {
                    return;
                }
            }
            Iterator<Location> iterator = pendingSpawnAssignments.iterator();
            while (iterator.hasNext()) {
                Location expected = iterator.next();
                if (expected.getWorld() == entity.getWorld() && expected.distanceSquared(entity.getLocation()) <= 4.0) {
                    iterator.remove();
                    activeMobIds.add(entity.getUniqueId());
                    entity.addScoreboardTag("blood_chest_mob");
                    break;
                }
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
        if (activeMobIds.remove(uuid)) {
            defeatedMobIds.add(uuid);
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
            if (isWithinArena(entity.getLocation())) {
                defeatedMobIds.add(uuid);
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
        int required = Math.min(5, mobSpawnLocations.size());
        if (defeatedMobIds.size() >= required && activeMobIds.isEmpty()) {
            mobsDefeated = true;
            onMobsDefeated();
        }
    }

    private void onMobsDefeated() {
        long elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000L;
        int chestCount = determineChestCount((int) elapsedSeconds);
        player.sendMessage(color("&7Wszystkie błotniaki zostały pokonane w &e" + elapsedSeconds + "s&7!"));
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
                tileState.customName(Component.text(ChatColor.translateAlternateColorCodes('&', chestSettings.getDisplayName())));
                List<Component> lore = chestSettings.getLore().stream()
                        .map(line -> Component.text(ChatColor.translateAlternateColorCodes('&', line)))
                        .toList();
                tileState.lore(lore);
                tileState.update();
            }
            spawnedChests.put(location, Boolean.FALSE);
        }
        player.sendMessage(color("&cPojawiły się " + available + " Krwawe Skrzynie!"));
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
        block.setType(Material.AIR, false);
        LootResult result = lootService.generateLoot(player.getUniqueId(), rewardSettings.getRollsPerChest(), pityManager);
        dropItems(location, result);
        if (result.isPityGranted()) {
            player.sendMessage(color("&6Pity drop został przyznany!"));
        }
        if (spawnedChests.values().stream().allMatch(Boolean::booleanValue)) {
            startExitCountdown();
        }
        return true;
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
                Title title = Title.title(Component.text(ChatColor.RED + "Powrót za"),
                        Component.text(ChatColor.GOLD + String.valueOf(remaining) + ChatColor.GRAY + " s"),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
                player.showTitle(title);
                remaining--;
            }
        };
        exitCountdownTask.runTaskTimer(plugin, 0L, 20L);
        player.sendMessage(color("&7Zostaniesz przeniesiony za &e" + exitCountdownSeconds + "s"));
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
            player.sendMessage(color("&7Zakończono wyzwanie Blood Chest."));
        }
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
        if (pasteOrigin != null && pasteOrigin.getWorld() != null) {
            schematicHandler.clearRegion(pasteOrigin.getWorld(), pasteOrigin, arenaSettings.getRegionSize());
        }
        manager.endSession(this);
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
