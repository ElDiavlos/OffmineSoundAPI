package ElTeam.offmineSimpleVoiceElTeam.placed;

import java.util.*;

public class PlacedSoundRegistry {

    private final Map<String, PlacedSound> map = new LinkedHashMap<>();

    public Collection<PlacedSound> all() { return Collections.unmodifiableCollection(map.values()); }

    public Optional<PlacedSound> get(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public String add(PlacedSound ps) {
        map.put(ps.id(), ps);
        return ps.id();
    }

    public Optional<PlacedSound> remove(String id) {
        PlacedSound ps = map.remove(id);
        if (ps != null) ps.stop();
        return Optional.ofNullable(ps);
    }

    public void stopAll() {
        for (PlacedSound ps : map.values()) ps.stop();
        map.clear();
    }
}
