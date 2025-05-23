package io.github.x1f4r.mmocraft.combat.listeners;

import io.github.x1f4r.mmocraft.services.CombatService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageCalculationListener implements Listener {
    private final CombatService combatService;

    public DamageCalculationListener(CombatService combatService) {
        this.combatService = combatService;
    }

    // Priority HIGH: We want our calculations to be a primary influence on the final damage.
    // ignoreCancelled = true: If another plugin (e.g. protection) cancels the event at a lower priority,
    // we respect that and don't try to calculate damage for a cancelled event.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        combatService.processDamageByEntityEvent(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        // Avoid double-processing if it's also an EntityDamageByEntityEvent
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        // Process for other damage causes if the victim is a LivingEntity
        if (event.getEntity() instanceof LivingEntity victim && !victim.isDead()) {
            combatService.handleEnvironmentalDamage(victim, event.getDamage(), event.getCause(), event);
        }
    }
}