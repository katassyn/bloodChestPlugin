package pl.yourserver.bloodChestPlugin.loot;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;

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
    private final boolean hideFlags;
    private final boolean hideAttributes;
    private final boolean unbreakable;
    private final Map<Enchantment, Integer> enchantments;

    public LootItemDefinition(String id,
                              Material material,
                              String displayName,
                              String category,
                              List<String> lore,
                              int minAmount,
                              int maxAmount,
                              double weight,
                              int rolls,
                              double pityWeight,
                              boolean hideFlags,
                              boolean hideAttributes,
                              boolean unbreakable,
                              Map<Enchantment, Integer> enchantments) {
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
        this.hideFlags = hideFlags;
        this.hideAttributes = hideAttributes;
        this.unbreakable = unbreakable;
        this.enchantments = enchantments == null || enchantments.isEmpty()
                ? Map.of()
                : Map.copyOf(enchantments);
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

    public boolean isHideFlags() {
        return hideFlags;
    }

    public boolean isHideAttributes() {
        return hideAttributes;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }
}
