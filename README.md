# MMOCraft Plugin

MMOCraft is a Minecraft (Spigot) plugin designed to add various MMORPG-like features to the game. It includes systems for custom items, player and mob statistics, custom crafting, item abilities, and custom mob behaviors.

## Installation

1.  **Download**: Obtain the latest `MMOCraft.jar` file from the releases page (or build it from source).
2.  **Server Setup**: Ensure you have a Spigot-compatible Minecraft server (e.g., Spigot, Paper) version 1.20 or higher. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/plugin.yml]
3.  **Add Plugin**: Place the `MMOCraft.jar` file into your server's `plugins` directory.
4.  **First Run & Configuration**:
    * Start your server once. This will generate the default configuration files in the `plugins/MMOCraft/` directory:
        * `config.yml` (general settings)
        * `items.yml` (custom item definitions)
        * `mobs.yml` (custom mob stat overrides)
        * `recipes.yml` (custom crafting recipes)
    * Stop the server.
    * Edit these YAML files to customize the plugin to your liking.
    * Restart the server to apply your changes.

## Features

### Core Systems
* **Player Stats System**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/player/PlayerStatsManager.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/stats/PlayerStats.java]
    * Manages player attributes: Strength, Defense, Crit Chance, Crit Damage, Max Mana, Current Mana, Speed, Mining Speed, Foraging Speed, Fishing Speed, and Shooting Speed.
    * Stats are derived from base values and bonuses from equipped items.
    * Includes configurable health and mana regeneration mechanics. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/config.yml]
    * Displays player health and mana on the action bar.
* **Entity Stats System**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/stats/EntityStatsManager.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/mobs.yml]
    * Allows overriding base stats (Max Health, Defense, Strength, Speed, etc.) for vanilla mob types via `mobs.yml`.
* **Custom Item Management**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/items/ItemManager.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/items.yml]
    * Define custom items with unique names, lore, materials, enchantments, attributes (vanilla), and custom MMOCraft stats.
    * Supports item flags (e.g., `HIDE_ENCHANTS`, `HIDE_ATTRIBUTES`).
    * Items are configured in `items.yml`.
* **Custom Crafting**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/crafting/RecipeManager.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/crafting/CraftingGUIListener.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/recipes.yml]
    * Introduces a custom crafting GUI accessible via `/customcraft`.
    * Supports shaped recipes defined in `recipes.yml`.
    * Ingredients can be vanilla materials, material tags (e.g., `#LOGS`), or other custom items.
* **Combat Mechanics**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/combat/PlayerDamageListener.java]
    * Damage calculations incorporate attacker's Strength, Crit Chance, and Crit Damage, and victim's Defense.
    * Supports "True Damage" (ignores defense) if flagged on items or projectiles.
    * Visual feedback via floating damage indicators and critical hit particles. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/display/DamageAndHealthDisplayManager.java]
* **Item Abilities**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/items/PlayerAbilityListener.java]
    * Allows custom items to have right-click activated abilities (e.g., "Instant Transmission" for Aspect of the End, "Dragon's Fury" for Aspect of the Dragons).
    * Abilities can have mana costs and cooldowns.
    * Special "Instant Shot" mechanic for bows (e.g., Tree Bow), firing on right-click.
* **Armor Sets**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/items/ArmorSetListener.java]
    * Provides a framework for armor set bonuses.
    * Example: Tree Armor set has a sneak-triggered ability.
    * Example: Ender Armor pieces have their Defense and Health bonuses amplified when the player is in The End. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/items.yml, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/player/PlayerStatsManager.java]
* **Tool Proficiency System**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/listeners/PlayerToolListener.java]
    * `Mining Speed` and `Foraging Speed` stats affect block breaking times for relevant tools (Pickaxes, Axes, Shovels on appropriate materials). Requires Paper API for optimal block damage events.
    * `Fishing Speed` stat reduces the wait time for catching fish (requires Paper API for `FishHook` wait time modification).
    * `Shooting Speed` stat influences projectile velocity for drawn custom bows and cooldown for instant-shot bows. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/listeners/BowListener.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/items/PlayerAbilityListener.java]
* **Custom Mob AI & Drops**:
    * **Elder Dragon**: A summonable boss (`/summonelderdragon`) with custom AI routines including Fireball Volley, Lightning Strike, and Player Charge. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/entities/ai/ElderDragonAI.java, x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/commands/SummonElderDragonCommand.java]
    * Custom drops can be configured, e.g., Elder Dragon drops "Aspect of the Dragons". [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/entities/MobDropListener.java]
* **Visual Indicators**: [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/display/DamageAndHealthDisplayManager.java]
    * Floating damage numbers appear when entities are damaged.
    * Health bars are displayed above non-player living entities, showing current/max health.
* **Utility**:
    * Disables vanilla hunger/saturation decay. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/listeners/PlayerSaturationListener.java]
    * NBT Tagging System for identifying custom items and mobs. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/utils/NBTKeys.java]

### Administrative
* **Commands**: Provides commands for players and administrators (see "Commands" section below).
* **Configuration**: Most features are configurable through YAML files (`config.yml`, `items.yml`, `mobs.yml`, `recipes.yml`).

## Configuration Files

* `config.yml`: Main plugin configuration, including player health/mana regeneration rates. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/config.yml]
* `items.yml`: Defines all custom items, their stats, abilities, lore, etc. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/items.yml]
* `mobs.yml`: Configures stat overrides for vanilla mob types (e.g., making Zombies stronger). [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/mobs.yml]
* `recipes.yml`: Defines custom shaped crafting recipes for the custom crafting GUI. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/recipes.yml]

## Commands

[cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/plugin.yml]

* `/customcraft` (Aliases: `mcraft`, `mmoc`): Opens the custom MMO crafting GUI.
* `/givecustomitem <item_id> [amount] [player]` (Aliases: `mmogive`, `mcgive`): Gives a specified custom item.
* `/stats` (Aliases: `mystats`, `mmostats`): Displays the player's current MMOCraft stats.
* `/summonelderdragon`: Summons a custom Elder Dragon.
* `/reloadmobs`: Reloads the `mobs.yml` configuration.
* `/mmoadmin stat <health|mana> <current|max> <set|add|remove> <player> <amount>`: Admin command to modify player health or mana. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/commands/AdminStatsCommand.java]

## Permissions

[cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/resources/plugin.yml]

* `mmocraft.command.craft`: Allows use of `/customcraft`. (Default: true)
* `mmocraft.command.givecustomitem`: Allows use of `/givecustomitem`. (Default: op)
* `mmocraft.command.stats`: Allows use of `/stats`. (Default: true)
* `mmocraft.command.summon.elderdragon`: Allows use of `/summonelderdragon`. (Default: op)
* `mmocraft.command.reloadmobs`: Allows use of `/reloadmobs`. (Default: op)
* `mmocraft.command.adminstats`: Allows use of `/mmoadmin`. (Default: op)

## Current Limitations / Known Issues

* **Paper API Dependencies**: Some features, like precise custom block breaking speeds and fishing speed modifications, rely on Paper API methods. These may not function as intended on Spigot-only servers. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/listeners/PlayerToolListener.java]
* **Shapeless Recipes**: Custom shapeless recipes are not currently implemented in the custom crafting system. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/crafting/models/CustomRecipe.java]
* **Configuration Reloading**: While `/reloadmobs` reloads mob stats from `mobs.yml`, these changes typically only apply to newly spawned mobs. A full reload of item or recipe configurations might require a server restart or a more comprehensive reload command (not yet implemented).
* **Vanilla Crafting Table Override**: The custom crafting GUI currently overrides the vanilla crafting table interaction. An option to disable this override is not yet available. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/src/main/java/io/github/x1f4r/mmocraft/crafting/CraftingGUIListener.java]
* **Performance**: With a large number of custom entities or frequent stat updates, performance implications should be monitored on heavily populated servers.
* **Stat Persistence**: Player base stats (modified by `/mmoadmin`) are currently in-memory. They would reset on server restart unless a persistence layer (e.g., database, player data files) is added.

## For Developers: UUID Management

The project includes a PowerShell script (`AutoUUIDReplace.ps1`) located in `src/main/java/io/github/x1f4r/mmocraft/utils/` to aid in managing unique UUIDs for `AttributeModifier` instances and other unique identifiers within the Java source code. [cite: x1f4r/MMOCraft-Plugin/MMOCraft-Plugin-3ee092c2a6669604673dce0e4723a15dc1fd2271/README.md]

* **Purpose**: Automates the replacement of placeholder UUIDs with newly generated, unique UUIDs to prevent conflicts.
* **Usage**:
    1.  **Backup your project.**
    2.  Open PowerShell in the project's root directory.
    3.  Run the script: `.\src\main\java\io\github\x1f4r\mmocraft\utils\AutoUUIDReplace.ps1`
    4.  Confirm the backup prompt.
    5.  Review the changes and rebuild the project.
* **Placeholder Requirement**: For the script to identify and replace UUIDs, they must be defined in the code using the specific prefix: `UUID.fromString("YOUR_UNIQUE_UUID_HERE_...")`. Any suffix can be added after `YOUR_UNIQUE_UUID_HERE` for context.

This revised README should be more suitable for a GitHub repository, providing essential information for users and potential contributors.
