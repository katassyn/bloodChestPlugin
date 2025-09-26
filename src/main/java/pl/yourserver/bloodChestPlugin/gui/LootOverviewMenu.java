package pl.yourserver.bloodChestPlugin.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.GuiSettings;
import pl.yourserver.bloodChestPlugin.loot.LootItemDefinition;
import pl.yourserver.bloodChestPlugin.loot.LootRegistry;
import pl.yourserver.bloodChestPlugin.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LootOverviewMenu implements MenuView {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final PluginConfiguration configuration;
    private final MenuManager menuManager;
    private final LootRegistry lootRegistry;
    private final Inventory inventory;

    public LootOverviewMenu(PluginConfiguration configuration, MenuManager menuManager, LootRegistry lootRegistry) {
        this.configuration = configuration;
        this.menuManager = menuManager;
        this.lootRegistry = lootRegistry;
        this.inventory = Bukkit.createInventory(null, 54, LEGACY.deserialize("&8Nagrody Blood Chest"));
        build();
    }

    private void build() {
        GuiSettings gui = configuration.getGuiSettings();
        ItemStack filler = ItemStackUtil.createMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        int slot = 0;
        for (Map.Entry<String, List<LootItemDefinition>> entry : lootRegistry.getItemsByCategory().entrySet()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            List<String> lore = new ArrayList<>();
            List<LootItemDefinition> items = entry.getValue();
            for (int i = 0; i < Math.min(items.size(), 10); i++) {
                lore.add(ChatColor.GRAY + "- " + items.get(i).getDisplayName());
            }
            if (items.size() > 10) {
                lore.add(ChatColor.DARK_GRAY + "... (" + (items.size() - 10) + " more)");
            }
            Material icon = items.isEmpty() ? Material.PAPER : items.get(0).getMaterial();
            ItemStack stack = ItemStackUtil.createMenuItem(icon,
                    "&6" + entry.getKey().toLowerCase().replace('_', ' '), lore);
            inventory.setItem(slot++, stack);
        }
        inventory.setItem(gui.getBackButton().getSlot(), ItemStackUtil.createMenuItem(
                gui.getBackButton().getMaterial(), gui.getBackButton().getDisplayName(), gui.getBackButton().getLore()));
        inventory.setItem(gui.getCloseButton().getSlot(), ItemStackUtil.createMenuItem(
                gui.getCloseButton().getMaterial(), gui.getCloseButton().getDisplayName(), gui.getCloseButton().getLore()));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        GuiSettings gui = configuration.getGuiSettings();
        int slot = event.getRawSlot();
        if (slot == gui.getBackButton().getSlot()) {
            menuManager.openMainMenu((Player) event.getWhoClicked());
        } else if (slot == gui.getCloseButton().getSlot()) {
            event.getWhoClicked().closeInventory();
        }
    }
}
