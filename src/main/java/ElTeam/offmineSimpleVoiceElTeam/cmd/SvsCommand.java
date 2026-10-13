package ElTeam.offmineSimpleVoiceElTeam.cmd;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;
import ElTeam.offmineSimpleVoiceElTeam.placed.PlacedSound;
import ElTeam.offmineSimpleVoiceElTeam.ui.SoundAdminMenu;
import ElTeam.offmineSimpleVoiceElTeam.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/** Optional commands. The main integration path is SoundApi. */
public final class SvsCommand implements CommandExecutor, TabCompleter {
    private final OffmineSimpleVoiceElTeam plugin;
    public SvsCommand(OffmineSimpleVoiceElTeam plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(OffmineSimpleVoiceElTeam.PERM)) { Text.err(sender, "Нет прав."); return true; }
        if (command.getName().equalsIgnoreCase("soundadmin")) { open(sender); return true; }
        if (args.length == 0) { help(sender); return true; }
        SoundApi api = plugin.sounds();
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help" -> help(sender);
                case "admin", "menu" -> open(sender);
                case "reload" -> { plugin.library().reload(); Text.msg(sender, "Звуков в библиотеке: " + api.listSounds().size()); }
                case "sounds" -> Text.msg(sender, "Звуки: " + String.join(", ", api.listSounds()));
                case "code" -> {
                    if (!(sender instanceof Player p)) { Text.err(sender, "Копирование доступно только игрокам."); break; }
                    if (args.length < 2) { Text.err(sender, "/osv code <id>"); break; }
                    if (!api.exists(args[1])) throw new IllegalArgumentException("Нет звука с ID " + args[1]);
                    SoundAdminMenu.sendCode(p, args[1]);
                }
                case "download" -> {
                    if (args.length < 3) { Text.err(sender, "/osv download <id> <прямая-ссылка>"); break; }
                    plugin.menu().download(sender, args[1].toLowerCase(Locale.ROOT), args[2]);
                }
                case "play" -> {
                    if (args.length < 3) { Text.err(sender, "/osv play <ник> <id> [loop]"); break; }
                    Player p = Bukkit.getPlayerExact(args[1]);
                    if (p == null) throw new IllegalArgumentException("Игрок не найден: " + args[1]);
                    api.play(p, args[2], booleanArg(args, 3));
                    Text.msg(sender, "Звук " + args[2] + " запущен для " + p.getName());
                }
                case "playall" -> {
                    if (args.length < 2) { Text.err(sender, "/osv playall <id> [loop]"); break; }
                    api.playAll(args[1], booleanArg(args, 2));
                    Text.msg(sender, "Звук " + args[1] + " запущен для всех игроков");
                }
                case "at" -> {
                    if (args.length < 6) { Text.err(sender, "/osv at <x> <y> <z> <радиус> <id> [loop]"); break; }
                    Location loc = parseLocation(sender, args, 1);
                    UUID handle = api.playAt(loc, args[5], Float.parseFloat(args[4]), booleanArg(args, 6));
                    Text.msg(sender, "Воспроизведение в мире (временный ID): " + handle);
                }
                case "place" -> {
                    if (args.length < 7) { Text.err(sender, "/osv place <x> <y> <z> <радиус> <beaconId> <soundId> [loop]"); break; }
                    Location loc = parseLocation(sender, args, 1);
                    api.place(args[5], loc, Float.parseFloat(args[4]), args[6], booleanArg(args, 7));
                    Text.msg(sender, "Постоянный звуковой маяк создан: " + args[5]);
                }
                case "queueadd" -> {
                    if (args.length < 3) { Text.err(sender, "/osv queueadd <beaconId> <soundId>"); break; }
                    api.enqueue(args[1], args[2]);
                    Text.msg(sender, "Звук добавлен в очередь маяка");
                }
                case "list" -> {
                    var beacons = plugin.beacons().registry().all();
                    if (beacons.isEmpty()) Text.msg(sender, "Постоянных звуковых маяков нет");
                    else for (PlacedSound ps : beacons) Text.msg(sender, ps.id() + " @ "
                            + ps.location().getWorld().getName() + " " + ps.location().getBlockX()
                            + " " + ps.location().getBlockY() + " " + ps.location().getBlockZ()
                            + " R=" + ps.radius() + " files=" + ps.fileList());
                }
                case "stop" -> {
                    if (args.length < 2) { Text.err(sender, "/osv stop <beaconId|all>"); break; }
                    if (args[1].equalsIgnoreCase("all")) {
                        plugin.beacons().registry().stopAll();
                        plugin.beacons().saveToConfig();
                        api.stopAllAt();
                        Text.msg(sender, "Все локационные звуки остановлены");
                    } else {
                        boolean ok = api.removeBeacon(args[1]);
                        if (!ok) throw new IllegalArgumentException("Не найден маяк: " + args[1]);
                        Text.msg(sender, "Маяк удалён: " + args[1]);
                    }
                }
                case "stopat" -> {
                    if (args.length < 2) { Text.err(sender, "/osv stopat <UUID>"); break; }
                    boolean stopped = api.stopAt(UUID.fromString(args[1]));
                    Text.msg(sender, stopped ? "Позиционный звук остановлен" : "Звук не найден");
                }
                case "stopplayer" -> {
                    if (args.length < 2) { Text.err(sender, "/osv stopplayer <ник|all>"); break; }
                    if (args[1].equalsIgnoreCase("all")) api.stopAllPlayers();
                    else {
                        Player p = Bukkit.getPlayerExact(args[1]);
                        if (p == null) throw new IllegalArgumentException("Игрок не найден: " + args[1]);
                        api.stopPlayer(p);
                    }
                    Text.msg(sender, "Звук(и) остановлены");
                }
                case "stopglobal" -> { api.stopGlobal(); Text.msg(sender, "Все глобальные звуки остановлены"); }
                default -> help(sender);
            }
        } catch (Exception e) {
            Text.err(sender, "Ошибка: " + e.getMessage());
            plugin.getLogger().fine("Команда завершилась ошибкой: " + e);
        }
        return true;
    }

    private static boolean booleanArg(String[] args, int index) { return args.length > index && Boolean.parseBoolean(args[index]); }
    private static Location parseLocation(CommandSender sender, String[] args, int start) {
        World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("На сервере нет миров"));
        return new Location(world, Double.parseDouble(args[start]), Double.parseDouble(args[start + 1]),
                Double.parseDouble(args[start + 2]));
    }
    private void open(CommandSender sender) {
        if (!(sender instanceof Player player)) { Text.err(sender, "Админ-панель доступна только в игре"); return; }
        plugin.menu().open(player, 0);
    }
    private static void help(CommandSender sender) {
        Text.msg(sender, "/soundadmin или /osv admin — панель звуков");
        Text.msg(sender, "/osv download <id> <url> — скачать звук по ссылке");
        Text.msg(sender, "/osv sounds / reload / code <id>");
        Text.msg(sender, "/osv play <ник> <id> [loop:true|false]");
        Text.msg(sender, "/osv playall <id> [loop:true|false]");
        Text.msg(sender, "/osv at <x> <y> <z> <радиус> <id> [loop]");
        Text.msg(sender, "/osv place <x> <y> <z> <радиус> <маяк> <id> [loop]");
        Text.msg(sender, "/osv queueadd <маяк> <id>, /osv list, /osv stop <маяк|all>");
        Text.msg(sender, "/osv stopat <UUID>, /osv stopplayer <ник|all>, /osv stopglobal");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(OffmineSimpleVoiceElTeam.PERM)) return List.of();
        if (cmd.getName().equalsIgnoreCase("soundadmin")) return List.of();
        if (args.length == 1) return matching(List.of("help", "admin", "menu", "reload", "sounds", "code", "download",
                "play", "playall", "at", "place", "queueadd", "list", "stop", "stopat", "stopplayer", "stopglobal"), args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("play") && args.length == 2 || sub.equals("stopplayer") && args.length == 2) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toCollection(ArrayList::new));
            if (sub.equals("stopplayer")) names.add("all");
            return matching(names, args[args.length - 1]);
        }
        if ((sub.equals("play") && args.length == 3) || (sub.equals("playall") && args.length == 2)
                || (sub.equals("code") && args.length == 2) || (sub.equals("at") && args.length == 6)
                || (sub.equals("place") && args.length == 7) || (sub.equals("queueadd") && args.length == 3))
            return matching(plugin.library().ids(), args[args.length - 1]);
        if (sub.equals("stop") && args.length == 2 || sub.equals("queueadd") && args.length == 2) {
            List<String> names = plugin.beacons().registry().all().stream().map(PlacedSound::id).collect(Collectors.toCollection(ArrayList::new));
            if (sub.equals("stop")) names.add("all");
            return matching(names, args[args.length - 1]);
        }
        if ((sub.equals("play") && args.length == 4) || (sub.equals("playall") && args.length == 3)
                || (sub.equals("at") && args.length == 7) || (sub.equals("place") && args.length == 8))
            return matching(List.of("true", "false"), args[args.length - 1]);
        return List.of();
    }

    private static List<String> matching(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
