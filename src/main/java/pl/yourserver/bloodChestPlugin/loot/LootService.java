package pl.yourserver.bloodChestPlugin.loot;

import org.bukkit.inventory.ItemStack;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.session.PityManager;
import pl.yourserver.bloodChestPlugin.util.ItemStackUtil;
import pl.yourserver.bloodChestPlugin.util.WeightedPicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class LootService {

    private final LootRegistry registry;
    private final PluginConfiguration.PitySettings pitySettings;
    private final List<LootItemDefinition> allItems;
    private final List<LootItemDefinition> pityPool;

    public LootService(LootRegistry registry, PluginConfiguration.PitySettings pitySettings) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.pitySettings = Objects.requireNonNull(pitySettings, "pitySettings");
        this.allItems = registry.getAll();
        this.pityPool = registry.getPityPool();
    }

    public LootResult generateLoot(UUID playerId, int rollsPerChest, PityManager pityManager) {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < rollsPerChest; i++) {
            LootItemDefinition definition = WeightedPicker.pick(allItems, LootItemDefinition::getWeight);
            if (definition == null) {
                continue;
            }
            for (int roll = 0; roll < definition.getRolls(); roll++) {
                int amount = randomAmount(definition.getMinAmount(), definition.getMaxAmount());
                drops.add(ItemStackUtil.createLootItem(definition, amount));
            }
        }

        boolean pityGranted = false;
        if (pityManager != null) {
            int progress = pityManager.incrementAndGet(playerId);
            if (progress >= pitySettings.getThreshold() && !pityPool.isEmpty()) {
                LootItemDefinition pityDrop = WeightedPicker.pick(pityPool, LootItemDefinition::getPityWeight);
                if (pityDrop != null) {
                    int amount = randomAmount(pityDrop.getMinAmount(), pityDrop.getMaxAmount());
                    drops.add(ItemStackUtil.createLootItem(pityDrop, amount));
                    pityGranted = true;
                }
                pityManager.reset(playerId);
            }
        }

        return new LootResult(drops, pityGranted);
    }

    private int randomAmount(int min, int max) {
        if (max <= min) {
            return Math.max(1, min);
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
