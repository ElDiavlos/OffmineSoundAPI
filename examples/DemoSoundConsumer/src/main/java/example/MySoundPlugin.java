package example;

import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MySoundPlugin extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только в игре");
            return true;
        }
        SoundApi api = getServer().getServicesManager().load(SoundApi.class);
        if (api == null || !api.isReady()) {
            player.sendMessage("Simple Voice Chat ещё не готов");
            return true;
        }
        if (!api.exists("alarm")) {
            player.sendMessage("Добавьте звук с ID 'alarm' через /soundadmin");
            return true;
        }
        try {
            api.play(player, "alarm");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            player.sendMessage("Ошибка звука: " + ex.getMessage());
        }
        return true;
    }
}
