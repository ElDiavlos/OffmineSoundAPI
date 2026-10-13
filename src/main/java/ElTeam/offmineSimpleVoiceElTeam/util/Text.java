package ElTeam.offmineSimpleVoiceElTeam.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Soft cream / peach / sage messages; player-provided text is never interpreted as MiniMessage markup. */
public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Component PREFIX = MINI.deserialize("<#EFC4AB><bold>✿ Offmine</bold> <#C7A698>• ");
    private Text() {}

    public static void msg(CommandSender receiver, String content) {
        receiver.sendMessage(PREFIX.append(Component.text(content, TextColor.color(0xF0D9C5))));
    }

    public static void err(CommandSender receiver, String content) {
        receiver.sendMessage(PREFIX.append(Component.text(content, TextColor.color(0xE5A7A0))));
    }
}
