package io.github.x1f4r.mmocraft.exceptions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerNotFoundException extends CommandArgumentException {
    public PlayerNotFoundException(String playerName) {
        super("Player '" + playerName + "' not found.", "playerName");
    }
    // You might want a more specific message component if needed
}
