package io.github.x1f4r.mmocraft.services;

import io.github.x1f4r.mmocraft.abilities.ItemAbility;
import io.github.x1f4r.mmocraft.abilities.list.*; // Import your specific abilities
import io.github.x1f4r.mmocraft.abilities.listeners.AbilityProjectileListener;
import io.github.x1f4r.mmocraft.abilities.listeners.PlayerAbilityActivationListener;
import io.github.x1f4r.mmocraft.core.MMOCore;
import io.github.x1f4r.mmocraft.core.Service;
import io.github.x1f4r.mmocraft.items.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer; // Added import
import org.bukkit.Sound; // Added import
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AbilityService implements Service {
    private MMOCore core;
    private LoggingService logging;
    private ItemService itemService;
    private PlayerResourceService playerResourceService;
    // NBTService is used via static keys mostly

    private final Map<String, ItemAbility> abilityRegistry = new ConcurrentHashMap<>();
    // Cooldown tracking: AbilityID (lowercase) -> (PlayerUUID -> CooldownEndTimeMillis)
    private final Map<String, Map<UUID, Long>> playerAbilityCooldowns = new ConcurrentHashMap<>();

    public AbilityService(MMOCore core) {
        this.core = core;
    }

    @Override
    public void initialize(MMOCore core) {
        this.logging = core.getService(LoggingService.class);
        this.itemService = core.getService(ItemService.class);
        this.playerResourceService = core.getService(PlayerResourceService.class);
        // NBTService static keys should already be initialized

        registerAbilities(); // Register all implemented abilities
        core.registerListener(new PlayerAbilityActivationListener(this));
        core.registerListener(new AbilityProjectileListener(core)); // Pass core for service access

        logging.info(getServiceName() + " initialized. Registered " + abilityRegistry.size() + " abilities.");
    }

    private void registerAbilities() {
        // Manually register all defined ItemAbility implementations
        registerAbility(new DragonFuryAbility());
        registerAbility(new InstantTransmissionAbility());
        registerAbility(new GlacialScytheBoltAbility()); // Example projectile ability
        registerAbility(new InstantShotBowAbility());   // Example dynamic cooldown ability
        // Add other abilities here as they are created
        // e.g., registerAbility(new HealingPulseAbility());
    }

    @Override
    public void shutdown() {
        abilityRegistry.clear();
        playerAbilityCooldowns.clear();
        logging.info(getServiceName() + " shutdown. Ability registry and cooldowns cleared.");
    }

    public void registerAbility(@Nullable ItemAbility ability) {
        if (ability == null || ability.getAbilityId() == null || ability.getAbilityId().isBlank()) {
            logging.warn("Attempted to register a null ability or an ability with a null/blank ID.");
            return;
        }
        String abilityId = ability.getAbilityId().toLowerCase(); // Standardize ID
        if (abilityRegistry.containsKey(abilityId)) {
            logging.warn("Ability ID '" + abilityId + "' is already registered. Overwriting previous: " +
                    abilityRegistry.get(abilityId).getClass().getSimpleName() + " with " +
                    ability.getClass().getSimpleName());
        }
        abilityRegistry.put(abilityId, ability);
        logging.debug("Registered ability: " + abilityId + " (" + ability.getClass().getSimpleName() + ")");
    }

    @Nullable
    public ItemAbility getAbility(@Nullable String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return null;
        return abilityRegistry.get(abilityId.toLowerCase());
    }

    public int getActualManaCost(@NotNull CustomItem itemTemplate, @NotNull ItemAbility ability) {
        Objects.requireNonNull(itemTemplate, "CustomItem template cannot be null for mana cost calculation.");
        Objects.requireNonNull(ability, "ItemAbility cannot be null for mana cost calculation.");
        return itemTemplate.overrideManaCost() != null ? itemTemplate.overrideManaCost() : ability.getDefaultManaCost();
    }

    public int getActualCooldownTicks(@NotNull CustomItem itemTemplate, @NotNull ItemAbility ability) {
        Objects.requireNonNull(itemTemplate, "CustomItem template cannot be null for cooldown calculation.");
        Objects.requireNonNull(ability, "ItemAbility cannot be null for cooldown calculation.");
        // For abilities with dynamic cooldowns (like InstantShotBow based on shooting speed),
        // their canExecute() or execute() should handle the specific cooldown logic.
        // This method returns the *fixed* cooldown.
        return itemTemplate.overrideCooldownTicks() != null ? itemTemplate.overrideCooldownTicks() : ability.getDefaultCooldownTicks();
    }

    /**
     * Attempts to execute an ability for a player.
     * Checks item, ability validity, activation conditions, mana, cooldowns, and then executes.
     *
     * @param player The player attempting to use the ability.
     * @param itemStack The ItemStack that might trigger the ability.
     * @param action The Bukkit Action (e.g., RIGHT_CLICK_AIR).
     * @param hand The hand used for interaction (can be null, try to deduce if so).
     * @return true if an ability was found and processed (regardless of success/failure), false if no ability was relevant.
     */
    public boolean attemptToExecuteAbility(@NotNull Player player, @Nullable ItemStack itemStack, @NotNull Action action, @Nullable EquipmentSlot hand) {
        if (itemStack == null || itemStack.getType().isAir()) {
            // If item is null/air but hand is main, check offhand for right-click (common for dual-wielding style abilities)
            if (hand == EquipmentSlot.HAND && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
                ItemStack offHandItem = player.getInventory().getItemInOffHand();
                if (offHandItem != null && !offHandItem.getType().isAir()) {
                    // Try executing with offhand item
                    return attemptToExecuteAbilityOnSpecificItem(player, offHandItem, action, EquipmentSlot.OFF_HAND);
                }
            }
            return false; // No item to process
        }
        return attemptToExecuteAbilityOnSpecificItem(player, itemStack, action, hand);
    }

    private boolean attemptToExecuteAbilityOnSpecificItem(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull Action action, @Nullable EquipmentSlot hand) {
        CustomItem customItemTemplate = itemService.getCustomItemTemplateFromItemStack(itemStack);
        if (customItemTemplate == null || customItemTemplate.linkedAbilityId() == null) {
            return false; // Not a custom item with a linked ability
        }

        ItemAbility ability = getAbility(customItemTemplate.linkedAbilityId());
        if (ability == null) {
            logging.warnOnce("UndefinedAbility_" + customItemTemplate.id(), "Item " + customItemTemplate.id() + " (player " + player.getName() +
                    ") has undefined ability ID: '" + customItemTemplate.linkedAbilityId() + "'. This message will show once per item type.");
            return false; // Ability not registered
        }

        // Check activation action
        if (!ability.matchesActivationAction(action)) {
            return false; // Wrong action type for this ability
        }

        // Check activation slot (if hand is known)
        // If hand is null (e.g. from some PlayerInteractEvent cases), we might assume MAIN_HAND or skip this check.
        // PlayerAbilityActivationListener tries to deduce hand.
        EquipmentSlot abilityActivationSlot = ability.getActivationSlot();
        if (hand != null && abilityActivationSlot != null && hand != abilityActivationSlot) {
            // Example: ability is HAND only, but player tried to activate with OFF_HAND item (e.g. if F key swapped then right clicked)
            // Or if ability is specific to OFF_HAND.
            // This logic needs to align with how PlayerAbilityActivationListener determines the item and hand.
            // If ItemService.createItemStack puts the ability NBT on the item, and PlayerAbilityActivationListener
            // correctly passes the *actual interacting item* and its hand, this check becomes:
            // "is the item being interacted with in the hand slot specified by the ability?"
            if (!itemStack.equals(player.getInventory().getItem(abilityActivationSlot))) {
                // The item that triggered the event is not in the slot the ability expects.
                // This can happen if the event item is correct, but the ability is hardcoded to a slot.
                // More robust: if event.getHand() matches ability.getActivationSlot()
                return false;
            }
        }

        // Dynamic cooldowns are handled inside ability.canExecute() or ability.execute() for now (e.g. InstantShotBow)
        // This checks fixed cooldowns.
        int fixedCooldownTicks = getActualCooldownTicks(customItemTemplate, ability);
        if (fixedCooldownTicks > 0 && isOnCooldown(ability.getAbilityId(), player.getUniqueId(), fixedCooldownTicks)) {
            long remainingMillis = getRemainingCooldownMillis(ability.getAbilityId(), player.getUniqueId(), fixedCooldownTicks);
            player.sendActionBar(Component.text(PlainTextComponentSerializer.plainText().serialize(ability.getDisplayName()) + " on cooldown! (" +
                    String.format("%.1f", remainingMillis / 1000.0) + "s)", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.7f, 0.7f);
            return true; // Ability was processed (cooldown message sent)
        }

        int manaCost = getActualManaCost(customItemTemplate, ability);
        if (manaCost > 0 && !playerResourceService.consumeMana(player, manaCost)) { // consumeMana now updates action bar
            player.sendActionBar(Component.text("Not enough mana for " + PlainTextComponentSerializer.plainText().serialize(ability.getDisplayName()) + "! Need " + manaCost + ", have " + playerResourceService.getCurrentMana(player) + ".", NamedTextColor.BLUE));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
            return true; // Ability was processed (mana message sent)
        }

        if (!ability.canExecute(player, itemStack, customItemTemplate, core)) {
            if (manaCost > 0) playerResourceService.addMana(player, manaCost); // Refund mana if canExecute fails
            // canExecute should send its own specific "cannot execute" message/sound.
            return true; // Ability was processed (canExecute message sent)
        }

        // --- Execute the Ability ---
        boolean executionSuccess = false;
        try {
            executionSuccess = ability.execute(player, itemStack, customItemTemplate, core);
        } catch (Exception e) {
            logging.severe("Error executing ability '" + ability.getAbilityId() + "' for player " + player.getName(), e);
            player.sendMessage(Component.text("An error occurred while using " + PlainTextComponentSerializer.plainText().serialize(ability.getDisplayName()) + ".", NamedTextColor.RED));
            executionSuccess = false; // Ensure it's marked as failed
        }


        if (executionSuccess) {
            if (fixedCooldownTicks > 0) { // Only set fixed cooldown if defined > 0
                setCooldown(ability.getAbilityId(), player.getUniqueId(), fixedCooldownTicks);
            }
            // Specific post-execution actions if needed by ability type
            if ("instant_transmission".equalsIgnoreCase(ability.getAbilityId())) {
                player.setFallDistance(0f);
            }
        } else {
            if (manaCost > 0) playerResourceService.addMana(player, manaCost); // Refund mana on internal execution failure
            // The ability.execute() method itself should message the player if it returns false for a known reason.
            // If an exception occurred, player got a generic error message.
            logging.warn("Ability '" + ability.getAbilityId() + "' execute() method returned false for player " + player.getName());
        }
        return true; // Ability was found and processed (even if it failed execution internally)
    }

    public boolean isOnCooldown(@NotNull String abilityId, @NotNull UUID playerId, int cooldownTicks) {
        if (cooldownTicks <= 0) return false;
        Map<UUID, Long> cooldownMap = playerAbilityCooldowns.get(abilityId.toLowerCase());
        if (cooldownMap == null) return false;
        long lastUsedTime = cooldownMap.getOrDefault(playerId, 0L);
        return (System.currentTimeMillis() - lastUsedTime) < (cooldownTicks * 50L);
    }

    public long getRemainingCooldownMillis(@NotNull String abilityId, @NotNull UUID playerId, int cooldownTicks) {
        if (cooldownTicks <= 0) return 0L;
        Map<UUID, Long> cooldownMap = playerAbilityCooldowns.get(abilityId.toLowerCase());
        if (cooldownMap == null) return 0L;
        long lastUsedTime = cooldownMap.getOrDefault(playerId, 0L);
        if (lastUsedTime == 0L) return 0L;

        long timePassedMillis = System.currentTimeMillis() - lastUsedTime;
        long totalCooldownMillis = cooldownTicks * 50L;
        return Math.max(0L, totalCooldownMillis - timePassedMillis);
    }

    public void setCooldown(@NotNull String abilityId, @NotNull UUID playerId, int actualCooldownTicks) {
        if (actualCooldownTicks > 0) {
            playerAbilityCooldowns
                    .computeIfAbsent(abilityId.toLowerCase(), k -> new ConcurrentHashMap<>())
                    .put(playerId, System.currentTimeMillis());
            if (logging.isDebugMode()) {
                logging.debug("Set cooldown for ability '" + abilityId + "' on player " + playerId + " for " + actualCooldownTicks + " ticks.");
            }
        }
    }
}