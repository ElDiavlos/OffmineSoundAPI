package ElTeam.offmineSimpleVoiceElTeam.ui;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import ElTeam.offmineSimpleVoiceElTeam.library.SoundDownloader;
import ElTeam.offmineSimpleVoiceElTeam.library.SoundLibrary;
import ElTeam.offmineSimpleVoiceElTeam.util.Text;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Paper 26.3 native dialog UI: no inventory GUI and no chat input. */
public final class SoundAdminMenu implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int PAGE_SIZE = 8;
    private static final String NAMESPACE = "offminesoundapi";
    private final OffmineSimpleVoiceElTeam plugin;
    private final SoundDownloader downloader;
    private final Set<String> downloadingIds = ConcurrentHashMap.newKeySet();

    public SoundAdminMenu(OffmineSimpleVoiceElTeam plugin) {
        this.plugin = plugin;
        this.downloader = new SoundDownloader(plugin);
    }

    private static Component ui(String mini) {
        return MINI.deserialize(mini);
    }

    private static Component ui(String mini, String key, String value) {
        return MINI.deserialize(mini, Placeholder.unparsed(key, value));
    }

    private static ActionButton button(String title, String tooltip, String action) {
        return ActionButton.create(ui(title), ui(tooltip), 170,
                DialogAction.customClick(Key.key(NAMESPACE, action), null));
    }

    private static ActionButton copyButton(String title, String snippet) {
        return ActionButton.create(ui(title),
                ui("<#AB8174>Нажми, чтобы скопировать Java-код"), 170,
                DialogAction.staticAction(ClickEvent.copyToClipboard(snippet)));
    }

    private static DialogBase.Builder base(String title, List<DialogBody> body) {
        return DialogBase.builder(ui(title))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body);
    }

    public void open(Player p, int requestedPage) {
        if (!p.hasPermission(OffmineSimpleVoiceElTeam.PERM)) return;
        plugin.library().reload();
        List<String> ids = plugin.library().ids();
        int last = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(last, requestedPage));
        // In vanilla dialogs, ActionButtons always appear *below* the entire body.
        // Render the selectable sounds as clickable body text so that the order is:
        // heading -> counters -> sounds -> hint -> footer controls.
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(ui("<#DAB19B>✦  <#FFF0DB>Твоя библиотека звуков  <#DAB19B>✦")));
        body.add(DialogBody.plainMessage(ui("<#B89181>Звуков: <#F3CCAD>" + ids.size()
                + "   <#B89181>•   Страница <#F3CCAD>" + (page + 1) + "/" + (last + 1))));

        if (ids.isEmpty()) {
            body.add(DialogBody.plainMessage(ui("<#D5B09C>♫  Библиотека пока пуста"), 300));
        } else {
            for (int i = page * PAGE_SIZE; i < Math.min(ids.size(), (page + 1) * PAGE_SIZE); i++) {
                String id = ids.get(i);
                String filename = plugin.library().fileName(id).orElse("неизвестный файл");
                Component entry = ui("<#F4D4B5>♫  <#FFEADC><id>  <#DAB19B>›", "id", id)
                        .clickEvent(ClickEvent.custom(
                                Key.key(NAMESPACE, "sound/" + id),
                                BinaryTagHolder.binaryTagHolder("{}")))
                        .hoverEvent(HoverEvent.showText(ui("<#B78B79>Нажми, чтобы открыть звук\n<#F8D7C6><name>", "name", filename)));
                body.add(DialogBody.plainMessage(entry, 300));
            }
        }
        body.add(DialogBody.plainMessage(ui("<#CBB1A2>Нажми на название звука, чтобы прослушать его или скопировать код для Java API."), 320));

        // Footer holds only library management / pagination buttons.
        List<ActionButton> actions = new ArrayList<>();
        if (page > 0) actions.add(button("<#EECFB7>❮ Назад", "<#C5A397>Предыдущая страница", "page/" + (page - 1)));
        if (page < last) actions.add(button("<#EECFB7>Далее ❯", "<#C5A397>Следующая страница", "page/" + (page + 1)));
        actions.add(button("<#C7D5B7>＋ Загрузить звук", "<#B0BEA1>Ввести ID и прямую ссылку", "upload"));
        actions.add(button("<#EDD1B6>↻ Обновить", "<#C5A397>Перечитать библиотеку", "page/" + page));
        actions.add(button("<#E5B7B7>♡ Помощь", "<#C5A397>Форматы, ссылки и команды", "help"));
        p.showDialog(Dialog.create(builder -> builder.empty()
                .base(base("<#F6CFB6>✿ <#FFE8D5>Offmine <#E3AFAC>• <#FFE8D5>Sound Library", body).build())
                .type(DialogType.multiAction(actions, ActionButton.create(ui("<#C7B1A5>Закрыть"), null, 170, null), 2))));
    }

    private void openUpload(Player p, String idDefault, String urlDefault) {
        p.showDialog(Dialog.create(builder -> builder.empty()
                .base(base("<#F7D6BC>✿ <#FFEADC>Добавить звук", List.of(
                        DialogBody.plainMessage(ui("<#E4BEA5>Вставь прямую ссылку, GitHub Blob или общедоступную ссылку Dropbox.")),
                        DialogBody.plainMessage(ui("<#C6A89C>MP3, WAV, OGG, FLAC и другие форматы. Для MP3/OGG нужен FFmpeg.")),
                        DialogBody.plainMessage(ui("<#D8ADA4>Не работает Catbox? Используй GitHub Raw или загрузи файл в папку sounds/ вручную."))
                )).inputs(List.of(
                        DialogInput.text("sound_id", ui("<#F2CDB5>ID звука <#B89989>(a-z, 0-9, _ и -)"))
                                .initial(idDefault).maxLength(48).width(320).build(),
                        DialogInput.text("sound_url", ui("<#F2CDB5>Прямая ссылка на аудиофайл"))
                                .initial(urlDefault).maxLength(2048).width(320).build()
                )).build())
                .type(DialogType.confirmation(
                        button("<#B8D0B5>✓ Скачать", "<#B8D0B5>Проверить ссылку и начать загрузку", "submit"),
                        button("<#E5BBB1>↩ Библиотека", "<#C5A397>Вернуться в список звуков", "page/0")
                ))));
    }

    private void openSound(Player p, String id) {
        if (!plugin.library().contains(id)) {
            Text.err(p, "Звук «" + id + "» не найден. Обновите библиотеку.");
            open(p, 0);
            return;
        }
        String name = plugin.library().fileName(id).orElse("неизвестный файл");
        List<ActionButton> actions = List.of(
                button("<#C8DBBB>♫ Прослушать", "<#B8C7A8>Воспроизвести звук себе", "preview/" + id),
                copyButton("<#F1D2B9>⧉ Игроку", "SoundApi.get().play(player, \"" + id + "\");"),
                copyButton("<#F1D2B9>⧉ Всем", "SoundApi.get().playAll(\"" + id + "\");"),
                copyButton("<#F1D2B9>⧉ В точке", "SoundApi.get().playAt(location, \"" + id + "\", 24f);"),
                copyButton("<#E5C4C0>⧉ Импорт", "import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;"),
                button("<#E9CAB7>❮ К библиотеке", "<#C5A397>Вернуться к списку", "page/0")
        );
        p.showDialog(Dialog.create(builder -> builder.empty()
                .base(base("<#EFD0B9>♫  <#FFE7D1>" + id, List.of(
                        DialogBody.plainMessage(ui("<#F4CEB2>Звук <#FFE7D1><id>", "id", id)),
                        DialogBody.plainMessage(ui("<#C7A898>Файл: <#E6C8AD><filename>", "filename", name)),
                        DialogBody.plainMessage(ui("<#C9AC9B>Нажми <#C9DCB9>⧉<#C9AC9B>, чтобы скопировать код. В чужом плагине укажи зависимость <#FFE0B9>OffmineSoundAPI<#C9AC9B>."))
                )).build())
                .type(DialogType.multiAction(actions, ActionButton.create(ui("<#C7B1A5>Закрыть"), null, 170, null), 2))));
    }

    private void openHelp(Player p) {
        p.showDialog(Dialog.create(builder -> builder.empty()
                .base(base("<#EECFB8>♡ <#FFE7D1>Справка", List.of(
                        DialogBody.plainMessage(ui("<#F6D6B6>✦ Скачивание <#C6A799>— прямая HTTP(S)-ссылка или GitHub Blob / Dropbox.")),
                        DialogBody.plainMessage(ui("<#C6A799>Catbox не нужен: GitHub Raw, Dropbox с общим доступом или файл в папке sounds/.")),
                        DialogBody.plainMessage(ui("<#F6D6B6>✦ Громкость <#C6A799>— V → Настройка громкости → Звуки сервера в Simple Voice Chat.")),
                        DialogBody.plainMessage(ui("<#F6D6B6>✦ HTTP 404 <#C6A799>— файл по этой ссылке не найден: проверь адрес и доступность в браузере.")),
                        DialogBody.plainMessage(ui("<#F6D6B6>✦ Форматы <#C6A799>— WAV; MP3 / OGG / FLAC через FFmpeg.")),
                        DialogBody.plainMessage(ui("<#F6D6B6>✦ Команды <#C6A799>— /soundadmin, /osv download, /osv code, /osv help."))
                )).build())
                .type(DialogType.notice(button("<#EBD0B7>❮ В библиотеку", "<#C7A798>Назад", "page/0")))));
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        Key key = event.getIdentifier();
        if (!NAMESPACE.equals(key.namespace())) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection conn)) return;
        Player p = conn.getPlayer();
        if (!p.hasPermission(OffmineSimpleVoiceElTeam.PERM)) return;
        String action = key.value();
        DialogResponseView response = event.getDialogResponseView();
        // Handle every player/dialog operation on the server thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !p.hasPermission(OffmineSimpleVoiceElTeam.PERM)) return;
            if (action.equals("upload")) {
                openUpload(p, "", "");
            } else if (action.equals("help")) {
                openHelp(p);
            } else if (action.equals("submit")) {
                if (response == null) return;
                String id = response.getText("sound_id");
                String url = response.getText("sound_url");
                if (url == null || url.isBlank()) {
                    Text.err(p, "Вставь прямую ссылку на аудиофайл.");
                    openUpload(p, id == null ? "" : id, "");
                    return;
                }
                id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
                url = url.trim();
                if (id.isBlank()) id = guessId(url);
                if (!SoundLibrary.validId(id)) {
                    Text.err(p, "ID должен содержать 1–48 символов: латиница a-z, цифры, _ или -.");
                    openUpload(p, id, url);
                    return;
                }
                download(p, id, url);
            } else if (action.startsWith("page/")) {
                try { open(p, Integer.parseInt(action.substring(5))); }
                catch (NumberFormatException ignored) { open(p, 0); }
            } else if (action.startsWith("sound/")) {
                openSound(p, action.substring(6));
            } else if (action.startsWith("preview/")) {
                String id = action.substring(8);
                try {
                    plugin.sounds().play(p, id);
                    Text.msg(p, "Воспроизвожу: " + id);
                } catch (Exception e) { Text.err(p, "Не удалось воспроизвести: " + e.getMessage()); }
                openSound(p, id);
            }
        });
    }

    public void download(CommandSender requester, String id, String url) {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        url = url == null ? "" : url.trim();
        if (!SoundLibrary.validId(id)) {
            Text.err(requester, "ID: только a-z, 0-9, _ и - (1–48 символов).");
            return;
        }
        if (plugin.library().contains(id)) {
            Text.err(requester, "ID уже используется: " + id);
            return;
        }
        if (!downloadingIds.add(id)) {
            Text.err(requester, "Этот звук уже загружается: " + id);
            return;
        }
        Text.msg(requester, "Загружаю «" + id + "» по ссылке…");
        UUID playerId = requester instanceof Player player ? player.getUniqueId() : null;
        String soundId = id;
        String soundUrl = url;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = null;
            Exception error = null;
            try { result = downloader.download(soundId, soundUrl); }
            catch (Exception ex) { error = ex; }
            String finalResult = result;
            Exception finalError = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                downloadingIds.remove(soundId);
                CommandSender receiver = playerId == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(playerId);
                if (receiver == null) return;
                if (finalError != null) {
                    Text.err(receiver, "Загрузка не удалась: " + finalError.getMessage());
                    if (receiver instanceof Player player) {
                        Text.msg(player, "Попробуй GitHub Raw или публичную ссылку Dropbox. Можно загрузить файл вручную в plugins/OffmineSoundAPI/sounds/ и выполнить /osv reload.");
                        player.sendMessage(ui("<#D7B2A4>↳ <#F5D3B6><click:run_command:'/soundadmin'>Открыть библиотеку снова</click>"));
                    }
                    return;
                }
                try {
                    plugin.library().add(soundId, finalResult);
                    Text.msg(receiver, "Звук «" + soundId + "» добавлен: " + finalResult);
                    if (receiver instanceof Player player) openSound(player, soundId);
                } catch (Exception ex) {
                    Text.err(receiver, "Файл скачан, но не добавлен в библиотеку: " + ex.getMessage());
                }
            });
        });
    }

    public static String guessId(String url) {
        try {
            String path = URI.create(url).getPath();
            String base = path.substring(path.lastIndexOf('/') + 1)
                    .replaceFirst("(?i)\\.(wav|mp3|ogg|flac|aac|m4a|opus)$", "");
            base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
            if (base.length() > 48) base = base.substring(0, 48);
            return base.isEmpty() ? "sound" : base;
        } catch (Exception ignored) { return "sound"; }
    }

    /** Backward-compatible /osv code <id> copy links. */
    public static void sendCode(Player p, String id) {
        p.sendMessage(ui("<#EECDB7>✦ <#FFE8D6>Звук <#F6D5B9><id> <#CDAEA1>— нажмите, чтобы скопировать код:", "id", id));
        copy(p, "Игроку", "SoundApi.get().play(player, \"" + id + "\");");
        copy(p, "Всем", "SoundApi.get().playAll(\"" + id + "\");");
        copy(p, "По координатам", "SoundApi.get().playAt(location, \"" + id + "\", 24f);");
        copy(p, "Импорт", "import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;");
    }

    private static void copy(Player player, String caption, String snippet) {
        player.sendMessage(ui("<#D7E2C5>⧉ Скопировать <#F6D6BA>" + caption)
                .clickEvent(ClickEvent.copyToClipboard(snippet))
                .hoverEvent(HoverEvent.showText(ui("<#F1D4BF>Нажми, чтобы скопировать"))));
    }

    public void shutdown() { downloadingIds.clear(); }
}
