package ElTeam.offmineSimpleVoiceElTeam.api;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import ElTeam.offmineSimpleVoiceElTeam.audio.AudioUtil;
import ElTeam.offmineSimpleVoiceElTeam.audio.FrameSupplier;
import ElTeam.offmineSimpleVoiceElTeam.audio.QueueFrameSupplier;
import ElTeam.offmineSimpleVoiceElTeam.placed.PlacedSound;
import ElTeam.offmineSimpleVoiceElTeam.playback.GlobalPlaybackManager;
import ElTeam.offmineSimpleVoiceElTeam.playback.PersonalPlaybackManager;
import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Public API for other Bukkit plugins. Call from the server main thread. */
public final class SoundApi {
    private final OffmineSimpleVoiceElTeam plugin;
    private final Map<UUID, PositionalSound> positions = new ConcurrentHashMap<>();

    private record PositionalSound(AudioPlayer player, LocationalAudioChannel channel, BukkitTask expiry) {
        void stop() {
            if (expiry != null) expiry.cancel();
            try { if (player != null && player.isPlaying()) player.stopPlaying(); } catch (Exception ignored) { }
            try { if (channel != null && !channel.isClosed()) channel.flush(); } catch (Exception ignored) { }
        }
    }

    public SoundApi(OffmineSimpleVoiceElTeam plugin) { this.plugin = plugin; }

    /** Also registered in Bukkit ServicesManager as SoundApi.class. */
    public static SoundApi get() {
        return OffmineSimpleVoiceElTeam.get().sounds();
    }

    public List<String> listSounds() { return plugin.library().ids(); }
    public boolean exists(String id) { return plugin.library().contains(id); }
    public Path file(String id) { return plugin.library().resolve(id); }
    public boolean isReady() { return VoiceService.get().isReady(); }

    public void play(Player player, String soundId) { play(player, soundId, false); }
    public void play(Player player, String soundId, boolean loop) {
        mainThread();
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) throw new IllegalArgumentException("Игрок не в сети");
        ready();
        try { PersonalPlaybackManager.get().startForPlayer(player, file(soundId).getFileName().toString(), loop); }
        catch (Exception e) { throw new IllegalStateException("Не удалось воспроизвести звук: " + soundId, e); }
    }

    public void playAll(String soundId) { playAll(soundId, false); }
    public void playAll(String soundId, boolean loop) {
        mainThread();
        ready();
        try { GlobalPlaybackManager.get().startPlayAll(file(soundId).getFileName().toString(), loop); }
        catch (Exception e) { throw new IllegalStateException("Не удалось воспроизвести звук: " + soundId, e); }
    }

    /** Play a temporary position-based sound. Returns a handle to stop it. Not saved after restart. */
    public UUID playAt(Location location, String soundId, float radius) {
        return playAt(location, soundId, radius, false);
    }

    public UUID playAt(Location location, String soundId, float radius, boolean loop) {
        mainThread();
        checkLocation(location, radius);
        ready();
        short[] pcm;
        try { pcm = AudioUtil.loadAudioToPcm48Mono(file(soundId)); }
        catch (Exception e) { throw new IllegalStateException("Не удалось прочитать звук: " + soundId, e); }
        if (pcm.length == 0) throw new IllegalArgumentException("Звуковой файл пустой");
        LocationalAudioChannel ch = VoiceService.get().createLocational(location.getWorld(), location, radius);
        if (ch == null) throw new IllegalStateException("Не удалось создать звуковой канал");
        AudioPlayer player = VoiceService.get().play(new FrameSupplier(pcm, loop), ch);
        if (player == null) { ch.flush(); throw new IllegalStateException("Не удалось начать воспроизведение"); }
        UUID handle = UUID.randomUUID();
        BukkitTask expiry = loop ? null : Bukkit.getScheduler().runTaskLater(plugin,
                () -> stopAt(handle), Math.max(1L, (long) Math.ceil(pcm.length / 2400.0) + 2L));
        positions.put(handle, new PositionalSound(player, ch, expiry));
        return handle;
    }

    public boolean stopAt(UUID handle) {
        mainThread();
        PositionalSound session = positions.remove(handle);
        if (session == null) return false;
        session.stop();
        return true;
    }

    public void stopAllAt() {
        mainThread();
        for (UUID handle : new ArrayList<>(positions.keySet())) stopAt(handle);
    }

    /** Persistent location sound / queue, restored after restart. */
    public void place(String beaconId, Location location, float radius, String soundId, boolean loop) {
        mainThread();
        if (beaconId == null || beaconId.isBlank()) throw new IllegalArgumentException("Укажите ID маяка");
        if (plugin.beacons().registry().get(beaconId).isPresent()) throw new IllegalArgumentException("ID маяка уже существует");
        checkLocation(location, radius);
        ready();
        Path path = file(soundId);
        short[] pcm;
        try { pcm = AudioUtil.loadAudioToPcm48Mono(path); }
        catch (Exception e) { throw new IllegalStateException("Не удалось прочитать звук: " + soundId, e); }
        if (pcm.length == 0) throw new IllegalArgumentException("Пустой файл");
        LocationalAudioChannel ch = VoiceService.get().createLocational(location.getWorld(), location, radius);
        if (ch == null) throw new IllegalStateException("Не удалось создать канал");
        QueueFrameSupplier frames = new QueueFrameSupplier(List.of(pcm), loop, 0, 0);
        AudioPlayer player = VoiceService.get().play(frames, ch);
        if (player == null) { ch.flush(); throw new IllegalStateException("Не удалось запустить звук"); }
        PlacedSound sound = new PlacedSound(beaconId, location, radius, ch,
                List.of(new PlacedSound.AudioTrack(path.getFileName().toString(), pcm)), loop, player, frames);
        plugin.beacons().addAndPersist(sound);
    }

    public boolean removeBeacon(String beaconId) {
        mainThread();
        return plugin.beacons().removeAndPersist(beaconId);
    }
    public void enqueue(String beaconId, String soundId) {
        mainThread();
        try {
            if (!plugin.beacons().enqueueToBeacon(beaconId, file(soundId).getFileName().toString()))
                throw new IllegalArgumentException("Маяк не найден: " + beaconId);
        } catch (Exception e) { throw new IllegalStateException("Не удалось добавить звук в очередь маяка", e); }
    }
    public void stopPlayer(Player player) {
        mainThread();
        Objects.requireNonNull(player, "player");
        VoiceService.get().stopForPlayer(player.getUniqueId());
        PersonalPlaybackManager.get().clearFor(player.getUniqueId());
    }
    public void stopAllPlayers() {
        mainThread();
        VoiceService.get().stopForAllPlayers();
        PersonalPlaybackManager.get().clearAll();
        GlobalPlaybackManager.get().stopAll();
    }
    public void stopGlobal() { mainThread(); GlobalPlaybackManager.get().stopAll(); }
    public void shutdown() { stopAllAt(); }

    private static void checkLocation(Location location, float radius) {
        if (location == null || location.getWorld() == null) throw new IllegalArgumentException("Нужен мир и Location");
        if (!(radius > 0 && radius <= 1024) || !Float.isFinite(radius))
            throw new IllegalArgumentException("Радиус должен быть от 0 до 1024");
    }
    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("SoundApi вызывается только из основного потока сервера");
    }
    private static void ready() {
        if (!VoiceService.get().isReady()) throw new IllegalStateException("Simple Voice Chat ещё не запущен");
    }
}
