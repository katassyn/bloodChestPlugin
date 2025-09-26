package pl.yourserver.bloodChestPlugin.session;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SessionListener implements Listener {

    private final SessionManager sessionManager;

    public SessionListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean handled = sessionManager.handleChestInteract(player, block);
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        sessionManager.handleEntityDeath(event.getEntity());
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        sessionManager.handleEntitySpawn(event.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.handlePlayerQuit(event.getPlayer());
    }
}
