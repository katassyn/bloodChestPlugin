package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PityManager {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Integer> progress = new HashMap<>();

    public PityManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pity-data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (String key : configuration.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int value = configuration.getInt(key, 0);
                progress.put(uuid, value);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public int incrementAndGet(UUID uuid) {
        int value = progress.getOrDefault(uuid, 0) + 1;
        progress.put(uuid, value);
        return value;
    }

    public void reset(UUID uuid) {
        progress.remove(uuid);
    }

    public int get(UUID uuid) {
        return progress.getOrDefault(uuid, 0);
    }

    public void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : progress.entrySet()) {
            configuration.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            configuration.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pity data: " + ex.getMessage());
        }
    }
}
