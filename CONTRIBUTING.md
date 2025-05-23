# Contributing to MMOCraft Plugin

Thank you for your interest in contributing to MMOCraft! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites
- Java 21 or higher
- Git
- Basic understanding of Bukkit/Spigot plugin development
- Familiarity with Gradle build system

### Development Setup
1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/MMOCraft-Plugin.git`
3. Import the project into your IDE as a Gradle project
4. Set up a test server with Purpur 1.21.5-R0.1

## 📋 Code Standards

### Logging Standards
Follow the established logging patterns defined in `LoggingService`:
- **DEBUG**: Detailed information for development/troubleshooting (only when debug=true)
- **INFO**: General information about plugin operations and state changes  
- **WARN**: Recoverable issues that may indicate problems but don't stop functionality
- **SEVERE**: Critical errors that may cause plugin malfunction or data loss

```java
// Good examples
logging.debug("Calculated stats for " + player.getName() + ": " + stats.toString());
logging.info("PlayerStatsService initialized with " + statsCache.size() + " cached entries");
logging.warn("Player " + player.getName() + " has invalid stat value, using default");
logging.severe("Failed to save player data for " + player.getName(), exception);
```

### Service Development
When creating new services:

1. **Implement Service Interface**:
   ```java
   public class MyService implements Service {
       @Override
       public void initialize(MMOCore core) { /* Required */ }
       
       @Override
       public String getServiceName() { /* Required */ }
       
       @Override
       public void shutdown() { /* Required - cleanup resources */ }
   }
   ```

2. **Register in MMOCore**:
   ```java
   registerService(new MyService(this), MyService.class);
   ```

3. **Follow Dependency Order**: Services that depend on others should be registered after their dependencies

### Configuration Standards
- All behavior should be configurable via YAML files
- Provide sensible defaults in code
- Add validation for all configuration values
- Support hot-reloading where possible
- Use `MMOConstants` for hardcoded values

### Error Handling
- Use specific exception types where possible
- Always log exceptions with context
- Implement graceful fallbacks for non-critical failures
- Use try-catch-finally blocks for resource management

### Performance Considerations
- Implement caching for expensive operations
- Use async tasks for heavy calculations
- Add performance monitoring for new systems
- Clean up resources properly in shutdown methods

## 🔧 Development Workflow

### 1. Create Feature Branch
```bash
git checkout -b feature/my-awesome-feature
```

### 2. Make Changes
- Write clean, documented code
- Follow existing patterns and conventions
- Add configuration options as needed
- Include error handling and logging

### 3. Add Tests
While we don't have extensive unit tests yet, please consider:
- Testing configuration validation
- Testing service initialization/shutdown
- Testing core functionality manually

### 4. Update Documentation
- Update README.md if adding new features
- Add JavaDoc comments for public methods
- Document configuration options
- Include examples in code comments

### 5. Test Thoroughly
- Test with debug mode enabled
- Verify configuration validation works
- Check performance impact with monitoring commands
- Test hot-reloading functionality

### 6. Submit Pull Request
- Write a clear title and description
- Reference any related issues
- Include screenshots/examples if applicable
- Be responsive to feedback

## 🎯 Areas for Contribution

### High Priority
- **Unit Tests**: The project needs comprehensive test coverage
- **Database Integration**: Add optional database storage for player data
- **Web API**: REST API for external tools and statistics
- **GUI Improvements**: Enhanced player interfaces and admin tools

### Medium Priority
- **New Abilities**: Creative item abilities and effects
- **Mob AI Behaviors**: More sophisticated AI patterns
- **Economy Integration**: Vault integration for item trading
- **Performance Optimizations**: Query optimization and caching improvements

### Low Priority
- **Localization**: Multi-language support
- **Integration Plugins**: Support for other popular plugins
- **Metrics Collection**: Optional analytics and usage statistics
- **Documentation**: Video tutorials and examples

## 🐛 Bug Reports

When reporting bugs, please include:
- **Server Version**: Purpur/Paper version
- **Plugin Version**: MMOCraft version
- **Configuration**: Relevant config files
- **Error Logs**: Complete stack traces
- **Steps to Reproduce**: Detailed reproduction steps
- **Expected vs Actual**: What you expected vs what happened

## 💡 Feature Requests

For new features, please provide:
- **Use Case**: Why this feature is needed
- **Implementation Ideas**: How you envision it working
- **Configuration**: What settings should be configurable
- **Performance Impact**: Consider performance implications
- **Backwards Compatibility**: How it affects existing setups

## 📚 Resources

### Documentation
- [Bukkit API Documentation](https://hub.spigotmc.org/javadocs/bukkit/)
- [Paper API Documentation](https://papermc.io/javadocs/)
- [Adventure API Documentation](https://docs.advntr.dev/)

### Tools
- [NBT Explorer](https://www.planetminecraft.com/mod/nbtexplorer/) - For debugging NBT data
- [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) - For packet manipulation
- [WorldEdit](https://enginehub.org/worldedit/) - For testing in development

### Community
- [Spigot Forums](https://www.spigotmc.org/forums/) - General plugin development
- [Paper Discord](https://discord.gg/papermc) - Technical discussions
- [Bukkit Discord](https://discord.gg/bukkit) - Community support

## 🔍 Code Review Process

All contributions go through code review:

1. **Automated Checks**: Code style and basic validation
2. **Functionality Review**: Does it work as intended?
3. **Architecture Review**: Does it fit the plugin's design?
4. **Performance Review**: Any performance implications?
5. **Documentation Review**: Is it properly documented?

## 📄 License

By contributing to MMOCraft Plugin, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to MMOCraft! Together we can make Minecraft more MMO-like and enjoyable for everyone. 🎮