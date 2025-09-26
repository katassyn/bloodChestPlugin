package pl.yourserver.bloodChestPlugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public interface MenuView {

    Inventory getInventory();

    void onClick(InventoryClickEvent event);

    default void onClose(InventoryCloseEvent event) {
    }

    default void onOpen(Player player) {
    }
}
