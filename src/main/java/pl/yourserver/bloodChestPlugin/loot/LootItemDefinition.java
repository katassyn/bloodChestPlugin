package pl.yourserver.bloodChestPlugin.loot;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LootItemDefinition {

    private final String id;
    private final Material material;
    private final String displayName;
    private final String category;
    private final List<String> lore;
    private final int minAmount;
    private final int maxAmount;
    private final double weight;
    private final int rolls;
    private final double pityWeight;

    public LootItemDefinition(String id,
                              Material material,
                              String displayName,
                              String category,
                              List<String> lore,
                              int minAmount,
                              int maxAmount,
                              double weight,
                              int rolls,
                              double pityWeight) {
        this.id = Objects.requireNonNull(id, "id");
        this.material = Objects.requireNonNull(material, "material");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.category = Objects.requireNonNull(category, "category");
        this.lore = Collections.unmodifiableList(lore);
        this.minAmount = minAmount;
        this.maxAmount = Math.max(minAmount, maxAmount);
        this.weight = weight;
        this.rolls = Math.max(1, rolls);
        this.pityWeight = pityWeight;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getLore() {
        return lore;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public double getWeight() {
        return weight;
    }

    public int getRolls() {
        return rolls;
    }

    public double getPityWeight() {
        return pityWeight;
    }
}
