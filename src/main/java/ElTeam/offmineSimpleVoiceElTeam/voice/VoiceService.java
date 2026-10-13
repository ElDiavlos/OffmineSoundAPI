package ElTeam.offmineSimpleVoiceElTeam.voice;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.*;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class VoiceService {

    private static final VoiceService INSTANCE = new VoiceService();
    public static VoiceService get() { return INSTANCE; }

    private volatile VoicechatServerApi api;
    private volatile String categoryError = "Simple Voice Chat ещё не инициализирован";

    public void attach(VoicechatServerApi api) { this.api = Objects.requireNonNull(api); }
    public void detach() {
        this.api = null;
        categoryError = "Simple Voice Chat отключён";
    }

    /** Registration is global and persists for clients joining later (SVC 2.6 API).
     *  Keeping the ID "osv" also preserves existing per-client volume preferences.
     */
    public synchronized boolean ensureVolumeCategory() {
        VoicechatServerApi current = api;
        if (current == null) {
            categoryError = "Simple Voice Chat API недоступен";
            return false;
        }
        try {
            if (hasVolumeCategory(current)) {
                categoryError = "";
                return true;
            }
            VolumeCategory category = current.volumeCategoryBuilder()
                    .setId(OffmineSimpleVoiceElTeam.CATEGORY_ID)
                    .setName("Звуки сервера")
                    .setDescription("Громкость звуков OffmineSoundAPI")
                    .build();
            current.registerVolumeCategory(category);
            if (hasVolumeCategory(current)) {
                categoryError = "";
                return true;
            }
            categoryError = "Категория не найдена в Voice Chat после регистрации";
            return false;
        } catch (RuntimeException ex) {
            categoryError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return false;
        }
    }

    private static boolean hasVolumeCategory(VoicechatServerApi voicechat) {
        Collection<VolumeCategory> categories = voicechat.getVolumeCategories();
        return categories != null && categories.stream().anyMatch(category ->
                OffmineSimpleVoiceElTeam.CATEGORY_ID.equals(category.getId()));
    }

    public boolean isVolumeCategoryRegistered() {
        VoicechatServerApi current = api;
        if (current == null) return false;
        try { return hasVolumeCategory(current); }
        catch (RuntimeException ex) {
            categoryError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return false;
        }
    }

    public String volumeCategoryStatus() {
        if (api == null) return "Voice Chat API недоступен";
        if (isVolumeCategoryRegistered()) return "Звуки сервера (id=osv) зарегистрирована";
        return categoryError.isBlank() ? "Категория отсутствует" : categoryError;
    }
    public boolean isReady() { return api != null; }
    public Optional<VoicechatServerApi> api() { return Optional.ofNullable(api); }

    public StaticAudioChannel createStaticForPlayer(Player bukkitPlayer) {
        if (api == null) return null;
        var conn = api.getConnectionOf(bukkitPlayer.getUniqueId());
        if (conn == null) return null;
        StaticAudioChannel ch = api.createStaticAudioChannel(
                UUID.randomUUID(),
                api.fromServerLevel(bukkitPlayer.getWorld()),
                conn
        );
        return categorize(ch);
    }

    public LocationalAudioChannel createLocational(World world, Location loc, float distance) {
        if (api == null) return null;
        LocationalAudioChannel ch = api.createLocationalAudioChannel(
                UUID.randomUUID(),
                api.fromServerLevel(world),
                api.createPosition(loc.getX(), loc.getY(), loc.getZ())
        );
        if (ch != null) ch.setDistance(distance);
        return categorize(ch);
    }

    /** Every channel uses the same client-side Simple Voice Chat volume slider.
     * This applies to personal, global and locational sounds alike.
     */
    private static <T extends AudioChannel> T categorize(T channel) {
        if (channel != null) {
            channel.setCategory(OffmineSimpleVoiceElTeam.CATEGORY_ID);
        }
        return channel;
    }

    public AudioPlayer play(short[] pcm48kMono, AudioChannel channel) {
        if (api == null || channel == null) return null;
        OpusEncoder enc = api.createEncoder();
        AudioPlayer player = api.createAudioPlayer(channel, enc, pcm48kMono);
        player.startPlaying();
        return player;
    }

    public AudioPlayer play(Supplier<short[]> frameSupplier, AudioChannel channel) {
        if (api == null || channel == null) return null;
        OpusEncoder enc = api.createEncoder();
        AudioPlayer player = api.createAudioPlayer(channel, enc, frameSupplier);
        player.startPlaying();
        return player;
    }

    public double broadcastRange() {
        return api == null ? 48.0 : api.getBroadcastRange();
    }

    private final Map<UUID, List<ActivePlayback>> playerPlaybacks = new ConcurrentHashMap<>();

    private static final class ActivePlayback {
        final AudioPlayer player;
        final AudioChannel channel;
        final boolean cleanupOnStop;
        BukkitTask watcher;

        ActivePlayback(AudioPlayer player, AudioChannel channel, boolean cleanupOnStop) {
            this.player = player;
            this.channel = channel;
            this.cleanupOnStop = cleanupOnStop;
        }

        void stop() {
            try { if (watcher != null) watcher.cancel(); } catch (Throwable ignored) {}
            try { if (player != null && player.isPlaying()) player.stopPlaying(); } catch (Throwable ignored) {}
            try { if (channel != null && !channel.isClosed()) channel.flush(); } catch (Throwable ignored) {}
        }
    }

    public void track(UUID playerId, AudioPlayer player, AudioChannel channel) {
        track(playerId, player, channel, false);
    }

    public void track(UUID playerId, AudioPlayer player, AudioChannel channel, boolean cleanupOnStop) {
        if (player == null || playerId == null) return;
        ActivePlayback ap = new ActivePlayback(player, channel, cleanupOnStop);
        playerPlaybacks
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(ap);

        if (cleanupOnStop) {
            ap.watcher = Bukkit.getScheduler().runTaskTimer(
                    OffmineSimpleVoiceElTeam.get(),
                    () -> {
                        try {
                            if (!player.isPlaying()) {
                                ap.stop();
                                removeTracked(playerId, ap);
                            }
                        } catch (Throwable t) {
                            try { ap.stop(); } catch (Throwable ignored) {}
                            removeTracked(playerId, ap);
                        }
                    },
                    10L, 10L
            );
        }
    }

    private void removeTracked(UUID playerId, ActivePlayback ap) {
        List<ActivePlayback> list = playerPlaybacks.get(playerId);
        if (list == null) return;
        list.remove(ap);
        if (list.isEmpty()) playerPlaybacks.remove(playerId);
    }

    public int stopForPlayer(UUID playerId) {
        List<ActivePlayback> list = playerPlaybacks.remove(playerId);
        if (list == null || list.isEmpty()) return 0;
        int n = 0;
        for (ActivePlayback ap : list) { ap.stop(); n++; }
        return n;
    }

    public int stopForAllPlayers() {
        int total = 0;
        for (List<ActivePlayback> list : playerPlaybacks.values()) {
            for (ActivePlayback ap : list) { ap.stop(); total++; }
        }
        playerPlaybacks.clear();
        return total;
    }

    public void shutdown() {
        stopForAllPlayers();
    }
}
