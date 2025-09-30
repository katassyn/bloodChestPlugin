package pl.yourserver.bloodChestPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.yourserver.bloodChestPlugin.loot.LootItemDefinition;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackUtil {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private ItemStackUtil() {
    }

    public static ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(serialize(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(serialize(line));
                }
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack createLootItem(LootItemDefinition definition, int amount) {
        ItemStack stack = new ItemStack(definition.getMaterial());
        stack.setAmount(Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(serialize(definition.getDisplayName()));
            if (!definition.getLore().isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : definition.getLore()) {
                    loreComponents.add(serialize(line));
                }
                meta.lore(loreComponents);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component serialize(String input) {
        if (input == null) {
            return Component.empty();
        }
        Component component = SERIALIZER.deserialize(input.replace("§", "&"));
        return component.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);

    }
}
