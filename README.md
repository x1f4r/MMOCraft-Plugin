# MMOCraft Plugin

A comprehensive Minecraft plugin that transforms vanilla gameplay into an MMO-style experience with custom items, stats, abilities, AI-enhanced mobs, and advanced crafting systems.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Development Guide](#development-guide)
- [Configuration](#configuration)
- [Commands & Permissions](#commands--permissions)
- [API Reference](#api-reference)
- [Performance & Monitoring](#performance--monitoring)
- [Contributing](#contributing)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

MMOCraft is a modular Minecraft plugin built on the **Purpur** server platform (Paper/Spigot compatible) that adds RPG elements to vanilla Minecraft. The plugin features a service-oriented architecture with comprehensive configuration systems, performance monitoring, and administrative tools.

### 🎮 Key Features

- **Custom Player Stats System**: MaxHealth, MaxMana, Strength, Defense, Intelligence, Speed, Crit Chance/Damage
- **Advanced Item System**: Custom items with NBT stats, abilities, and attribute modifiers
- **AI-Enhanced Mobs**: Custom mob types with intelligent behaviors and loot tables
- **Ability System**: Item-based abilities with cooldowns and visual effects
- **Crafting Enhancements**: Custom recipes and crafting GUI systems
- **Tool Proficiency**: Mining, foraging, fishing, and shooting speed improvements
- **Performance Monitoring**: Built-in performance tracking and administrative commands
- **Configuration Validation**: Comprehensive YAML validation with helpful error messages

## 🏗️ Architecture

### Core Design Principles

1. **Service-Oriented Architecture**: All major functionality is implemented as independent services
2. **Dependency Injection**: Services are registered and retrieved through the MMOCore
3. **Event-Driven**: Extensive use of Bukkit events for loose coupling
4. **Configuration-First**: All behavior is configurable via YAML files
5. **Performance-Conscious**: Built-in caching, cleanup tasks, and monitoring

### 📁 Project Structure

```
src/main/java/io/github/x1f4r/mmocraft/
├── MMOCraft.java                 # Main plugin class
├── abilities/                    # Item abilities system
│   ├── AbstractItemAbility.java
│   ├── ItemAbility.java
│   ├── list/                    # Specific ability implementations
│   └── listeners/               # Ability event handlers
├── ai/                          # Mob AI system
│   ├── AIBehavior.java
│   ├── AIController.java
│   └── behaviors/               # AI behavior implementations
├── commands/                    # Command system
│   ├── AbstractMMOCommand.java  # Base command class
│   ├── admin/                   # Administrative commands
│   └── user/                    # Player commands
├── constants/                   # Centralized constants
│   └── MMOConstants.java
├── core/                        # Core framework
│   ├── MMOCore.java            # Service container
│   └── Service.java            # Service interface
├── entities/                    # Custom entities and stats
├── items/                       # Custom item system
├── player/                      # Player data and stats
│   ├── PlayerProfile.java
│   ├── stats/
│   └── listeners/
├── services/                    # Service implementations
│   ├── ConfigService.java      # Configuration management
│   ├── PlayerStatsService.java # Player statistics
│   ├── LoggingService.java     # Centralized logging
│   └── ...                     # Other services
└── utils/                       # Utility classes
```

### 🔧 Service Architecture

The plugin uses a centralized service container (`MMOCore`) that manages:

- **Service Registration**: Services are registered during plugin initialization
- **Dependency Resolution**: Services can access other services through the core
- **Lifecycle Management**: Automatic initialization and shutdown of services
- **Thread Safety**: ConcurrentHashMap for service storage with proper ordering

#### Core Services

| Service | Purpose | Dependencies |
|---------|---------|--------------|
| `LoggingService` | Centralized logging with debug mode | None |
| `ConfigService` | Configuration management & validation | LoggingService |
| `PersistenceService` | Player data persistence (PDC) | LoggingService |
| `PlayerDataService` | Player profile management | ConfigService |
| `PlayerStatsService` | Stat calculation & attribute management | PlayerDataService, NBTService |
| `ItemService` | Custom item management | ConfigService |
| `AbilityService` | Item abilities & cooldowns | ItemService |

## 🚀 Quick Start

### Prerequisites

- **Java 21** or higher
- **Purpur Server** 1.21.5-R0.1 (or Paper/Spigot compatible)
- **Gradle** for building

### Installation

1. **Download or Build**:
   ```bash
   git clone https://github.com/your-repo/MMOCraft-Plugin.git
   cd MMOCraft-Plugin
   ./gradlew shadowJar
   ```

2. **Install Plugin**:
   - Copy `build/libs/MMOCraft-0.1.0-SNAPSHOT.jar` to your server's `plugins/` folder
   - Start/restart your server

3. **Verify Installation**:
   ```
   /mmocadmin system performance
   ```

### Basic Configuration

The plugin creates default configuration files on first run:

- `config.yml` - Main plugin settings
- `items.yml` - Custom item definitions
- `mobs.yml` - Mob configurations
- `recipes.yml` - Custom crafting recipes
- `loot_tables.yml` - Mob drop tables

## 💻 Development Guide

### Setting Up Development Environment

1. **Clone Repository**:
   ```bash
   git clone https://github.com/x1f4r/MMOCraft-Plugin.git
   cd MMOCraft-Plugin
   ```

2. **Import to IDE**:
   - IntelliJ IDEA: Import as Gradle project
   - Eclipse: Use Gradle integration
   - VS Code: Install Java extensions

3. **Configure Server**:
   ```bash
   # Add to your test server's start script
   java -jar purpur-1.21.5-R0.1.jar --noconsole
   ```

### Creating a New Service

1. **Implement Service Interface**:
   ```java
   public class MyCustomService implements Service {
       private MMOCore core;
       private LoggingService logging;
       
       public MyCustomService(MMOCore core) {
           this.core = core;
       }
       
       @Override
       public void initialize(MMOCore core) {
           this.logging = core.getService(LoggingService.class);
           logging.info("MyCustomService initialized");
       }
       
       @Override
       public String getServiceName() {
           return "MyCustomService";
       }
       
       @Override
       public void shutdown() {
           logging.info("MyCustomService shutdown");
       }
   }
   ```

2. **Register Service**:
   ```java
   // In MMOCore constructor
   registerService(new MyCustomService(this), MyCustomService.class);
   ```

### Adding Custom Items

1. **Define in items.yml**:
   ```yaml
   custom_items:
     flame_sword:
       material: DIAMOND_SWORD
       display_name: "&cFlame Sword"
       lore:
         - "&7A sword wreathed in flames"
       stats:
         strength: 25
         critChance: 15
       abilities:
         - "dragon_fury"
   ```

2. **Create Ability** (if needed):
   ```java
   public class MyAbility extends AbstractItemAbility {
       public MyAbility() {
           super("my_ability", 30); // 30 second cooldown
       }
       
       @Override
       protected void executeAbility(Player player, ItemStack item) {
           // Ability implementation
       }
   }
   ```

### Performance Considerations

- **Use caching**: PlayerStatsService demonstrates proper caching patterns
- **Async operations**: Use `Bukkit.getScheduler().runTaskAsynchronously()` for heavy operations
- **Resource cleanup**: Always implement proper `shutdown()` methods
- **Event handling**: Use appropriate event priorities and avoid blocking operations

### Debugging & Monitoring

1. **Enable Debug Mode**:
   ```yaml
   # config.yml
   debug: true
   ```

2. **Performance Monitoring**:
   ```
   /mmocadmin system performance
   /mmocadmin system performance playerstats
   ```

3. **Log Analysis**:
   - Check `logs/latest.log` for detailed debug information
   - Service initialization order and timing
   - Cache hit/miss ratios

## ⚙️ Configuration

### Main Configuration (config.yml)

```yaml
# Debug mode - enables detailed logging
debug: false

# Player default stats
player_defaults:
  base_stats:
    maxHealth: 20
    maxMana: 100
    strength: 0
    defense: 0
    intelligence: 0
    speed: 0
    critChance: 5
    critDamage: 50
    speedPercent: 0
    miningSpeed: 0
    foragingSpeed: 0
    fishingSpeed: 0
    shootingSpeed: 0

# Plugin messages
messages:
  no_permission: "&cYou don't have permission to use this command."
  command_error: "An error occurred while executing the command."
  player_not_found: "&cPlayer not found."
```

### Configuration Validation

The plugin automatically validates all configuration files and provides helpful error messages:

- **Range Validation**: Stats must be within acceptable ranges
- **Required Fields**: Missing required fields are reported
- **Type Validation**: Ensures proper data types (numbers, strings, etc.)
- **Reference Validation**: Checks that referenced items/mobs exist

### Hot-Reloading

Most configurations support hot-reloading:
```
/mmocadmin system reload config
/mmocadmin system reload items
/mmocadmin system reload all
```

## 🎮 Commands & Permissions

### User Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/mmoc stats` | View your stats | `mmocraft.user.stats` |
| `/mmoc craft` | Open custom crafting GUI | `mmocraft.user.craft` |

### Administrative Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/mmocadmin system reload <config\|all>` | Reload configurations | `mmocraft.admin.system.reload` |
| `/mmocadmin system debug <true\|false>` | Toggle debug mode | `mmocraft.admin.system.debug` |
| `/mmocadmin system performance [service]` | View performance stats | `mmocraft.admin.system.performance` |
| `/mmocadmin item give <player> <item>` | Give custom item | `mmocraft.admin.item.give` |
| `/mmocadmin mob spawn <type> [location]` | Spawn custom mob | `mmocraft.admin.mob.spawn` |

### Permission Hierarchy

```
mmocraft.*                    # All permissions
├── mmocraft.user.*          # All user permissions
│   ├── mmocraft.user.stats
│   └── mmocraft.user.craft
└── mmocraft.admin.*         # All admin permissions
    ├── mmocraft.admin.system.*
    ├── mmocraft.admin.item.*
    ├── mmocraft.admin.mob.*
    └── mmocraft.admin.player.*
```

## 📚 API Reference

### Core Classes

#### MMOCore
Central service container and plugin coordinator.

```java
// Get service instance
PlayerStatsService stats = core.getService(PlayerStatsService.class);

// Register event listener
core.registerListener(new MyListener());

// Register command
core.registerCommand("mycommand", new MyCommand(core));
```

#### PlayerStatsService
Manages player statistics and attribute modifiers.

```java
// Get player's calculated stats
CalculatedPlayerStats stats = service.getCalculatedStats(player);

// Schedule stat recalculation (batched)
service.scheduleStatsUpdate(player);

// Get performance metrics
String performance = service.getPerformanceStats();
```

#### ConfigService
Handles configuration loading, validation, and hot-reloading.

```java
// Get configuration
FileConfiguration config = service.getConfig("items.yml");

// Check if config is managed
boolean managed = service.isManagedConfig("config.yml");

// Reload specific config
service.reloadConfig("items.yml");
```

### Event System

#### Custom Events

- `PlayerStatsCalculatedEvent` - Fired when player stats are recalculated
- `CustomItemCraftEvent` - Fired when custom item is crafted
- `AbilityActivateEvent` - Fired when item ability is used

#### Event Listeners

```java
@EventHandler
public void onStatsCalculated(PlayerStatsCalculatedEvent event) {
    Player player = event.getPlayer();
    CalculatedPlayerStats stats = event.getStats();
    // Handle stats change
}
```

### NBT Integration

The plugin uses Bukkit's PersistentDataContainer for NBT data:

```java
// Custom item identification
NamespacedKey key = new NamespacedKey(plugin, "custom_item");
container.set(key, PersistentDataType.STRING, "flame_sword");

// Stat storage
NamespacedKey statsKey = new NamespacedKey(plugin, "item_stats");
container.set(statsKey, PersistentDataType.STRING, statsJson);
```

## 📊 Performance & Monitoring

### Built-in Metrics

The plugin tracks performance metrics for optimization:

- **PlayerStatsService**: Recalculations, cache hits/misses, cache size
- **ConfigService**: Reload operations, validation time
- **Memory Usage**: Service cleanup, cache management

### Cache Management

- **Automatic Cleanup**: Removes offline players every 10 minutes
- **Size Limits**: Maximum 1000 cached player stats
- **Hit Rate Monitoring**: Tracks cache efficiency

### Performance Commands

```bash
# Overall performance
/mmocadmin system performance

# Service-specific performance
/mmocadmin system performance playerstats

# Debug information
/mmocadmin system debug true
```

### Optimization Tips

1. **Stat Recalculation**: Batched with 2-tick delay to handle rapid equipment changes
2. **Cache Strategy**: LRU-based removal with offline player cleanup
3. **Async Operations**: Heavy calculations moved off main thread
4. **Memory Management**: Automatic cleanup on player disconnect

## 🤝 Contributing

### Code Standards

1. **Logging Standards**:
   - `DEBUG`: Detailed information (only when debug=true)
   - `INFO`: Important events and state changes
   - `WARN`: Recoverable issues and deprecation warnings
   - `SEVERE`: Critical errors and failures

2. **Service Design**:
   - Implement `Service` interface
   - Proper initialization and shutdown
   - Thread-safe operations
   - Comprehensive error handling

3. **Configuration**:
   - All behavior should be configurable
   - Provide sensible defaults
   - Validate configuration input
   - Support hot-reloading where possible

### Development Workflow

1. **Fork Repository**: Create your own fork
2. **Create Feature Branch**: `git checkout -b feature/my-feature`
3. **Write Tests**: Add unit tests for new functionality
4. **Follow Standards**: Use consistent code style and logging
5. **Update Documentation**: Update README and JavaDoc
6. **Submit PR**: Create pull request with detailed description

### Testing

```bash
# Run tests
./gradlew test

# Build plugin
./gradlew shadowJar

# Test on server
cp build/libs/*.jar /path/to/server/plugins/
```

## 🔧 Troubleshooting

### Common Issues

#### Plugin Won't Load
```
[MMOCraft] Critical failure initializing service: ServiceName
```
**Solution**: Check service dependencies and initialization order in MMOCore.

#### Configuration Errors
```
[MMOCraft] Invalid critChance value: 150 (should be 0-100)
```
**Solution**: Check configuration validation messages and fix YAML syntax.

#### Performance Issues
```
[MMOCraft] Stats cache exceeded maximum size. Removed 100 oldest entries.
```
**Solution**: Monitor performance with `/mmocadmin system performance` and adjust cache settings.

### Debug Mode

Enable debug mode for detailed logging:
```yaml
# config.yml
debug: true
```

Or use command:
```
/mmocadmin system debug true
```

### Log Analysis

Check `logs/latest.log` for:
- Service initialization order and timing
- Configuration validation results
- Performance metrics and warnings
- Error stack traces with context

### Getting Help

1. **Check Logs**: Enable debug mode and check console output
2. **Verify Configuration**: Use built-in validation messages
3. **Monitor Performance**: Use admin commands to check system health
4. **Report Issues**: Include configuration files and relevant log sections

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Purpur Team** for the excellent server platform
- **Paper Project** for the enhanced Bukkit API
- **Adventure API** for modern text components
- **Bukkit Community** for extensive documentation and support

---

**MMOCraft Plugin** - Transforming Minecraft into an MMO Experience

For additional support or questions, please check the [Issues](https://github.com/your-repo/MMOCraft-Plugin/issues) section.
