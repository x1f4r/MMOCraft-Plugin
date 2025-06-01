package com.x1f4r.mmocraft.combat.listeners;

import com.x1f4r.mmocraft.combat.model.DamageInstance;
import com.x1f4r.mmocraft.combat.model.DamageType;
import com.x1f4r.mmocraft.combat.service.DamageCalculationService;
import com.x1f4r.mmocraft.combat.service.MobStatProvider; // Added
import com.x1f4r.mmocraft.playerdata.PlayerDataService;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile; // Added for victim profile update test
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID; // Added import

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerCombatListenerTest {

    @Mock private DamageCalculationService mockDamageCalcService;
    @Mock private PlayerDataService mockPlayerDataService;
    @Mock private LoggingUtil mockLogger;
    @Mock private MobStatProvider mockMobStatProvider; // Added

    @Mock private Player mockAttackerPlayer;
    @Mock private PlayerInventory mockPlayerInventory;
    @Mock private ItemStack mockWeaponInHand;
    @Mock private LivingEntity mockVictimLivingEntity; // Can be Player or Mob
    @Mock private EntityDamageByEntityEvent mockEvent;
    @Mock private Arrow mockArrow;


    private PlayerCombatListener listener;

    @Captor ArgumentCaptor<Double> baseDamageCaptor;
    @Captor ArgumentCaptor<DamageType> damageTypeCaptor;


    @BeforeEach
    void setUp() {
        listener = new PlayerCombatListener(mockDamageCalcService, mockPlayerDataService, mockLogger, mockMobStatProvider); // Added mockMobStatProvider

        // Common event mocks
        when(mockEvent.getEntity()).thenReturn(mockVictimLivingEntity);
        lenient().when(mockEvent.isCancelled()).thenReturn(false); // Default: event not cancelled
    }

    private void setupPlayerAttackerWithWeapon(Material material) {
        when(mockEvent.getDamager()).thenReturn(mockAttackerPlayer);
        when(mockAttackerPlayer.getInventory()).thenReturn(mockPlayerInventory);
        when(mockPlayerInventory.getItemInMainHand()).thenReturn(mockWeaponInHand);
        when(mockWeaponInHand.getType()).thenReturn(material);
    }

    private void setupMobAttacker(EntityType mobType, double bukkitDamage) {
        LivingEntity mobAttacker = mock(LivingEntity.class); // Use a fresh mock for specific type if needed
        when(mobAttacker.getType()).thenReturn(mobType);
        when(mockEvent.getDamager()).thenReturn(mobAttacker);
        when(mockEvent.getDamage()).thenReturn(bukkitDamage); // Bukkit's original damage for this mob
    }


    @Test
    void onEntityDamageByEntity_playerAttacksWithDiamondSword_usesCorrectBaseDamage() {
        setupPlayerAttackerWithWeapon(Material.DIAMOND_SWORD);
        // Ensure UUIDs are available from mocks if not already
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockVictimLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        DamageInstance fakeDamageInstance = new DamageInstance(
                mockAttackerPlayer, mockVictimLivingEntity,
                mockAttackerPlayer.getUniqueId(), mockVictimLivingEntity.getUniqueId(),
                null, null, // attackerProfile, victimProfile
                7.0, DamageType.PHYSICAL, // baseDamage, type
                false, false, // criticalHit, evaded
                "", 5.0 // mitigationDetails, finalDamage
        );
        when(mockDamageCalcService.calculateDamage(eq(mockAttackerPlayer), eq(mockVictimLivingEntity), baseDamageCaptor.capture(), damageTypeCaptor.capture()))
            .thenReturn(fakeDamageInstance);

        listener.onEntityDamageByEntity(mockEvent);

        assertEquals(7.0, baseDamageCaptor.getValue(), 0.01, "Base damage for Diamond Sword should be 7.0");
        assertEquals(DamageType.PHYSICAL, damageTypeCaptor.getValue());
        verify(mockEvent).setDamage(5.0);
        verify(mockLogger).info(contains("hit"));
    }

    @Test
    void onEntityDamageByEntity_playerAttacksUnarmed_usesCorrectBaseDamage() {
        setupPlayerAttackerWithWeapon(Material.AIR); // Unarmed or empty hand
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockVictimLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        DamageInstance fakeDamageInstance = new DamageInstance(
                mockAttackerPlayer, mockVictimLivingEntity,
                mockAttackerPlayer.getUniqueId(), mockVictimLivingEntity.getUniqueId(),
                null, null, 1.0, DamageType.PHYSICAL, false, false, "", 0.5
        );
        when(mockDamageCalcService.calculateDamage(any(Entity.class), any(Entity.class), baseDamageCaptor.capture(), any(DamageType.class)))
            .thenReturn(fakeDamageInstance);

        listener.onEntityDamageByEntity(mockEvent);

        assertEquals(1.0, baseDamageCaptor.getValue(), 0.01, "Base damage for unarmed should be 1.0");
        verify(mockEvent).setDamage(0.5);
    }

    @Test
    void onEntityDamageByEntity_zombieAttacks_usesMobStatProviderDamage() {
        LivingEntity mobAttacker = mock(LivingEntity.class);
        when(mobAttacker.getType()).thenReturn(EntityType.ZOMBIE);
        when(mockEvent.getDamager()).thenReturn(mobAttacker);
        // when(mockEvent.getDamage()).thenReturn(3.0); // This would be Bukkit's original, we override with MobStatProvider

        when(mockMobStatProvider.getBaseAttackDamage(EntityType.ZOMBIE)).thenReturn(5.0); // Custom base damage
        lenient().when(mobAttacker.getUniqueId()).thenReturn(UUID.randomUUID()); // mobAttacker is local mock
        lenient().when(mockVictimLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        DamageInstance fakeDamageInstance = new DamageInstance(
                mobAttacker, mockVictimLivingEntity,
                mobAttacker.getUniqueId(), mockVictimLivingEntity.getUniqueId(),
                null, null, 5.0, DamageType.PHYSICAL, false, false, "", 4.0
        );
        when(mockDamageCalcService.calculateDamage(eq(mobAttacker), eq(mockVictimLivingEntity), baseDamageCaptor.capture(), any(DamageType.class)))
            .thenReturn(fakeDamageInstance);

        listener.onEntityDamageByEntity(mockEvent);

        assertEquals(5.0, baseDamageCaptor.getValue(), 0.01, "Base damage for Zombie should be from MobStatProvider.");
        verify(mockEvent).setDamage(4.0);
    }

    @Test
    void onEntityDamageByEntity_arrowFromPlayer_resolvesShooterAndUsesEventDamage() {
        when(mockEvent.getDamager()).thenReturn(mockArrow);
        when(mockArrow.getShooter()).thenReturn(mockAttackerPlayer);
        when(mockEvent.getDamage()).thenReturn(8.5); // Bukkit's calculated arrow damage
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockVictimLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        DamageInstance fakeDamageInstance = new DamageInstance(
                mockAttackerPlayer, mockVictimLivingEntity,
                mockAttackerPlayer.getUniqueId(), mockVictimLivingEntity.getUniqueId(),
                null, null, 8.5, DamageType.PHYSICAL, false, false, "", 6.0
        );
        when(mockDamageCalcService.calculateDamage(eq(mockAttackerPlayer), eq(mockVictimLivingEntity), baseDamageCaptor.capture(), any(DamageType.class)))
            .thenReturn(fakeDamageInstance);

        listener.onEntityDamageByEntity(mockEvent);

        assertEquals(8.5, baseDamageCaptor.getValue(), 0.01, "Base damage for player's arrow should be from event.");
        verify(mockEvent).setDamage(6.0);
    }


    @Test
    void onEntityDamageByEntity_eventCancelled_shouldReturn() {
        when(mockEvent.isCancelled()).thenReturn(true);
        listener.onEntityDamageByEntity(mockEvent);
        verify(mockDamageCalcService, never()).calculateDamage(any(), any(), anyDouble(), any());
        verify(mockEvent, never()).setDamage(anyDouble());
    }

    @Test
    void onEntityDamageByEntity_victimNotLiving_shouldReturn() {
        Entity nonLivingVictim = mock(Entity.class); // Generic non-living entity
        when(mockEvent.getEntity()).thenReturn(nonLivingVictim);

        listener.onEntityDamageByEntity(mockEvent);

        verify(mockDamageCalcService, never()).calculateDamage(any(), any(), anyDouble(), any());
        verify(mockLogger).finer("Victim is not a LivingEntity, skipping custom damage calculation.");
    }

    @Test
    void onEntityDamageByEntity_evadedAttack_logsEvasionAndSetsZeroDamage() {
        setupPlayerAttackerWithWeapon(Material.IRON_SWORD);
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockVictimLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        DamageInstance evadedInstance = new DamageInstance(
            mockAttackerPlayer, mockVictimLivingEntity,
            mockAttackerPlayer.getUniqueId(), mockVictimLivingEntity.getUniqueId(),
            null, null, 6.0, DamageType.PHYSICAL,
            false, true, "Evaded.", 0.0 // Evaded = true, finalDamage = 0
        );
        when(mockDamageCalcService.calculateDamage(any(), any(), anyDouble(), any())).thenReturn(evadedInstance);

        listener.onEntityDamageByEntity(mockEvent);

        verify(mockEvent).setDamage(0.0);
        verify(mockLogger).info(contains("EVADED"));

        // If victim or attacker is a player, check for action bar message (optional, needs Player mock)
        if (mockVictimLivingEntity instanceof Player) {
            // verify((Player)mockVictimLivingEntity, times(1)).sendActionBar(anyString()); // sendActionBar is in DamageInstance now
        }
    }

    @Test
    void onEntityDamageByEntity_playerVictim_profileHealthUpdated() {
        setupPlayerAttackerWithWeapon(Material.STONE_SWORD); // Base damage 5.0
        Player mockVictimAsPlayer = mock(Player.class); // Specific Player mock for victim
        PlayerProfile mockVictimPlayerProfile = mock(PlayerProfile.class);

        when(mockEvent.getEntity()).thenReturn(mockVictimAsPlayer); // Override general victim with Player victim
        // UUID for mockVictimAsPlayer is stubbed in its mock creation if needed, or here explicitly
        lenient().when(mockVictimAsPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(mockAttackerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPlayerDataService.getPlayerProfile(mockVictimAsPlayer.getUniqueId())).thenReturn(mockVictimPlayerProfile);


        DamageInstance damageDone = new DamageInstance(
            mockAttackerPlayer, mockVictimAsPlayer,
            mockAttackerPlayer.getUniqueId(), mockVictimAsPlayer.getUniqueId(),
            null, mockVictimPlayerProfile, // victimProfile is available
            5.0, DamageType.PHYSICAL,
            false, false, "", 3.5 // Final damage after calculations
        );
        when(mockDamageCalcService.calculateDamage(any(), eq(mockVictimAsPlayer), anyDouble(), any())).thenReturn(damageDone);

        listener.onEntityDamageByEntity(mockEvent);

        verify(mockEvent).setDamage(3.5);
        verify(mockVictimPlayerProfile).takeDamage(3.5); // Check if PlayerProfile.takeDamage was called
        verify(mockLogger).info(contains("hit"));
        verify(mockLogger).fine(contains("Updated PlayerProfile health for"));
    }
}
