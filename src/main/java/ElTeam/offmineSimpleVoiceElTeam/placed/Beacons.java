package ElTeam.offmineSimpleVoiceElTeam.placed;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import ElTeam.offmineSimpleVoiceElTeam.audio.AudioUtil;
import ElTeam.offmineSimpleVoiceElTeam.audio.QueueFrameSupplier;
import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class Beacons {

    public static final String CFG_KEY = "beacons";

    private final PlacedSoundRegistry registry = new PlacedSoundRegistry();
    private final File soundsDir;

    public Beacons(File soundsDir) {
        this.soundsDir = soundsDir;
    }

    public PlacedSoundRegistry registry() {
        return registry;
    }

    public void loadFromConfig() {
        FileConfiguration cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        List<Map<?, ?>> raw = cfg.getMapList(CFG_KEY);
        boolean changed = false;

        for (Map<?, ?> m : raw) {
            try {
                String id;
                Object idRaw = m.get("id");
                if (idRaw == null || String.valueOf(idRaw).isBlank()) {
                    id = java.util.UUID.randomUUID().toString();
                    ((Map<Object, Object>) m).put("id", id);
                    changed = true;
                } else {
                    id = String.valueOf(idRaw);
                }

                String worldName = String.valueOf(m.get("world"));
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Мир не найден для маяка " + id + ": " + worldName);
                    continue;
                }

                double x = toDouble(m.get("x")), y = toDouble(m.get("y")), z = toDouble(m.get("z"));
                float radius = (float) toDouble(m.get("radius"));
                Object loopObj = m.containsKey("loop") ? m.get("loop") : Boolean.TRUE;
                boolean loop = toBool(loopObj);

                List<String> files = new ArrayList<>();
                if (m.containsKey("files")) {
                    Object f = m.get("files");
                    if (f instanceof List<?> lst) {
                        for (Object o : lst) files.add(String.valueOf(o));
                    } else if (f != null) {
                        files.add(String.valueOf(f));
                    }
                } else if (m.containsKey("file")) {
                    files.add(String.valueOf(m.get("file")));
                    ((Map<Object, Object>) m).remove("file");
                    ((Map<Object, Object>) m).put("files", new ArrayList<>(files));
                    changed = true;
                } else {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] У маяка " + id + " не указаны файлы.");
                    continue;
                }

                List<PlacedSound.AudioTrack> tracks = new ArrayList<>();
                for (String file : files) {
                    Path path = new File(soundsDir, file).toPath();
                    if (!AudioUtil.exists(path)) {
                        OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Файл в очереди маяка " + id + " не найден: " + file);
                        continue;
                    }
                    short[] pcm = AudioUtil.loadAudioToPcm48Mono(path);
                    tracks.add(new PlacedSound.AudioTrack(file, pcm));
                }

                if (tracks.isEmpty()) {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Маяк " + id + ": очередь пустая (все файлы недоступны).");
                    continue;
                }

                Location loc = new Location(w, x, y, z);

                LocationalAudioChannel ch = VoiceService.get().createLocational(w, loc, radius);
                if (ch == null) {
                    OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Невозможно создать локационный канал для маяка " + id);
                    continue;
                }

                List<short[]> initial = tracks.stream().map(t -> t.pcm).collect(Collectors.toList());
                QueueFrameSupplier supplier = new QueueFrameSupplier(initial, loop, 0, 0);
                AudioPlayer player = VoiceService.get().play(supplier, ch);

                PlacedSound ps = new PlacedSound(id, loc, radius, ch, tracks, loop, player, supplier);
                registry.add(ps);
            } catch (Exception ex) {
                OffmineSimpleVoiceElTeam.get().logger().warning("[OSV] Не удалось загрузить маяк: " + ex.getMessage());
            }
        }

        if (changed) {
            OffmineSimpleVoiceElTeam.get().saveConfig();
        }
        OffmineSimpleVoiceElTeam.get().logger().info("[OSV] Загруженные маяки из конфигурации: " + registry.all().size());
    }

    public void saveToConfig() {
        FileConfiguration cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        List<Map<String, Object>> list = registry.all().stream()
                .map(this::serialize)
                .collect(Collectors.toList());
        cfg.set(CFG_KEY, list);
        OffmineSimpleVoiceElTeam.get().saveConfig();
    }

    public void addAndPersist(PlacedSound ps) {
        registry.add(ps);
        saveToConfig();
    }

    public boolean removeAndPersist(String id) {
        var removed = registry.remove(id);
        saveToConfig();
        return removed.isPresent();
    }

    public boolean enqueueToBeacon(String id, String fileName) throws Exception {
        var opt = registry.get(id);
        if (opt.isEmpty()) return false;

        var ps = opt.get();
        Path path = new File(soundsDir, fileName).toPath();
        if (!AudioUtil.exists(path)) throw new IllegalArgumentException("Файл не найден: " + path.getFileName());

        short[] pcm = AudioUtil.loadAudioToPcm48Mono(path);
        ps.enqueue(fileName, pcm);
        saveToConfig();
        return true;
    }

    private Map<String, Object> serialize(PlacedSound ps) {
        Map<String, Object> m = new LinkedHashMap<>();
        var l = ps.location();
        m.put("id", ps.id());
        m.put("world", l.getWorld().getName());
        m.put("x", l.getX());
        m.put("y", l.getY());
        m.put("z", l.getZ());
        m.put("radius", ps.radius());
        m.put("loop", ps.loop());
        m.put("files", ps.fileList());
        return m;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
