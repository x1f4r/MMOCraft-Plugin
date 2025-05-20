package io.github.x1f4r.mmocraft.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

public class MessageUtils {
    public static void sendPlayerMessage(CommandSender sender, String message, TextColor color) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(Component.text(message, color));
    }
    public static void sendPlayerMessage(CommandSender sender, Component component) {
        if (component == null) return;
        sender.sendMessage(component);
    }
    // Add other common messaging utilities if apparent from AbstractMMOCommand
}
