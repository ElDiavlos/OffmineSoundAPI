package ElTeam.offmineSimpleVoiceElTeam.library;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Stable IDs are stored in sounds.yml; file names are never accepted as paths. */
public final class SoundLibrary {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final Set<String> EXT = Set.of("wav", "mp3", "ogg", "flac", "aac", "m4a", "opus");
    private final OffmineSimpleVoiceElTeam plugin;
    private final File directory;
    private final File registry;
    private final Map<String, String> entries = new TreeMap<>();

    public SoundLibrary(OffmineSimpleVoiceElTeam plugin, File dir) {
        this.plugin = plugin;
        directory = dir;
        registry = new File(plugin.getDataFolder(), "sounds.yml");
    }

    public static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }
    public static boolean supported(String name) {
        String x = name.toLowerCase(Locale.ROOT);
        int dot = x.lastIndexOf('.');
        return dot >= 0 && EXT.contains(x.substring(dot + 1));
    }
    public File directory() { return directory; }

    public synchronized void reload() {
        entries.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(registry);
        if (cfg.isConfigurationSection("sounds")) {
            for (String id : cfg.getConfigurationSection("sounds").getKeys(false)) {
                String name = cfg.getString("sounds." + id);
                if (validId(id) && validName(name) && new File(directory, name).isFile()) entries.put(id, name);
            }
        }
        File[] files = directory.listFiles(f -> f.isFile() && supported(f.getName()));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                if (entries.containsValue(f.getName())) continue;
                String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
                String id = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
                if (id.isEmpty()) id = "sound";
                if (id.length() > 43) id = id.substring(0, 43);
                String candidate = id;
                for (int n = 2; entries.containsKey(candidate); n++) candidate = id + "_" + n;
                entries.put(candidate, f.getName());
            }
        }
        save();
    }

    public synchronized void add(String id, String fileName) {
        if (!validId(id) || !validName(fileName)) throw new IllegalArgumentException("Неверный ID или имя файла");
        if (entries.containsKey(id)) throw new IllegalArgumentException("ID уже существует: " + id);
        if (!new File(directory, fileName).isFile()) throw new IllegalArgumentException("Нет файла: " + fileName);
        entries.put(id, fileName);
        save();
    }

    public synchronized boolean contains(String id) { return entries.containsKey(id); }
    public synchronized List<String> ids() { return List.copyOf(entries.keySet()); }
    public synchronized Optional<String> fileName(String id) { return Optional.ofNullable(entries.get(id)); }
    public synchronized Path resolve(String id) {
        String file = entries.get(id);
        if (file == null) throw new IllegalArgumentException("Звук не найден: " + id);
        Path path = directory.toPath().toAbsolutePath().normalize().resolve(file).normalize();
        if (!path.startsWith(directory.toPath().toAbsolutePath().normalize()) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Файл звука отсутствует: " + id);
        }
        return path;
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\")
                && !name.equals(".") && !name.equals("..") && supported(name);
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        entries.forEach((id, file) -> cfg.set("sounds." + id, file));
        try { cfg.save(registry); }
        catch (IOException e) { plugin.getLogger().severe("Не удалось сохранить sounds.yml: " + e.getMessage()); }
    }
}
