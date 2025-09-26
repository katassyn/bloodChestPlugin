package pl.yourserver.bloodChestPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.integration.IngredientPouchBridge;
import pl.yourserver.bloodChestPlugin.loot.LootRegistry;
import pl.yourserver.bloodChestPlugin.session.SessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MenuManager implements Listener {

    private final Plugin plugin;
    private final PluginConfiguration configuration;
    private final SessionManager sessionManager;
    private final LootRegistry lootRegistry;
    private final IngredientPouchBridge pouchBridge;
    private final Map<UUID, MenuView> menus = new HashMap<>();

    public MenuManager(Plugin plugin,
                       PluginConfiguration configuration,
                       SessionManager sessionManager,
                       LootRegistry lootRegistry,
                       IngredientPouchBridge pouchBridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.sessionManager = sessionManager;
        this.lootRegistry = lootRegistry;
        this.pouchBridge = pouchBridge;
    }

    public void openMainMenu(Player player) {
        MenuView menu = new MainMenuView(configuration, sessionManager, this, pouchBridge, player);
        openMenu(player, menu);
    }

    public void openLootMenu(Player player) {
        MenuView menu = new LootOverviewMenu(configuration, this, lootRegistry);
        openMenu(player, menu);
    }

    private void openMenu(Player player, MenuView menu) {
        menus.put(player.getUniqueId(), menu);
        menu.onOpen(player);
        player.openInventory(menu.getInventory());
    }

    public Optional<MenuView> getMenu(Player player) {
        return Optional.ofNullable(menus.get(player.getUniqueId()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuView menu = menus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        if (!event.getInventory().equals(menu.getInventory())) {
            return;
        }
        event.setCancelled(true);
        menu.onClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        MenuView menu = menus.get(player.getUniqueId());
        if (menu != null && menu.getInventory().equals(event.getInventory())) {
            menus.remove(player.getUniqueId());
            menu.onClose(event);
        }
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public PluginConfiguration getConfiguration() {
        return configuration;
    }
}
