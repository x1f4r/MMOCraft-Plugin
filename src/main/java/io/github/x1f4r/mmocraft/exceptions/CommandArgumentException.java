package io.github.x1f4r.mmocraft.exceptions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CommandArgumentException extends Exception {
    private final String argumentName;
    public CommandArgumentException(String message, String argumentName) {
        super(message);
        this.argumentName = argumentName;
    }
    public String getArgumentName() {
        return argumentName;
    }
    public Component getMessageComponent() {
        return Component.text(getMessage(), NamedTextColor.RED);
    }
}
