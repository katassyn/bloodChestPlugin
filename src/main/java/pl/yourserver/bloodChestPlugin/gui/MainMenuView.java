package pl.yourserver.bloodChestPlugin.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.GuiSettings;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration.KeyRequirement;
import pl.yourserver.bloodChestPlugin.integration.IngredientPouchBridge;
import pl.yourserver.bloodChestPlugin.session.SessionManager;
import pl.yourserver.bloodChestPlugin.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;

public class MainMenuView implements MenuView {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final PluginConfiguration configuration;
    private final SessionManager sessionManager;
    private final IngredientPouchBridge pouchBridge;
    private final Inventory inventory;
    private final Player player;

    public MainMenuView(PluginConfiguration configuration,
                        SessionManager sessionManager,
                        IngredientPouchBridge pouchBridge,
                        Player player) {
        this.configuration = configuration;
        this.sessionManager = sessionManager;
        this.pouchBridge = pouchBridge;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, LEGACY.deserialize(configuration.getGuiSettings().getTitle()));
        build();
    }

    private void build() {
        GuiSettings gui = configuration.getGuiSettings();
        ItemStack filler = ItemStackUtil.createMenuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        ItemStack instructions = ItemStackUtil.createMenuItem(Material.BOOK, "&6Instructions", gui.getInstructions());
        inventory.setItem(11, instructions);
        inventory.setItem(gui.getStartButton().getSlot(), ItemStackUtil.createMenuItem(
                gui.getStartButton().getMaterial(), gui.getStartButton().getDisplayName(), gui.getStartButton().getLore()));
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
        if (slot == gui.getStartButton().getSlot()) {
            attemptStart();
        } else if (slot == gui.getCloseButton().getSlot()) {
            event.getWhoClicked().closeInventory();
        }
    }

    private void attemptStart() {
        if (sessionManager.getSession(player.getUniqueId()).isPresent()) {
            player.sendMessage(color("&cYou are already inside a Blood Chest instance."));
            return;
        }
        KeyRequirement key = configuration.getKeyRequirement();
        if (!consumeKey(player, key)) {
            player.sendMessage(color("&cYou need " + key.getAmount() + "x " + key.getDisplayName() + " to enter."));
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(sessionManager.getPlugin(), () -> {
            try {
                sessionManager.startSession(player);
            } catch (Exception ex) {
                player.sendMessage(color("&cFailed to start the instance: " + ex.getMessage()));
                ex.printStackTrace();
            }
        });
    }

    private boolean consumeKey(Player player, KeyRequirement key) {
        int needed = key.getAmount();
        List<Integer> matchingSlots = new ArrayList<>();
        int inventoryTotal = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.getType() != key.getMaterial()) {
                continue;
            }
            if (!matchesDisplayName(stack, key.getDisplayName())) {
                continue;
            }
            inventoryTotal += stack.getAmount();
            matchingSlots.add(i);
            if (inventoryTotal >= needed) {
                break;
            }
        }

        int removeFromInventory = Math.min(needed, inventoryTotal);
        int remaining = needed - removeFromInventory;
        if (remaining > 0) {
            if (pouchBridge == null || !pouchBridge.isAvailable() || key.getPouchItemId().isEmpty()) {
                return false;
            }
            String pouchItemId = key.getPouchItemId().get();
            int pouchQuantity = pouchBridge.getQuantity(player, pouchItemId);
            if (pouchQuantity < remaining) {
                return false;
            }
            if (!pouchBridge.withdraw(player, pouchItemId, remaining)) {
                return false;
            }
        }

        if (removeFromInventory > 0) {
            deductFromInventory(matchingSlots, removeFromInventory);
        }

        player.updateInventory();
        return true;
    }

    private void deductFromInventory(List<Integer> matchingSlots, int amountToRemove) {
        int remaining = amountToRemove;
        for (int slot : matchingSlots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null) {
                continue;
            }
            int remove = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - remove);
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            remaining -= remove;
        }
    }

    private boolean matchesDisplayName(ItemStack stack, String requiredName) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return false;
        }
        String itemName = ChatColor.stripColor(LEGACY.serialize(meta.displayName()));
        String needed = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', requiredName));
        return itemName.equalsIgnoreCase(needed);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public Player getPlayer() {
        return player;
    }
}
