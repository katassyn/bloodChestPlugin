package pl.yourserver.bloodChestPlugin.session;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicSessionListener implements Listener {

    private final SessionManager sessionManager;

    public MythicSessionListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        LivingEntity livingEntity = event.getLivingEntity();
        if (livingEntity == null) {
            return;
        }
        String internalName = event.getMobType() != null
                ? event.getMobType().getInternalName()
                : null;
        sessionManager.handleMythicMobSpawn(livingEntity, internalName);
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        String internalName = event.getMobType() != null
                ? event.getMobType().getInternalName()
                : null;
        sessionManager.handleMythicMobDeath(livingEntity, internalName);
    }
}
