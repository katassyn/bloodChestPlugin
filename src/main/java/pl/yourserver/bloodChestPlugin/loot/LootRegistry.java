package pl.yourserver.bloodChestPlugin.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class LootRegistry {

    private final Map<String, LootItemDefinition> itemsById;
    private final Map<String, List<LootItemDefinition>> itemsByCategory;
    private final List<LootItemDefinition> pityPool;

    public LootRegistry(Collection<LootItemDefinition> items, Collection<String> pityPoolIds) {
        this.itemsById = new HashMap<>();
        this.itemsByCategory = new HashMap<>();
        Objects.requireNonNull(items, "items").forEach(item -> {
            itemsById.put(item.getId(), item);
            itemsByCategory.computeIfAbsent(item.getCategory(), key -> new ArrayList<>()).add(item);
        });
        this.pityPool = pityPoolIds == null ? Collections.emptyList() : pityPoolIds.stream()
                .map(itemsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<LootItemDefinition> getAll() {
        return List.copyOf(itemsById.values());
    }

    public Map<String, List<LootItemDefinition>> getItemsByCategory() {
        return itemsByCategory.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public Optional<LootItemDefinition> getById(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public List<LootItemDefinition> getPityPool() {
        return pityPool;
    }
}
