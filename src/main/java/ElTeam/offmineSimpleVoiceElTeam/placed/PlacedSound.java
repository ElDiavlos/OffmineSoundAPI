package ElTeam.offmineSimpleVoiceElTeam.placed;

import ElTeam.offmineSimpleVoiceElTeam.audio.QueueFrameSupplier;
import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlacedSound {

    public static final class AudioTrack {
        public final String fileName;
        public final short[] pcm;

        public AudioTrack(String fileName, short[] pcm) {
            this.fileName = fileName;
            this.pcm = pcm;
        }
    }

    private final String id;
    private final Location location;
    private final double radius;
    private final LocationalAudioChannel channel;

    private volatile AudioPlayer player;
    private volatile QueueFrameSupplier supplier;

    private final boolean loopQueue;
    private final List<AudioTrack> tracks;

    public PlacedSound(String id,
                       Location location,
                       double radius,
                       LocationalAudioChannel channel,
                       List<AudioTrack> initialTracks,
                       boolean loopQueue,
                       AudioPlayer startedPlayer,
                       QueueFrameSupplier startedSupplier) {
        this.id = id;
        this.location = location.clone();
        this.radius = radius;
        this.channel = channel;
        this.loopQueue = loopQueue;
        this.tracks = Collections.synchronizedList(new ArrayList<>(initialTracks != null ? initialTracks : List.of()));
        this.player = startedPlayer;
        this.supplier = startedSupplier;
    }

    public String id() { return id; }
    public Location location() { return location.clone(); }
    public double radius() { return radius; }
    public boolean loop() { return loopQueue; }

    public List<String> fileList() {
        synchronized (tracks) {
            List<String> names = new ArrayList<>(tracks.size());
            for (AudioTrack t : tracks) names.add(t.fileName);
            return names;
        }
    }

    public synchronized void enqueue(String fileName, short[] pcm) {
        AudioTrack at = new AudioTrack(fileName, pcm);
        tracks.add(at);

        QueueFrameSupplier s = supplier;
        AudioPlayer ap = player;

        if (s != null && ap != null && ap.isPlaying()) {
            s.addTrack(pcm);
            return;
        }

        startNewPlayerFrom(tracks.size() - 1, 0);
    }

    private synchronized void startNewPlayerFrom(int startTrackIndex, int startSample) {
        List<short[]> initial = new ArrayList<>();
        synchronized (tracks) {
            for (AudioTrack t : tracks) initial.add(t.pcm);
        }
        QueueFrameSupplier s = new QueueFrameSupplier(initial, loopQueue, startTrackIndex, startSample);
        AudioPlayer ap = VoiceService.get().play(s, channel);
        this.supplier = s;
        this.player = ap;
    }

    public void stop() {
        try { if (player != null && player.isPlaying()) player.stopPlaying(); } catch (Throwable ignored) {}
        try { if (channel != null && !channel.isClosed()) channel.flush(); } catch (Throwable ignored) {}
    }
}
