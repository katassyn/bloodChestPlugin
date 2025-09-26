package pl.yourserver.bloodChestPlugin.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ConfigUtil {

    private ConfigUtil() {
    }

    public static Material readMaterial(String value, Material fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static EntityType readEntityType(String value, EntityType fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return EntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static Vector readVector(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double x = section.getDouble("x", 0.0);
        double y = section.getDouble("y", 0.0);
        double z = section.getDouble("z", 0.0);
        return new Vector(x, y, z);
    }

    public static List<ConfigurationSection> children(ConfigurationSection parent) {
        if (parent == null) {
            return Collections.emptyList();
        }
        List<ConfigurationSection> sections = new ArrayList<>();
        for (String key : parent.getKeys(false)) {
            ConfigurationSection child = parent.getConfigurationSection(key);
            if (child != null) {
                sections.add(child);
            }
        }
        return sections;
    }
}
