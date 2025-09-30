package pl.yourserver.bloodChestPlugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import pl.yourserver.bloodChestPlugin.command.BloodChestCommand;
import pl.yourserver.bloodChestPlugin.config.ConfigurationLoader;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.gui.MenuManager;
import pl.yourserver.bloodChestPlugin.integration.IngredientPouchBridge;
import pl.yourserver.bloodChestPlugin.loot.LootLoader;
import pl.yourserver.bloodChestPlugin.loot.LootRegistry;
import pl.yourserver.bloodChestPlugin.loot.LootService;
import pl.yourserver.bloodChestPlugin.session.PityManager;
import pl.yourserver.bloodChestPlugin.session.SessionListener;
import pl.yourserver.bloodChestPlugin.session.SessionManager;
import pl.yourserver.bloodChestPlugin.session.WorldEditSchematicHandler;
import pl.yourserver.bloodChestPlugin.session.MythicSessionListener;

import java.io.File;

public final class BloodChestPlugin extends JavaPlugin {

    private PluginConfiguration configuration;
    private LootRegistry lootRegistry;
    private LootService lootService;
    private PityManager pityManager;
    private SessionManager sessionManager;
    private MenuManager menuManager;
    private IngredientPouchBridge pouchBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("items.yml");

        try {
            ConfigurationLoader configurationLoader = new ConfigurationLoader();
            configuration = configurationLoader.load(getConfig());

            File itemsFile = new File(getDataFolder(), "items.yml");
            LootLoader lootLoader = new LootLoader();
            lootRegistry = lootLoader.load(itemsFile);

            pityManager = new PityManager(this);
            lootService = new LootService(lootRegistry, configuration.getPitySettings());
            sessionManager = new SessionManager(this, configuration, new WorldEditSchematicHandler(), lootService, pityManager);
            pouchBridge = new IngredientPouchBridge(getLogger());
            menuManager = new MenuManager(this, configuration, sessionManager, pouchBridge);
        } catch (Exception ex) {
            getLogger().severe("Failed to load BloodChestPlugin configuration: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(new SessionListener(sessionManager), this);

        Plugin mythicMobs = getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs != null && mythicMobs.isEnabled()) {
            getServer().getPluginManager().registerEvents(new MythicSessionListener(sessionManager), this);
        } else {
            getLogger().warning("MythicMobs plugin not found or disabled. Mythic mob support will be limited.");
        }

        PluginCommand command = getCommand("blood_chest");
        if (command != null) {
            command.setExecutor(new BloodChestCommand(configuration, menuManager));
        } else {
            getLogger().warning("Command blood_chest is not defined in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (pityManager != null) {
            pityManager.save();
        }
    }

    private void saveResourceIfMissing(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (!target.exists()) {
            saveResource(resourcePath, false);
        }
    }
}
