package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.ArenaSlot;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.SpawnLocation;

import java.util.Objects;

public class ArenaSlotInstance {

    private final ArenaSlot configuration;
    private final Location origin;
    private boolean busy;

    public ArenaSlotInstance(ArenaSlot configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.origin = createLocation(configuration.getOrigin());
        this.busy = false;
    }

    private Location createLocation(SpawnLocation spawnLocation) {
        World world = Bukkit.getWorld(spawnLocation.getWorld());
        if (world == null) {
            throw new IllegalStateException("World " + spawnLocation.getWorld() + " is not loaded");
        }
        return new Location(world, spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(),
                spawnLocation.getYaw(), spawnLocation.getPitch());
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public Location getOrigin() {
        return origin.clone();
    }

    public String getId() {
        return configuration.getId();
    }
}
