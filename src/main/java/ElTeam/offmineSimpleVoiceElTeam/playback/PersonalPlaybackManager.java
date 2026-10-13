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

public final class PersonalPlaybackManager {

    private static final PersonalPlaybackManager INSTANCE = new PersonalPlaybackManager();
    public static PersonalPlaybackManager get() { return INSTANCE; }
    private PersonalPlaybackManager() {}

    public static final String CFG_KEY = "personal_playbacks";

    public static final class PSession {
        final String fileName;
        final boolean loop;
        final short[] pcm;
        final int lengthSamples;

        volatile int resumeSamples;
        volatile boolean playing;
        volatile FrameSupplier supplier;
        volatile AudioPlayer player;

        PSession(String fileName, boolean loop, short[] pcm) {
            this.fileName = fileName;
            this.loop = loop;
            this.pcm = pcm;
            this.lengthSamples = pcm.length;
            this.resumeSamples = 0;
            this.playing = false;
        }
    }

    private final Map<UUID, List<PSession>> sessions = new ConcurrentHashMap<>();

    public void startForPlayer(Player p, String fileName, boolean loop) throws Exception {
        File soundsDir = new File(OffmineSimpleVoiceElTeam.get().getDataFolder(), "sounds");
        Path file = new File(soundsDir, fileName).toPath();
        if (!AudioUtil.exists(file)) throw new IllegalArgumentException("Файл не найден: " + file.getFileName());

        short[] pcm = AudioUtil.loadAudioToPcm48Mono(file);
        PSession ps = new PSession(file.getFileName().toString(), loop, pcm);
        sessions.computeIfAbsent(p.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>())).add(ps);
        attachNowOrLater(p, ps);
    }

    public void onQuit(Player p) {
        List<PSession> list = sessions.get(p.getUniqueId());
        if (list == null) return;
        for (PSession ps : list) {
            if (ps.playing && ps.supplier != null) {
                ps.resumeSamples = ps.supplier.currentSample();
            }
            if (ps.player != null) {
                try { if (ps.player.isPlaying()) ps.player.stopPlaying(); } catch (Throwable ignored) {}
            }
            ps.playing = false;
            ps.player = null;
            ps.supplier = null;
        }
    }

    public void onJoin(Player p) {
        List<PSession> list = sessions.get(p.getUniqueId());
        if (list == null) return;
        for (PSession ps : list) {
            if (!ps.playing && ps.resumeSamples < ps.lengthSamples) {
                attachNowOrLater(p, ps);
            }
        }
    }

    public void clearFor(UUID playerId) {
        List<PSession> list = sessions.remove(playerId);
        if (list == null) return;
        for (PSession ps : list) {
            if (ps.player != null) {
                try { if (ps.player.isPlaying()) ps.player.stopPlaying(); } catch (Throwable ignored) {}
            }
        }
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            clearFor(id);
        }
        sessions.clear();
    }

    private void attachNowOrLater(Player p, PSession ps) {
        if (!VoiceService.get().isReady()) {
            retryAttach(p.getUniqueId(), ps, 10);
            return;
        }
        StaticAudioChannel ch = VoiceService.get().createStaticForPlayer(p);
        if (ch == null) {
            retryAttach(p.getUniqueId(), ps, 10);
            return;
        }
        int start = Math.max(0, Math.min(ps.resumeSamples, ps.lengthSamples));
        if (!ps.loop && start >= ps.lengthSamples) {
            return;
        }
        FrameSupplier supplier = new FrameSupplier(ps.pcm, ps.loop, start);
        AudioPlayer ap = VoiceService.get().play(supplier, ch);
        if (ap != null) {
            ps.supplier = supplier;
            ps.player = ap;
            ps.playing = true;
            VoiceService.get().track(p.getUniqueId(), ap, ch, !ps.loop);
        }
    }

    private void retryAttach(UUID playerId, PSession ps, int attempts) {
        if (attempts <= 0) return;
        Bukkit.getScheduler().runTaskLater(OffmineSimpleVoiceElTeam.get(), () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                attachNowOrLater(p, ps);
            } else {
                retryAttach(playerId, ps, attempts - 1);
            }
        }, 1L);
    }

    public void snapshotAll() {
        for (var entry : sessions.entrySet()) {
            List<PSession> list = entry.getValue();
            if (list == null) continue;
            for (PSession ps : list) {
                if (ps.playing && ps.supplier != null) {
                    ps.resumeSamples = ps.supplier.currentSample();
                }
            }
        }
    }

    public void saveToConfig() {
        FileConfiguration cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        List<Map<String, Object>> out = new ArrayList<>();

        for (var e : sessions.entrySet()) {
            UUID playerId = e.getKey();
            for (PSession ps : e.getValue()) {
                int resume = (ps.playing && ps.supplier != null) ? ps.supplier.currentSample() : ps.resumeSamples;
                if (!ps.loop && resume >= ps.lengthSamples) continue;

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("player", playerId.toString());
                m.put("file", ps.fileName);
                m.put("loop", ps.loop);
                m.put("resume_samples", Math.max(0, Math.min(resume, ps.lengthSamples)));
                out.add(m);
            }
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
                UUID playerId = UUID.fromString(String.valueOf(m.get("player")));
                String fileName = String.valueOf(m.get("file"));
                boolean loop = m.containsKey("loop") && Boolean.parseBoolean(String.valueOf(m.get("loop")));
                int resume = 0;
                if (m.containsKey("resume_samples")) {
                    try { resume = Integer.parseInt(String.valueOf(m.get("resume_samples"))); } catch (Exception ignored) {}
                }

                Path file = new File(soundsDir, fileName).toPath();
                if (!AudioUtil.exists(file)) {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Персональный звук: файл не найден: " + fileName);
                    continue;
                }

                short[] pcm = AudioUtil.loadAudioToPcm48Mono(file);
                PSession ps = new PSession(fileName, loop, pcm);
                ps.resumeSamples = Math.max(0, Math.min(resume, ps.lengthSamples));

                sessions.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>())).add(ps);

                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    attachNowOrLater(p, ps);
                }
            } catch (Throwable t) {
                OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Не удалось восстановить персональное воспроизведение: " + t.getMessage());
            }
        }
    }
}
