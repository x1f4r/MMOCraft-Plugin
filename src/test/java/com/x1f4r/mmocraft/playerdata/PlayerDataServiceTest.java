package com.x1f4r.mmocraft.playerdata;

import com.x1f4r.mmocraft.core.MMOCraftPlugin;
import com.x1f4r.mmocraft.eventbus.EventBusService;
import com.x1f4r.mmocraft.persistence.PersistenceService;
import com.x1f4r.mmocraft.persistence.RowMapper;
import com.x1f4r.mmocraft.playerdata.model.PlayerProfile;
import com.x1f4r.mmocraft.playerdata.model.Stat;
import com.x1f4r.mmocraft.util.JsonUtil;
import com.x1f4r.mmocraft.util.LoggingUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerDataServiceTest {

    @Mock private MMOCraftPlugin mockPlugin; // Though BasicPlayerDataService doesn't use it directly if others are passed
    @Mock private PersistenceService mockPersistenceService;
    @Mock private LoggingUtil mockLogger;
    @Mock private EventBusService mockEventBusService;
    @Mock private ResultSet mockResultSet;

    @Captor private ArgumentCaptor<String> sqlCaptor;
    @Captor private ArgumentCaptor<Object[]> paramsCaptor;
    @Captor private ArgumentCaptor<PlayerProfile> profileCaptor;


    private BasicPlayerDataService playerDataService;

    private UUID testPlayerUUID;
    private String testPlayerName;

    @BeforeEach
    void setUp() {
        playerDataService = new BasicPlayerDataService(mockPlugin, mockPersistenceService, mockLogger, mockEventBusService);
        testPlayerUUID = UUID.randomUUID();
        testPlayerName = "TestPlayer";
    }

    @Test
    void initDatabaseSchema_shouldExecuteCorrectSQL() throws SQLException {
        playerDataService.initDatabaseSchema();
        verify(mockPersistenceService).executeUpdate(sqlCaptor.capture());
        String executedSql = sqlCaptor.getValue();
        assertTrue(executedSql.contains("CREATE TABLE IF NOT EXISTS player_profiles"));
        assertTrue(executedSql.contains("player_uuid TEXT PRIMARY KEY NOT NULL"));
        assertTrue(executedSql.contains("core_stats TEXT"));
        verify(mockLogger).info(contains("'player_profiles' table schema initialized"));
    }

    @Test
    void initDatabaseSchema_shouldLogErrorOnSQLException() throws SQLException {
        doThrow(new SQLException("Test DB Error")).when(mockPersistenceService).executeUpdate(anyString());
        playerDataService.initDatabaseSchema();
        verify(mockLogger).severe(contains("Failed to initialize 'player_profiles' table schema"), any(SQLException.class));
    }

    private PlayerProfile createNewTestProfile(UUID uuid, String name) {
        PlayerProfile profile = new PlayerProfile(uuid, name);
        Map<Stat, Double> defaultStats = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            defaultStats.put(stat, 10.0); // Example default
        }
        defaultStats.put(Stat.VITALITY, 15.0);
        profile.setCoreStats(defaultStats);
        return profile;
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadPlayerProfile_newPlayer_shouldCreateAndCacheProfile() throws Exception {
        // Arrange: Persistence service returns empty for this player
        when(mockPersistenceService.executeQuerySingle(anyString(), any(RowMapper.class), eq(testPlayerUUID.toString())))
            .thenReturn(Optional.empty());
        // Arrange: Saving the new profile should succeed
        when(mockPersistenceService.executeUpdate(anyString(), any())).thenReturn(1);


        // Act
        CompletableFuture<PlayerProfile> future = playerDataService.loadPlayerProfile(testPlayerUUID, testPlayerName);
        PlayerProfile profile = future.get(); // Wait for completion

        // Assert
        assertNotNull(profile);
        assertEquals(testPlayerUUID, profile.getPlayerUUID());
        assertEquals(testPlayerName, profile.getPlayerName());
        assertEquals(1, profile.getLevel()); // Default for new profile
        assertNotNull(profile.getCoreStats());
        assertFalse(profile.getCoreStats().isEmpty()); // Should have default stats

        // Verify it was cached
        assertSame(profile, playerDataService.getPlayerProfile(testPlayerUUID));
        // Verify it was saved (INSERT for new profile)
        verify(mockPersistenceService).executeUpdate(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().startsWith("INSERT INTO player_profiles"));
        assertEquals(testPlayerUUID.toString(), paramsCaptor.getValue()[0]);

        verify(mockLogger).info("No existing profile found for " + testPlayerName + ". Creating new profile.");
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadPlayerProfile_existingPlayer_shouldLoadAndCacheProfile() throws Exception {
        // Arrange: Mock ResultSet for existing player data
        LocalDateTime firstLogin = LocalDateTime.now().minusDays(1);
        LocalDateTime lastLogin = LocalDateTime.now().minusHours(1); // This will be updated
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        stats.put(Stat.STRENGTH, 20.0);
        stats.put(Stat.AGILITY, 18.0);

        when(mockResultSet.getString("player_uuid")).thenReturn(testPlayerUUID.toString());
        when(mockResultSet.getString("player_name")).thenReturn(testPlayerName);
        when(mockResultSet.getLong("current_health")).thenReturn(150L);
        when(mockResultSet.getLong("max_health")).thenReturn(200L);
        when(mockResultSet.getLong("current_mana")).thenReturn(75L);
        when(mockResultSet.getLong("max_mana")).thenReturn(100L);
        when(mockResultSet.getInt("level")).thenReturn(5);
        when(mockResultSet.getLong("experience")).thenReturn(5000L);
        when(mockResultSet.getLong("currency")).thenReturn(1000L);
        when(mockResultSet.getString("core_stats")).thenReturn(JsonUtil.statsMapToJson(stats));
        when(mockResultSet.getString("first_login")).thenReturn(firstLogin.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        when(mockResultSet.getString("last_login")).thenReturn(lastLogin.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        when(mockPersistenceService.executeQuerySingle(anyString(), any(RowMapper.class), eq(testPlayerUUID.toString())))
            .thenAnswer(invocation -> {
                RowMapper<PlayerProfile> mapper = invocation.getArgument(1);
                return Optional.of(mapper.mapRow(mockResultSet));
            });

        // Act
        CompletableFuture<PlayerProfile> future = playerDataService.loadPlayerProfile(testPlayerUUID, "NewTestNameIfChanged");
        PlayerProfile profile = future.get();

        // Assert
        assertNotNull(profile);
        assertEquals(testPlayerUUID, profile.getPlayerUUID());
        assertEquals("NewTestNameIfChanged", profile.getPlayerName()); // Name should be updated
        assertEquals(5, profile.getLevel());
        assertEquals(200L, profile.getMaxHealth());
        assertEquals(1000L, profile.getCurrency());
        assertEquals(20.0, profile.getStatValue(Stat.STRENGTH));
        assertTrue(profile.getLastLogin().isAfter(lastLogin)); // Last login should be updated to now-ish

        // Verify it was cached
        assertSame(profile, playerDataService.getPlayerProfile(testPlayerUUID));
        verify(mockLogger).info("Loaded profile for player: NewTestNameIfChanged (UUID: " + testPlayerUUID + ")");
    }

    @Test
    void savePlayerProfile_shouldExecuteUpdateWithCorrectData() throws Exception {
        // Arrange: Setup a profile in the cache
        PlayerProfile profileToSave = createNewTestProfile(testPlayerUUID, testPlayerName);
        profileToSave.setLevel(3);
        profileToSave.setCurrency(500);
        profileToSave.setStatValue(Stat.INTELLIGENCE, 25.0);
        playerDataService.cachePlayerProfile(profileToSave);

        LocalDateTime oldLastLogin = profileToSave.getLastLogin();

        // Act
        CompletableFuture<Void> saveFuture = playerDataService.savePlayerProfile(testPlayerUUID);
        saveFuture.get(); // Wait for save to complete

        // Assert
        verify(mockPersistenceService).executeUpdate(sqlCaptor.capture(), paramsCaptor.capture());
        String executedSql = sqlCaptor.getValue();
        Object[] capturedParams = paramsCaptor.getValue();

        // Check if it's an UPDATE or INSERT OR REPLACE (BasicPlayerDataService uses UPSERT logic)
        assertTrue(executedSql.startsWith("UPDATE") || executedSql.startsWith("INSERT OR REPLACE"));

        if (executedSql.startsWith("UPDATE")) {
            assertEquals(testPlayerName, capturedParams[0]);
            assertEquals(JsonUtil.statsMapToJson(profileToSave.getCoreStats()), capturedParams[8]);
            assertTrue(((String)capturedParams[9]).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))); // last_login updated
            assertEquals(testPlayerUUID.toString(), capturedParams[10]); // UUID is last for UPDATE
        } else { // INSERT OR REPLACE
            assertEquals(testPlayerUUID.toString(), capturedParams[0]);
            assertEquals(testPlayerName, capturedParams[1]);
            assertEquals(JsonUtil.statsMapToJson(profileToSave.getCoreStats()), capturedParams[9]);
            assertTrue(profileToSave.getLastLogin().isAfter(oldLastLogin)); // lastLogin field in object updated
        }
        assertTrue(profileToSave.getLastLogin().isAfter(oldLastLogin));
    }

    @Test
    void savePlayerProfile_profileNotCached_shouldLogWarning() throws ExecutionException, InterruptedException, SQLException { // Added SQLException
        playerDataService.savePlayerProfile(UUID.randomUUID()).get(); // Non-cached UUID
        verify(mockLogger).warning(contains("Attempted to save profile for UUID"));
        verify(mockPersistenceService, never()).executeUpdate(anyString(), any());
    }

    @Test
    void cacheAndUncachePlayerProfile_shouldWorkCorrectly() {
        PlayerProfile profile = createNewTestProfile(testPlayerUUID, testPlayerName);
        assertNull(playerDataService.getPlayerProfile(testPlayerUUID), "Profile should not be cached initially.");

        playerDataService.cachePlayerProfile(profile);
        assertSame(profile, playerDataService.getPlayerProfile(testPlayerUUID), "Profile should be cached.");
        verify(mockLogger).fine("Cached profile for player: " + testPlayerName);

        PlayerProfile uncachedProfile = playerDataService.uncachePlayerProfile(testPlayerUUID);
        assertSame(profile, uncachedProfile, "Uncached profile should be the one we cached.");
        assertNull(playerDataService.getPlayerProfile(testPlayerUUID), "Profile should be removed from cache.");
        verify(mockLogger).fine(contains("Uncached profile for player UUID: " + testPlayerUUID));
    }

    @Test
    void getDefaultStats_shouldReturnNonEmptyMap() {
        Map<Stat, Double> defaultStats = playerDataService.getDefaultStats();
        assertNotNull(defaultStats);
        assertFalse(defaultStats.isEmpty());
        assertTrue(defaultStats.containsKey(Stat.STRENGTH));
        assertTrue(defaultStats.containsKey(Stat.VITALITY));
        assertEquals(15.0, defaultStats.get(Stat.VITALITY)); // From default setup
    }
}
