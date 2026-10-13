// Minimal offline self-checks. No remote HTTP request is made and no server is required.
import ElTeam.offmineSimpleVoiceElTeam.library.SoundDownloader;
import ElTeam.offmineSimpleVoiceElTeam.listener.ServerEvents;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OfflineChecks {
    private static void eq(String actual, String expected) {
        if (!actual.equals(expected)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void yes(boolean condition) {if(!condition) throw new AssertionError("Check failed");}
    private static void no(boolean condition) {yes(!condition);}
    private static void kind(String prefix, String expected) {
        byte[] bytes = new byte[64];
        System.arraycopy(prefix.getBytes(StandardCharsets.US_ASCII),0, bytes, 0, prefix.length());
        eq(SoundDownloader.detectAudioType(bytes),expected);
    }
    public static void main(String[] args) throws Exception {
        // GitHub file views become public direct raw content URLs.
        eq(SoundDownloader.normalizeLink("https://github.com/demo/sounds/blob/main/effects/alarm.mp3").toString(),
           "https://raw.githubusercontent.com/demo/sounds/main/effects/alarm.mp3");
        // Dropbox retains the access key and switches to a download response.
        eq(SoundDownloader.normalizeLink("https://www.dropbox.com/scl/fi/abc123/alarm.mp3?rlkey=ZZZ&dl=0").toString(),
           "https://www.dropbox.com/scl/fi/abc123/alarm.mp3?rlkey=ZZZ&dl=1");
        eq(SoundDownloader.normalizeLink("https://www.dropbox.com/s/abc123/alarm.mp3").toString(),
           "https://www.dropbox.com/s/abc123/alarm.mp3?dl=1");
        eq(SoundDownloader.normalizeLink("https://example.org/a.mp3").toString(),
           "https://example.org/a.mp3");
        kind("ID3", "mp3");
        kind("fLaC", "flac");
        kind("OggS", "ogg");
        byte[] wav = new byte[64];
        System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
        eq(SoundDownloader.detectAudioType(wav), "wav");
        byte[] m4a = new byte[64];
        System.arraycopy("ftyp".getBytes(), 0, m4a, 4, 4);
        eq(SoundDownloader.detectAudioType(m4a), "m4a");
        byte[] mp3 = {(byte)0xFF, (byte)0xFB, 0x12, 0x34};
        eq(SoundDownloader.detectAudioType(mp3), "mp3");
        byte[] lowRateMp3 = {(byte)0xFF, (byte)0xE3, 0x12, 0x34};
        eq(SoundDownloader.detectAudioType(lowRateMp3), "mp3");
        byte[] aac = {(byte)0xFF, (byte)0xF1, 0x12, 0x34};
        eq(SoundDownloader.detectAudioType(aac), "aac");
        yes(SoundDownloader.detectAudioType("<html>Error 403</html>".getBytes()) == null);
        yes(SoundDownloader.detectAudioType("{\"error\":\"no\"}".getBytes()) == null);
        yes(ServerEvents.isAdminCommand("osv"));
        yes(ServerEvents.isAdminCommand("soundadmin"));
        yes(ServerEvents.isAdminCommand("OffmineSoundAPI:osv"));
        yes(ServerEvents.isAdminCommand("offminesoundapi:soundadmin"));
        no(ServerEvents.isAdminCommand("tell"));
        no(ServerEvents.isAdminCommand("minecraft:kill"));
        System.out.println("17 offline checks passed");
    }
}
