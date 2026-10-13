package ElTeam.offmineSimpleVoiceElTeam.playback;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import ElTeam.offmineSimpleVoiceElTeam.audio.AudioUtil;
import ElTeam.offmineSimpleVoiceElTeam.audio.FrameSupplier;
import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalPlaybackManager {

    private static final GlobalPlaybackManager INSTANCE = new GlobalPlaybackManager();
    public static GlobalPlaybackManager get() { return INSTANCE; }
    private GlobalPlaybackManager() {}

    public static final String CFG_KEY = "global_playbacks";

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public static final class Session {
        final UUID id;
        final String fileName;
        final boolean loop;
        final short[] pcm;
        final int lengthSamples;
        final long startEpochMs;
        volatile boolean closed = false;

        final Map<UUID, AudioPlayer> players = new ConcurrentHashMap<>();

        Session(String fileName, boolean loop, short[] pcm) {
            this(UUID.randomUUID(), fileName, loop, pcm, System.currentTimeMillis());
        }

        Session(UUID id, String fileName, boolean loop, short[] pcm, long startEpochMs) {
            this.id = id;
            this.fileName = fileName;
            this.loop = loop;
            this.pcm = pcm;
            this.lengthSamples = pcm.length;
            this.startEpochMs = startEpochMs;
        }

        int currentOffsetSamples() {
            long elapsedMs = Math.max(0L, System.currentTimeMillis() - startEpochMs);
            long elapsedSamples = elapsedMs * 48L;
            if (loop && lengthSamples > 0) {
                return (int)(elapsedSamples % lengthSamples);
            }
            return (int)Math.min(elapsedSamples, lengthSamples);
        }
    }

    public void startPlayAll(String fileName, boolean loop) throws Exception {
        File soundsDir = new File(OffmineSimpleVoiceElTeam.get().getDataFolder(), "sounds");
        Path file = new File(soundsDir, fileName).toPath();
        if (!AudioUtil.exists(file)) throw new IllegalArgumentException("Файл не найден: " + file.getFileName());

        short[] pcm = AudioUtil.loadAudioToPcm48Mono(file);
        Session session = new Session(file.getFileName().toString(), loop, pcm);
        sessions.put(session.id, session);

        for (Player p : Bukkit.getOnlinePlayers()) {
            attachPlayerToSession(session, p);
        }

        if (!loop) {
            scheduleStopWhenFinished(session);
        }
    }

    public void attachPlayerToAllSessions(Player player) {
        for (Session s : sessions.values()) {
            attachPlayerToSession(s, player);
        }
    }

    private void attachPlayerToSession(Session s, Player p) {
        if (s.closed) return;
        if (!VoiceService.get().isReady()) return;
        int offset = s.currentOffsetSamples();
        if (!s.loop && offset >= s.lengthSamples) {
            return;
        }
        StaticAudioChannel ch = VoiceService.get().createStaticForPlayer(p);
        if (ch == null) {
            retryAttachLater(s, p.getUniqueId(), 10);
            return;
        }
        FrameSupplier supplier = new FrameSupplier(s.pcm, s.loop, offset);
        AudioPlayer ap = VoiceService.get().play(supplier, ch);
        if (ap != null) {
            s.players.put(p.getUniqueId(), ap);
            VoiceService.get().track(p.getUniqueId(), ap, ch, !s.loop);
        }
    }

    private void retryAttachLater(Session s, UUID playerId, int attempts) {
        if (attempts <= 0) return;
        Bukkit.getScheduler().runTaskLater(OffmineSimpleVoiceElTeam.get(), () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                attachPlayerToSession(s, p);
            } else {
                retryAttachLater(s, playerId, attempts - 1);
            }
        }, 1L);
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            stopSession(id);
        }
        sessions.clear();
    }

    private void stopSession(UUID id) {
        Session s = sessions.remove(id);
        if (s == null) return;
        s.closed = true;
        for (AudioPlayer ap : s.players.values()) {
            try { if (ap != null && ap.isPlaying()) ap.stopPlaying(); } catch (Throwable ignored) {}
        }
        s.players.clear();
    }

    private void scheduleStopWhenFinished(Session s) {
        int offset = s.currentOffsetSamples();
        if (offset >= s.lengthSamples) {
            stopSession(s.id);
            return;
        }
        long remainingSamples = (long) s.lengthSamples - offset;
        long remainingMs = Math.max(1L, Math.round(remainingSamples / 48_000.0 * 1000.0));
        long ticks = Math.max(1L, Math.round(remainingMs / 50.0));
        Bukkit.getScheduler().runTaskLater(OffmineSimpleVoiceElTeam.get(), () -> stopSession(s.id), ticks);
    }

    public void saveToConfig() {
        FileConfiguration cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        List<Map<String, Object>> out = new ArrayList<>();

        for (Session s : sessions.values()) {
            int offset = s.currentOffsetSamples();
            if (!s.loop && offset >= s.lengthSamples) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id.toString());
            m.put("file", s.fileName);
            m.put("loop", s.loop);
            m.put("start_epoch_ms", s.startEpochMs);
            out.add(m);
        }

        cfg.set(CFG_KEY, out);
        OffmineSimpleVoiceElTeam.get().saveConfig();
    }

    public void loadFromConfig() {
        FileConfiguration cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        List<Map<?, ?>> raw = cfg.getMapList(CFG_KEY);
        if (raw == null || raw.isEmpty()) return;

        File soundsDir = new File(OffmineSimpleVoiceElTeam.get().getDataFolder(), "sounds");

        for (Map<?, ?> m : raw) {
            try {
                UUID id = m.containsKey("id") ? UUID.fromString(String.valueOf(m.get("id"))) : UUID.randomUUID();
                String fileName = String.valueOf(m.get("file"));
                boolean loop = m.containsKey("loop") && Boolean.parseBoolean(String.valueOf(m.get("loop")));
                long startEpochMs = System.currentTimeMillis();
                if (m.containsKey("start_epoch_ms")) {
                    try { startEpochMs = Long.parseLong(String.valueOf(m.get("start_epoch_ms"))); } catch (Exception ignored) {}
                }

                Path file = new File(soundsDir, fileName).toPath();
                if (!AudioUtil.exists(file)) {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Глобальный звук: файл не найден: " + fileName);
                    continue;
                }

                short[] pcm = AudioUtil.loadAudioToPcm48Mono(file);
                Session s = new Session(id, fileName, loop, pcm, startEpochMs);

                if (!s.loop && s.currentOffsetSamples() >= s.lengthSamples) {
                    continue;
                }

                sessions.put(s.id, s);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    attachPlayerToSession(s, p);
                }

                if (!s.loop) {
                    scheduleStopWhenFinished(s);
                }
            } catch (Throwable t) {
                OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Не удалось восстановить глобальное воспроизведение: " + t.getMessage());
            }
        }
    }
}
