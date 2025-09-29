package pl.yourserver.bloodChestPlugin.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-based bridge to the IngredientPouch plugin.
 *
 * <p>The BloodChest plugin cannot depend on the API at compile time, so we
 * detect the plugin at runtime and access its public {@code PouchAPI}
 * interface via reflection. When the plugin or API is unavailable, the bridge
 * simply reports that no pouch support is present.</p>
 */
public class IngredientPouchBridge {

    private static final List<String> TARGET_PLUGINS = List.of("IngredientPouchPlugin", "IngredientPouch", "IPS");

    private final Logger logger;
    private final Object apiInstance;
    private final Method getItemQuantityMethod;
    private final Method updateItemQuantityMethod;
    private final Method hasPouchOpenMethod;
    private final Method updatePouchGuiMethod;
    private boolean available;

    public IngredientPouchBridge(Logger logger) {
        this.logger = logger;

        Object api = null;
        Method getQuantity = null;
        Method updateQuantity = null;
        Method hasOpen = null;
        Method updateGui = null;
        boolean bridgeAvailable = false;

        try {
            PluginManager pluginManager = Bukkit.getPluginManager();
            Plugin pouchPlugin = findPlugin(pluginManager);
            if (pouchPlugin != null && pouchPlugin.isEnabled()) {
                Method getApiMethod = pouchPlugin.getClass().getMethod("getAPI");
                api = getApiMethod.invoke(pouchPlugin);
                if (api != null) {
                    Class<?> apiClass = api.getClass();
                    getQuantity = apiClass.getMethod("getItemQuantity", String.class, String.class);
                    updateQuantity = apiClass.getMethod("updateItemQuantity", String.class, String.class, int.class);
                    // Optional methods – not critical if missing.
                    try {
                        hasOpen = apiClass.getMethod("hasPouchOpen", Player.class);
                        updateGui = apiClass.getMethod("updatePouchGUI", Player.class, String.class);
                    } catch (NoSuchMethodException ignored) {
                        hasOpen = null;
                        updateGui = null;
                    }
                    bridgeAvailable = true;
                    logger.info("[BloodChest] Connected to IngredientPouch API via reflection.");
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[BloodChest] Failed to hook IngredientPouch API: " + ex.getMessage(), ex);
        }

        this.apiInstance = api;
        this.getItemQuantityMethod = getQuantity;
        this.updateItemQuantityMethod = updateQuantity;
        this.hasPouchOpenMethod = hasOpen;
        this.updatePouchGuiMethod = updateGui;
        this.available = bridgeAvailable;
    }

    public boolean isAvailable() {
        return available && apiInstance != null && getItemQuantityMethod != null && updateItemQuantityMethod != null;
    }

    public int getQuantity(Player player, String itemId) {
        if (!isAvailable() || player == null || itemId == null || itemId.isEmpty()) {
            return 0;
        }
        try {
            UUID uuid = player.getUniqueId();
            Object result = getItemQuantityMethod.invoke(apiInstance, uuid.toString(), itemId);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception ex) {
            disableOnError(ex);
            return 0;
        }
    }

    public boolean withdraw(Player player, String itemId, int amount) {
        if (!isAvailable() || player == null || itemId == null || itemId.isEmpty() || amount <= 0) {
            return false;
        }
        try {
            UUID uuid = player.getUniqueId();
            Object result = updateItemQuantityMethod.invoke(apiInstance, uuid.toString(), itemId, -amount);
            boolean success = !(result instanceof Boolean) || (Boolean) result;
            if (success) {
                notifyGui(player, itemId);
            }
            return success;
        } catch (Exception ex) {
            disableOnError(ex);
            return false;
        }
    }

    private void notifyGui(Player player, String itemId) {
        if (hasPouchOpenMethod == null || updatePouchGuiMethod == null) {
            return;
        }
        try {
            Object open = hasPouchOpenMethod.invoke(apiInstance, player);
            if (open instanceof Boolean && (Boolean) open) {
                updatePouchGuiMethod.invoke(apiInstance, player, itemId);
            }
        } catch (Exception ex) {
            // GUI sync failures should not disable the bridge; log at fine level.
            logger.log(Level.FINE, "[BloodChest] Unable to refresh IngredientPouch GUI: " + ex.getMessage(), ex);
        }
    }

    private void disableOnError(Exception ex) {
        logger.log(Level.WARNING, "[BloodChest] IngredientPouch API became unavailable: " + ex.getMessage(), ex);
        available = false;
    }

    private Plugin findPlugin(PluginManager pluginManager) {
        for (String name : TARGET_PLUGINS) {
            Plugin plugin = pluginManager.getPlugin(name);
            if (plugin != null) {
                return plugin;
            }
        }
        return null;
    }
}

