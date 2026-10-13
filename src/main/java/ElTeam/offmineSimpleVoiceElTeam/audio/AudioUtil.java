package ElTeam.offmineSimpleVoiceElTeam.audio;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Decode files into the 48kHz/mono/16-bit PCM required by Simple Voice Chat. */
public final class AudioUtil {
    private AudioUtil() { }

    public static short[] loadAudioToPcm48Mono(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new FileNotFoundException(path.toString());
        int maxSeconds = Math.max(1, OffmineSimpleVoiceElTeam.get().getConfig().getInt("audio.max_duration_seconds", 300));
        int maxBytes = Math.multiplyExact(maxSeconds, 48_000 * 2);
        byte[] pcm;
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")) {
            try { pcm = readWav(path, maxBytes); }
            catch (UnsupportedAudioFileException | IllegalArgumentException e) { pcm = runFfmpeg(path, maxBytes); }
        } else pcm = runFfmpeg(path, maxBytes);
        ShortBuffer src = ByteBuffer.wrap(pcm, 0, pcm.length - pcm.length % 2)
                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] out = new short[src.remaining()];
        src.get(out);
        return out;
    }

    private static byte[] readWav(Path path, int maxBytes) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    48_000f, 16, 1, 2, 48_000f, false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(fmt, input)) {
                return readLimited(decoded, maxBytes);
            }
        }
    }

    private static byte[] runFfmpeg(Path path, int maxBytes) throws Exception {
        var cfg = OffmineSimpleVoiceElTeam.get().getConfig();
        if (!cfg.getBoolean("converter.enabled", true))
            throw new IOException("FFmpeg отключен в config.yml");
        String executable = cfg.getString("converter.executable", "ffmpeg");
        int timeout = Math.max(1, cfg.getInt("converter.timeout_seconds", 60));
        ProcessBuilder pb = new ProcessBuilder(executable, "-nostdin", "-v", "error", "-i",
                path.toAbsolutePath().toString(), "-f", "s16le", "-ac", "1", "-ar", "48000", "pipe:1");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OffmineSoundAPI-ffmpeg-reader");
            t.setDaemon(true);
            return t;
        });
        Future<byte[]> output = pool.submit(() -> {
            try (InputStream in = proc.getInputStream()) { return readLimited(in, maxBytes); }
        });
        try {
            byte[] bytes = output.get(timeout, TimeUnit.SECONDS);
            if (!proc.waitFor(timeout, TimeUnit.SECONDS)) throw new IOException("Тайм-аут FFmpeg");
            if (proc.exitValue() != 0) throw new IOException("FFmpeg завершился с кодом " + proc.exitValue());
            return bytes;
        } catch (TimeoutException e) {
            throw new IOException("Тайм-аут FFmpeg (" + timeout + " с)", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw new IOException(e.getCause());
        } finally {
            proc.destroyForcibly();
            output.cancel(true);
            pool.shutdownNow();
        }
    }

    private static byte[] readLimited(InputStream input, int cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(cap, 65536));
        byte[] buf = new byte[8192];
        int read;
        while ((read = input.read(buf)) != -1) {
            if (out.size() > cap - read) throw new IOException("Звук превышает лимит длительности (audio.max_duration_seconds)");
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    public static boolean exists(Path path) { return Files.isRegularFile(path); }
}
