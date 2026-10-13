package ElTeam.offmineSimpleVoiceElTeam.audio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class QueueFrameSupplier implements Supplier<short[]> {
    public static final int FRAME = 960;

    private final CopyOnWriteArrayList<short[]> tracks = new CopyOnWriteArrayList<>();
    private final boolean loopQueue;
    private final AtomicInteger trackIndex;
    private final AtomicInteger sampleInTrack;

    public QueueFrameSupplier(List<short[]> initialTracks, boolean loopQueue) {
        this(initialTracks, loopQueue, 0, 0);
    }

    public QueueFrameSupplier(List<short[]> initialTracks, boolean loopQueue, int startTrackIndex, int startSampleInTrack) {
        if (initialTracks != null) this.tracks.addAll(initialTracks);
        this.loopQueue = loopQueue;
        int ti = Math.max(0, Math.min(startTrackIndex, Math.max(0, tracks.size() - 1)));
        this.trackIndex = new AtomicInteger(ti);
        int len = tracks.isEmpty() ? 0 : tracks.get(ti).length;
        int si = Math.max(0, Math.min(startSampleInTrack, len));
        this.sampleInTrack = new AtomicInteger(si);
    }

    public void addTrack(short[] pcm48kMono) {
        if (pcm48kMono == null || pcm48kMono.length == 0) return;
        tracks.add(pcm48kMono);
    }

    public int trackCount() { return tracks.size(); }

    public int currentTrackIndex() { return trackIndex.get(); }

    public int currentSampleInTrack() { return sampleInTrack.get(); }

    @Override
    public short[] get() {
        while (true) {
            int ti = trackIndex.get();
            if (tracks.isEmpty() || ti >= tracks.size() || ti < 0) {
                if (loopQueue && !tracks.isEmpty()) {
                    trackIndex.set(0);
                    sampleInTrack.set(0);
                    continue;
                }
                return null;
            }

            short[] t = tracks.get(ti);
            int si = sampleInTrack.get();

            if (si >= t.length) {
                int next = ti + 1;
                if (next >= tracks.size()) {
                    if (loopQueue && !tracks.isEmpty()) {
                        trackIndex.set(0);
                        sampleInTrack.set(0);
                        continue;
                    } else {
                        return null;
                    }
                } else {
                    trackIndex.set(next);
                    sampleInTrack.set(0);
                    continue;
                }
            }

            int end = Math.min(si + FRAME, t.length);
            int len = end - si;
            short[] out = new short[FRAME];
            System.arraycopy(t, si, out, 0, len);

            if (end >= t.length) {
                sampleInTrack.set(t.length);
            } else {
                sampleInTrack.addAndGet(FRAME);
            }
            return out;
        }
    }
}
