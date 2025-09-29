package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ArenaSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.SpawnLocation;
import pl.yourserver.bloodChestPlugin.loot.LootService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SessionManager {

    private final Plugin plugin;
    private final PluginConfiguration configuration;
    private final SchematicHandler schematicHandler;
    private final LootService lootService;
    private final PityManager pityManager;
    private final List<ArenaSlotInstance> slots = new ArrayList<>();
    private final Map<UUID, BloodChestSession> sessions = new HashMap<>();
    private final Map<UUID, Location> pendingReturns = new HashMap<>();
    private final Location returnLocation;
    private final File schematicFile;

    public SessionManager(Plugin plugin,
                          PluginConfiguration configuration,
                          SchematicHandler schematicHandler,
                          LootService lootService,
                          PityManager pityManager) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.schematicHandler = schematicHandler;
        this.lootService = lootService;
        this.pityManager = pityManager;

        ArenaSettings arenaSettings = configuration.getArenaSettings();
        for (PluginConfiguration.ArenaSlot slot : arenaSettings.getSlots()) {
            slots.add(new ArenaSlotInstance(slot));
        }
        this.returnLocation = toLocation(arenaSettings.getReturnLocation());
        this.schematicFile = new File(plugin.getDataFolder(),
                arenaSettings.getSchematicSettings().getFolder() + File.separator + arenaSettings.getSchematicSettings().getFile());
    }

    private Location toLocation(SpawnLocation spawnLocation) {
        Location location = new Location(Bukkit.getWorld(spawnLocation.getWorld()),
                spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(),
                spawnLocation.getYaw(), spawnLocation.getPitch());
        if (location.getWorld() == null) {
            throw new IllegalStateException("World " + spawnLocation.getWorld() + " is not loaded");
        }
        return location;
    }

    public synchronized BloodChestSession startSession(Player player) throws Exception {
        if (sessions.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Player already has an active blood chest session");
        }
        ArenaSlotInstance slot = slots.stream().filter(s -> !s.isBusy()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No arena instances are available. Please try again later."));
        slot.setBusy(true);
        BloodChestSession session = new BloodChestSession(plugin, configuration, schematicHandler, lootService, pityManager,
                slot, returnLocation, schematicFile, this);
        sessions.put(player.getUniqueId(), session);
        try {
            session.start(player);
            return session;
        } catch (Exception ex) {
            sessions.remove(player.getUniqueId());
            slot.setBusy(false);
            throw ex;
        }
    }

    public synchronized void endSession(BloodChestSession session) {
        sessions.remove(session.getPlayerId());
        session.getSlot().setBusy(false);
    }

    public Optional<BloodChestSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean handleChestInteract(Player player, Block block) {
        BloodChestSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            return session.handleChestInteraction(player, block);
        }
        return false;
    }

    public void handleEntityDeath(Entity entity) {
        for (BloodChestSession session : sessions.values()) {
            session.handleEntityDeath(entity);
        }
    }

    public void handleEntitySpawn(Entity entity) {
        for (BloodChestSession session : sessions.values()) {
            session.handleEntitySpawn(entity);
        }
    }

    public void handlePlayerQuit(Player player) {
        BloodChestSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.handlePlayerQuit();
        }
    }

    public void handlePlayerDeath(Player player) {
        BloodChestSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.handlePlayerDeath();
        }
    }

    public void markPendingReturn(UUID playerId) {
        pendingReturns.put(playerId, returnLocation.clone());
    }

    public Optional<Location> consumePendingReturn(UUID playerId) {
        Location location = pendingReturns.remove(playerId);
        return Optional.ofNullable(location);
    }

    public void clearPendingReturn(UUID playerId) {
        pendingReturns.remove(playerId);
    }

    public void shutdown() {
        Collection<BloodChestSession> copy = new ArrayList<>(sessions.values());
        for (BloodChestSession session : copy) {
            session.forceStop();
        }
        sessions.clear();
        pendingReturns.clear();
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
