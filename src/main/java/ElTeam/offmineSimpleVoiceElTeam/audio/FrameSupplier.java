package ElTeam.offmineSimpleVoiceElTeam.audio;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class FrameSupplier implements Supplier<short[]> {
    public static final int FRAME = 960;

    private final short[] pcm;
    private final boolean loop;
    private final AtomicInteger idx;

    public FrameSupplier(short[] pcm48kMono, boolean loop) {
        this(pcm48kMono, loop, 0);
    }

    public FrameSupplier(short[] pcm48kMono, boolean loop, int startSamples) {
        this.pcm = pcm48kMono;
        this.loop = loop;
        if (startSamples < 0) startSamples = 0;
        if (startSamples > pcm48kMono.length) startSamples = pcm48kMono.length;
        this.idx = new AtomicInteger(startSamples);
    }

    public int currentSample() {
        return Math.max(0, Math.min(idx.get(), pcm.length));
    }

    public int lengthSamples() {
        return pcm.length;
    }

    public boolean isLoop() { return loop; }

    @Override
    public short[] get() {
        int i = idx.get();
        if (i >= pcm.length) {
            if (!loop) return null;
            i = 0;
            idx.set(0);
        }
        int end = Math.min(i + FRAME, pcm.length);
        short[] slice = Arrays.copyOfRange(pcm, i, end);
        if (slice.length < FRAME) {
            short[] padded = new short[FRAME];
            System.arraycopy(slice, 0, padded, 0, slice.length);
            idx.set(pcm.length);
            return padded;
        } else {
            idx.addAndGet(FRAME);
            return slice;
        }
    }
}
