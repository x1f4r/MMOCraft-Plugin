package com.x1f4r.mmocraft.skill.service;

import com.x1f4r.mmocraft.skill.model.Skill;
import com.x1f4r.mmocraft.skill.model.SkillType;
import com.x1f4r.mmocraft.util.LoggingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRegistryServiceTest {

    @Mock
    private LoggingUtil mockLogger;
    @Mock // Added
    private com.x1f4r.mmocraft.core.MMOCraftPlugin mockPlugin; // Added for TestSkill constructor

    private BasicSkillRegistryService skillRegistryService;

    // Dummy Skill implementation for testing
    private static class TestSkill extends Skill {
        public TestSkill(com.x1f4r.mmocraft.core.MMOCraftPlugin plugin, String skillId, String skillName) { // Added plugin param
            super(plugin, skillId, skillName, "A test skill.", 0.0, 0.0, 0.0, SkillType.PASSIVE); // Added plugin, changed ints to doubles
        }
        @Override
        public void execute(com.x1f4r.mmocraft.playerdata.model.PlayerProfile casterProfile, org.bukkit.entity.Entity targetEntity, org.bukkit.Location targetLocation) {
            // Do nothing for test
        }
    }

    @BeforeEach
    void setUp() {
        skillRegistryService = new BasicSkillRegistryService(mockLogger);
    }

    @Test
    void registerSkill_newSkill_registersSuccessfully() {
        Skill skill1 = new TestSkill(mockPlugin, "fireball", "Fireball"); // Pass mockPlugin
        skillRegistryService.registerSkill(skill1);

        Optional<Skill> retrievedSkill = skillRegistryService.getSkill("fireball");
        assertTrue(retrievedSkill.isPresent());
        assertEquals("Fireball", retrievedSkill.get().getSkillName());
        verify(mockLogger).info("Registered skill: Fireball (ID: fireball)");
    }

    @Test
    void registerSkill_nullSkill_logsWarningAndDoesNotRegister() {
        skillRegistryService.registerSkill(null);
        verify(mockLogger).warning("Attempted to register a null skill or a skill with an invalid ID.");
        assertTrue(skillRegistryService.getAllSkills().isEmpty());
    }

    @Test
    void registerSkill_skillWithNullId_logsWarning() {
        // Skill skillWithNullId = new TestSkill(mockPlugin, null, "Nameless Skill"); // Pass mockPlugin
        // This will actually throw NullPointerException in Skill constructor if ID is null.
        // If ID can be set to null post-construction (not possible with final field):
        // skillRegistryService.registerSkill(skillWithNullId);
        // verify(mockLogger).warning(contains("null skill or a skill with an invalid ID"));

        // Test the constructor directly for this case
        assertThrows(NullPointerException.class, () -> new TestSkill(mockPlugin, null, "Test")); // Pass mockPlugin
    }


    @Test
    void registerSkill_duplicateSkillId_overwritesAndLogsWarning() {
        Skill skill1 = new TestSkill(mockPlugin, "heal", "Minor Heal"); // Pass mockPlugin
        Skill skill2 = new TestSkill(mockPlugin, "heal", "Greater Heal"); // Pass mockPlugin

        skillRegistryService.registerSkill(skill1);
        skillRegistryService.registerSkill(skill2);

        Optional<Skill> retrievedSkill = skillRegistryService.getSkill("heal");
        assertTrue(retrievedSkill.isPresent());
        assertEquals("Greater Heal", retrievedSkill.get().getSkillName(), "Should be overwritten by the new skill.");
        verify(mockLogger).warning(contains("Skill ID 'heal' was already registered. Overwriting"));
    }

    @Test
    void getSkill_existingSkillId_returnsSkill() {
        Skill skill1 = new TestSkill(mockPlugin, "dash", "Quick Dash"); // Pass mockPlugin
        skillRegistryService.registerSkill(skill1);

        Optional<Skill> retrieved = skillRegistryService.getSkill("dash");
        assertTrue(retrieved.isPresent());
        assertEquals(skill1, retrieved.get());
    }

    @Test
    void getSkill_nonExistentSkillId_returnsEmptyOptional() {
        Optional<Skill> retrieved = skillRegistryService.getSkill("non_existent_skill");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void getSkill_nullId_returnsEmptyOptional() {
        Optional<Skill> retrieved = skillRegistryService.getSkill(null);
        assertFalse(retrieved.isPresent());
    }

    @Test
    void getAllSkills_noSkills_returnsEmptyCollection() {
        assertTrue(skillRegistryService.getAllSkills().isEmpty());
    }

    @Test
    void getAllSkills_multipleSkills_returnsAllRegistered() {
        Skill skill1 = new TestSkill(mockPlugin, "s1", "Skill One"); // Pass mockPlugin
        Skill skill2 = new TestSkill(mockPlugin, "s2", "Skill Two"); // Pass mockPlugin
        skillRegistryService.registerSkill(skill1);
        skillRegistryService.registerSkill(skill2);

        Collection<Skill> allSkills = skillRegistryService.getAllSkills();
        assertEquals(2, allSkills.size());
        assertTrue(allSkills.contains(skill1));
        assertTrue(allSkills.contains(skill2));
    }

    @Test
    void getAllSkills_collectionIsUnmodifiable() {
        Skill skill1 = new TestSkill(mockPlugin, "s1", "Skill One"); // Pass mockPlugin
        skillRegistryService.registerSkill(skill1);
        Collection<Skill> allSkills = skillRegistryService.getAllSkills();
        assertThrows(UnsupportedOperationException.class, () -> allSkills.add(new TestSkill(mockPlugin, "s3", "Bad Skill"))); // Pass mockPlugin
    }

    @Test
    void unregisterSkill_existingSkill_removesAndReturnsTrue() {
        Skill skill1 = new TestSkill(mockPlugin, "shield_bash", "Shield Bash"); // Pass mockPlugin
        skillRegistryService.registerSkill(skill1);
        assertTrue(skillRegistryService.getSkill("shield_bash").isPresent());

        boolean result = skillRegistryService.unregisterSkill("shield_bash");
        assertTrue(result);
        assertFalse(skillRegistryService.getSkill("shield_bash").isPresent());
        verify(mockLogger).info("Unregistered skill: Shield Bash (ID: shield_bash)");
    }

    @Test
    void unregisterSkill_nonExistentSkill_returnsFalse() {
        boolean result = skillRegistryService.unregisterSkill("non_existent");
        assertFalse(result);
    }

    @Test
    void unregisterSkill_nullId_returnsFalse() {
        boolean result = skillRegistryService.unregisterSkill(null);
        assertFalse(result);
    }
}
