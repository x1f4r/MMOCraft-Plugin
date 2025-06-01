package com.x1f4r.mmocraft.statuseffect.manager;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.statuseffect.model.ActiveStatusEffect;
import com.x1f4r.mmocraft.statuseffect.model.StatusEffect;
import com.x1f4r.mmocraft.statuseffect.model.StatusEffectType;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BasicStatusEffectManager implements StatusEffectManager {

    private final MMOCraftPlugin plugin;
    private final LoggingUtil logger;
    private final PlayerDataService playerDataService;
    private final Map<UUID, List<ActiveStatusEffect>> activeEffectsMap = new ConcurrentHashMap<>();

    public BasicStatusEffectManager(MMOCraftPlugin plugin, LoggingUtil logger, PlayerDataService playerDataService) {
        this.plugin = plugin;
        this.logger = logger;
        this.playerDataService = playerDataService;
        logger.debug("BasicStatusEffectManager initialized.");
    }

    @Override
    public void applyEffect(LivingEntity target, StatusEffect effect) {
        if (target == null || effect == null) {
            logger.warning("Attempted to apply null effect or to null target.");
            return;
        }

        UUID targetId = target.getUniqueId();
        PlayerProfile targetProfile = null;
        if (target instanceof Player) {
            targetProfile = playerDataService.getPlayerProfile(targetId);
            if (targetProfile == null) {
                logger.warning("Cannot apply status effect " + effect.getEffectType() + " to player " + target.getName() + ": PlayerProfile not found in cache.");
                return;
            }
        }

        // Stacking / Overwriting / Refreshing logic would go here.
        // For now, simple: remove existing of same type, then add new one.
        // A more advanced system would check effect.canStackWith(existingEffect) or similar.
        List<ActiveStatusEffect> existingEffectsOfType = getActiveEffectsByTypeInternal(targetId, effect.getEffectType());
        for (ActiveStatusEffect existing : existingEffectsOfType) {
            // Example: if new effect is stronger or has longer duration, remove old one.
            // Or if stackable, increment stack count.
            // For simplicity now: remove all existing of this type.
            removeEffectInstanceInternal(target, existing, false); // false = don't call onRemove yet, batch it or let onApply handle
        }


        ActiveStatusEffect activeEffect = new ActiveStatusEffect(effect, targetId);
        activeEffectsMap.computeIfAbsent(targetId, k -> new ArrayList<>()).add(activeEffect);

        try {
            effect.onApply(target, targetProfile); // Apply initial effect logic
            logger.fine("Applied status effect " + effect.getEffectType() + " to " + target.getName());

            // If it was a stat buff/debuff, PlayerProfile needs recalculation
            if (targetProfile != null && isStatModifyingEffect(effect.getEffectType())) {
                targetProfile.recalculateDerivedAttributes();
            }
        } catch (Exception e) {
            logger.severe("Error during onApply for " + effect.getEffectType() + " on " + target.getName(), e);
            // Attempt to clean up if onApply failed badly
            activeEffectsMap.getOrDefault(targetId, new ArrayList<>()).remove(activeEffect);
            if (activeEffectsMap.getOrDefault(targetId, new ArrayList<>()).isEmpty()) {
                activeEffectsMap.remove(targetId);
            }
        }
    }

    private boolean isStatModifyingEffect(StatusEffectType type) {
        // Helper to identify effects that change core stats and require recalculation
        return type.name().startsWith("STAT_BUFF_") || type.name().startsWith("STAT_DEBUFF_");
    }

    private void removeEffectInstanceInternal(LivingEntity target, ActiveStatusEffect activeEffect, boolean callOnRemove) {
        if (target == null || activeEffect == null) return;

        UUID targetId = target.getUniqueId();
        PlayerProfile targetProfile = null;
        if (target instanceof Player) {
            targetProfile = playerDataService.getPlayerProfile(targetId);
        }

        if (callOnRemove) {
            try {
                activeEffect.getStatusEffect().onRemove(target, targetProfile); // Call removal logic
            } catch (Exception e) {
                logger.severe("Error during onRemove for " + activeEffect.getStatusEffect().getEffectType() + " on " + target.getName(), e);
            }
        }

        // If it was a stat buff/debuff, PlayerProfile needs recalculation after removal logic (which should revert stats)
        if (targetProfile != null && isStatModifyingEffect(activeEffect.getStatusEffect().getEffectType())) {
             targetProfile.recalculateDerivedAttributes();
        }
    }


    @Override
    public void removeEffect(LivingEntity target, StatusEffectType effectType) {
        if (target == null || effectType == null) return;
        UUID targetId = target.getUniqueId();

        List<ActiveStatusEffect> entityEffects = activeEffectsMap.get(targetId);
        if (entityEffects != null) {
            List<ActiveStatusEffect> toRemove = new ArrayList<>();
            for (ActiveStatusEffect activeEffect : entityEffects) {
                if (activeEffect.getStatusEffect().getEffectType() == effectType) {
                    toRemove.add(activeEffect);
                }
            }
            for (ActiveStatusEffect activeEffect : toRemove) {
                entityEffects.remove(activeEffect);
                removeEffectInstanceInternal(target, activeEffect, true);
                logger.fine("Removed status effect " + effectType + " from " + target.getName());
            }
            if (entityEffects.isEmpty()) {
                activeEffectsMap.remove(targetId);
            }
        }
    }

    @Override
    public void removeEffectInstance(LivingEntity target, ActiveStatusEffect activeEffectToRemove) {
        if (target == null || activeEffectToRemove == null) return;
        UUID targetId = target.getUniqueId();
        List<ActiveStatusEffect> entityEffects = activeEffectsMap.get(targetId);
        if (entityEffects != null) {
            if (entityEffects.remove(activeEffectToRemove)) {
                removeEffectInstanceInternal(target, activeEffectToRemove, true);
                logger.fine("Removed specific instance of " + activeEffectToRemove.getStatusEffect().getEffectType() + " from " + target.getName());
                if (entityEffects.isEmpty()) {
                    activeEffectsMap.remove(targetId);
                }
            }
        }
    }

    @Override
    public void removeAllEffects(LivingEntity target) {
        if (target == null) return;
        UUID targetId = target.getUniqueId();
        List<ActiveStatusEffect> entityEffects = activeEffectsMap.remove(targetId);
        if (entityEffects != null) {
            for (ActiveStatusEffect activeEffect : entityEffects) {
                 removeEffectInstanceInternal(target, activeEffect, true);
            }
            logger.info("Removed all status effects from " + target.getName());
        }
    }

    @Override
    public boolean hasEffect(LivingEntity target, StatusEffectType effectType) {
        if (target == null || effectType == null) return false;
        List<ActiveStatusEffect> entityEffects = activeEffectsMap.get(target.getUniqueId());
        if (entityEffects != null) {
            for (ActiveStatusEffect activeEffect : entityEffects) {
                if (activeEffect.getStatusEffect().getEffectType() == effectType && !activeEffect.isExpired()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<ActiveStatusEffect> getActiveEffectsOnEntity(LivingEntity target) {
        if (target == null) return Collections.emptyList();
        List<ActiveStatusEffect> effects = activeEffectsMap.get(target.getUniqueId());
        if (effects == null) return Collections.emptyList();
        // Return a copy to prevent external modification, filter out expired ones just in case.
        return effects.stream().filter(e -> !e.isExpired()).collect(Collectors.toList());
    }

    private List<ActiveStatusEffect> getActiveEffectsByTypeInternal(UUID targetId, StatusEffectType effectType) {
        List<ActiveStatusEffect> effects = activeEffectsMap.get(targetId);
        if (effects == null) return Collections.emptyList();
        return effects.stream()
                .filter(ae -> ae.getStatusEffect().getEffectType() == effectType && !ae.isExpired())
                .collect(Collectors.toList());
    }


    @Override
    public List<ActiveStatusEffect> getActiveEffectsByType(LivingEntity target, StatusEffectType effectType) {
         if (target == null || effectType == null) return Collections.emptyList();
         return getActiveEffectsByTypeInternal(target.getUniqueId(), effectType);
    }

    @Override
    public void tickAllActiveEffects() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, List<ActiveStatusEffect>> entry : activeEffectsMap.entrySet()) {
            UUID targetId = entry.getKey();
            List<ActiveStatusEffect> effects = entry.getValue();
            LivingEntity target = null; // Lazily get the entity only if needed

            List<ActiveStatusEffect> toRemove = new ArrayList<>();
            List<ActiveStatusEffect> toTick = new ArrayList<>();

            for (ActiveStatusEffect activeEffect : effects) {
                if (activeEffect.getExpirationTimeMillis() <= currentTime && !activeEffect.getStatusEffect().isPermanent()) {
                    toRemove.add(activeEffect);
                } else if (activeEffect.getStatusEffect().doesTick() && activeEffect.getNextTickTimeMillis() <= currentTime) {
                    toTick.add(activeEffect);
                }
            }

            if (!toRemove.isEmpty() || !toTick.isEmpty()) {
                target = Bukkit.getEntity(targetId) instanceof LivingEntity ? (LivingEntity) Bukkit.getEntity(targetId) : null;
                if (target == null || target.isDead()) {
                    // Target is no longer valid or dead, clear all effects
                    if(target != null) logger.fine("Target " + target.getName() + " is dead or invalid, clearing effects.");
                    else logger.fine("Target UUID " + targetId + " no longer valid, clearing effects.");
                    activeEffectsMap.remove(targetId); // Remove all effects for this UUID
                    continue; // Move to the next entry in activeEffectsMap
                }
            }

            PlayerProfile targetProfile = (target instanceof Player) ? playerDataService.getPlayerProfile(targetId) : null;

            for (ActiveStatusEffect effectToRemove : toRemove) {
                effects.remove(effectToRemove);
                try {
                    effectToRemove.getStatusEffect().onExpire(target, targetProfile);
                    logger.finer("Expired status effect " + effectToRemove.getStatusEffect().getEffectType() + " from " + target.getName());
                    if (targetProfile != null && isStatModifyingEffect(effectToRemove.getStatusEffect().getEffectType())) {
                        targetProfile.recalculateDerivedAttributes();
                    }
                } catch (Exception e) {
                    logger.severe("Error during onExpire for " + effectToRemove.getStatusEffect().getEffectType() + " on " + target.getName(), e);
                }
            }

            for (ActiveStatusEffect effectToTick : toTick) {
                try {
                    effectToTick.getStatusEffect().onTick(target, targetProfile);
                    effectToTick.updateNextTickTime(); // Schedule next tick
                     logger.finest("Ticked status effect " + effectToTick.getStatusEffect().getEffectType() + " on " + target.getName());
                    // Stat changes from ticks might also require recalculation if they are not direct health/mana changes
                    if (targetProfile != null && isStatModifyingEffect(effectToTick.getStatusEffect().getEffectType()) && effectToTick.getStatusEffect().getEffectType().name().contains("DURATION_CHANGE_ON_TICK_EXAMPLE")) {
                        // This is a hypothetical case. Most stat buffs/debuffs apply onApply/onExpire.
                        // If a tick *changes* a stat value that persists, then recalc is needed.
                        targetProfile.recalculateDerivedAttributes();
                    }
                } catch (Exception e) {
                     logger.severe("Error during onTick for " + effectToTick.getStatusEffect().getEffectType() + " on " + target.getName(), e);
                }
            }

            if (effects.isEmpty()) {
                activeEffectsMap.remove(targetId);
            }
        }
    }

    @Override
    public void shutdown() {
        logger.info("BasicStatusEffectManager shutting down. Clearing all active effects...");
        // This is a simple shutdown. For peristent effects or graceful removal,
        // one might call onExpire/onRemove for all effects on all players.
        // However, since stat modifications are in PlayerProfile (in-memory),
        // they will be gone when PlayerProfile is uncached.
        // If effects grant Bukkit PotionEffects, those should be cleared.
        for (UUID targetId : activeEffectsMap.keySet()) {
            LivingEntity target = Bukkit.getEntity(targetId) instanceof LivingEntity ? (LivingEntity) Bukkit.getEntity(targetId) : null;
            if (target != null) {
                removeAllEffects(target); // This will call onRemove/onExpire for each.
            }
        }
        activeEffectsMap.clear();
        logger.info("All active status effects cleared.");
    }
}
