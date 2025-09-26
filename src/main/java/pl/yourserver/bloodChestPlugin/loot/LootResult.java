package pl.yourserver.bloodChestPlugin.loot;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

public class LootResult {

    private final List<ItemStack> items;
    private final boolean pityGranted;

    public LootResult(List<ItemStack> items, boolean pityGranted) {
        this.items = List.copyOf(items);
        this.pityGranted = pityGranted;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public boolean isPityGranted() {
        return pityGranted;
    }
}
