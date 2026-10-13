package ElTeam.offmineSimpleVoiceElTeam.listener;

import ElTeam.offmineSimpleVoiceElTeam.playback.GlobalPlaybackManager;
import ElTeam.offmineSimpleVoiceElTeam.playback.PersonalPlaybackManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import java.util.Locale;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ServerEvents implements Listener {

    /** Extra command-tree filter: unauthorized clients don't see /osv or /soundadmin
     * (including /offminesoundapi:osv). Execution is still permission-checked.
     */
    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission(OffmineSimpleVoiceElTeam.PERM)) return;
        event.getCommands().removeIf(ServerEvents::isAdminCommand);
    }

    public static boolean isAdminCommand(String command) {
        if (command == null) return false;
        String name = command.toLowerCase(Locale.ROOT);
        int colon = name.lastIndexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        return name.equals("osv") || name.equals("soundadmin");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PersonalPlaybackManager.get().onJoin(e.getPlayer());
        GlobalPlaybackManager.get().attachPlayerToAllSessions(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PersonalPlaybackManager.get().onQuit(e.getPlayer());
    }
}
