package ElTeam.offmineSimpleVoiceElTeam.library;

import ElTeam.offmineSimpleVoiceElTeam.OffmineSimpleVoiceElTeam;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** HTTP(S) downloads on a Bukkit asynchronous task only. No localhost/LAN endpoints. */
public final class SoundDownloader {
    private final OffmineSimpleVoiceElTeam plugin;
    public SoundDownloader(OffmineSimpleVoiceElTeam plugin) { this.plugin = plugin; }

    public String download(String id, String link) throws IOException {
        if (!SoundLibrary.validId(id)) throw new IOException("ID: только латинские a-z, цифры, '_' и '-', до 48 знаков");
        if (plugin.library().contains(id)) throw new IOException("ID уже занят: " + id);
        long maxBytes = Math.max(1, plugin.getConfig().getInt("download.max_size_mb", 20)) * 1024L * 1024L;
        int timeout = Math.max(3, plugin.getConfig().getInt("download.timeout_seconds", 25));
        URL url = normalizeLink(link);
        HttpURLConnection conn = null;
        Path temp = null;
        try {
            for (int attempt = 0; attempt < 8; attempt++) {
                validatePublic(url);
                URLConnection raw = url.openConnection(Proxy.NO_PROXY);
                if (!(raw instanceof HttpURLConnection c)) throw new IOException("Поддерживаются только HTTP/HTTPS");
                conn = c;
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(timeout * 1000);
                conn.setReadTimeout(timeout * 1000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; OffmineSoundAPI/2.2; sound importer)");
                conn.setRequestProperty("Accept", "audio/*,application/octet-stream,*/*;q=0.6");
                int status = conn.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IOException("Редирект без адреса");
                    URL next = new URL(url, location);
                    conn.disconnect();
                    conn = null;
                    url = next;
                    continue;
                }
                if (status != 200) throw new IOException(httpExplanation(status, url));
                String mediaType = Optional.ofNullable(conn.getContentType()).orElse("").toLowerCase(Locale.ROOT);
                if (mediaType.startsWith("text/") || mediaType.contains("html") || mediaType.contains("json"))
                    throw new IOException("Ссылка вернула веб-страницу вместо аудио (Content-Type: " + mediaType + "). Нужна прямая ссылка на файл, а не страницу скачивания.");
                if (conn.getContentLengthLong() > maxBytes) throw new IOException("Файл больше " + (maxBytes / 1048576) + " МБ");
                temp = Files.createTempFile(plugin.library().directory().toPath(), ".download-", ".tmp");
                long total = 0;
                String ext;
                try (InputStream network = conn.getInputStream();
                     BufferedInputStream in = new BufferedInputStream(network);
                     OutputStream out = Files.newOutputStream(temp)) {
                    // Hosting services sometimes use application/octet-stream or no file extension.
                    // Detect the actual media bytes; never save HTML/error pages as audio.
                    in.mark(128);
                    byte[] header = in.readNBytes(64);
                    in.reset();
                    ext = detectAudioType(header);
                    if (ext == null) {
                        throw new IOException("Сервер вернул нераспознанные данные вместо аудио. "
                                + "Проверьте прямую ссылку и формат файла (MP3/WAV/OGG/FLAC/AAC/M4A/OPUS).");
                    }
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > maxBytes) throw new IOException("Превышен лимит " + (maxBytes / 1048576) + " МБ");
                        out.write(buf, 0, n);
                    }
                }
                if (total == 0) throw new IOException("Пустой файл");
                String fileName = id + "." + ext;
                Path target = plugin.library().directory().toPath().resolve(fileName);
                if (Files.exists(target)) throw new IOException("Файл уже существует: " + fileName);
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, target); }
                temp = null;
                // Library registration must happen on the main server thread (handled by caller).
                return fileName;
            }
            throw new IOException("Слишком много перенаправлений");
        } finally {
            if (conn != null) conn.disconnect();
            if (temp != null) Files.deleteIfExists(temp);
        }
    }

    /** HTTP errors depend on the remote server. Display an actionable explanation, not just a number. */
    public static String httpExplanation(int code, URL url) {
        String shortUrl = url.getProtocol() + "://" + url.getHost() + url.getPath(); // omit signed query tokens
        if (shortUrl.length() > 160) shortUrl = shortUrl.substring(0, 157) + "...";
        return switch (code) {
            case 404 -> "HTTP 404 — файл не найден: " + shortUrl
                    + ". Откройте ссылку в браузере. Нужен прямой адрес существующего файла, не страница сайта.";
            case 403 -> "HTTP 403 — сайт запрещает скачивание с сервера: " + shortUrl
                    + ". Загрузите файл на хостинг с открытыми прямыми ссылками.";
            case 401 -> "HTTP 401 — сайт требует вход или авторизацию. Нужна публичная прямая ссылка.";
            case 429 -> "HTTP 429 — сайт временно ограничил число запросов. Попробуйте позже.";
            case 500, 502, 503, 504 -> "HTTP " + code + " — ошибка удалённого сайта. Попробуйте позже.";
            default -> "HTTP " + code + " — сервер не выдал аудиофайл: " + shortUrl;
        };
    }

    /** Supported public provider links; never bypasses host/IP validation after redirects. */
    public static URL normalizeLink(String input) throws IOException {
        final URI uri;
        try { uri = URI.create(input.trim()); }
        catch (Exception e) { throw new IOException("Некорректная ссылка: нужен https://адрес-файла", e); }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("Разрешены только публичные ссылки http:// или https:// без авторизации");
        }
        String h = host.toLowerCase(Locale.ROOT);
        try {
            // GitHub's "blob" view is HTML. raw.githubusercontent.com is the raw file.
            if (h.equals("github.com") || h.equals("www.github.com")) {
                String path = uri.getRawPath();
                String[] parts = path == null ? new String[0] : path.split("/", 6);
                if (parts.length >= 6 && parts[3].equals("blob")
                        && !parts[1].isBlank() && !parts[2].isBlank() && !parts[4].isBlank()) {
                    return URI.create("https://raw.githubusercontent.com/" + parts[1] + "/" + parts[2]
                            + "/" + parts[4] + "/" + parts[5]).toURL();
                }
                throw new IOException("Для GitHub укажите ссылку на конкретный файл: "
                        + "github.com/владелец/репозиторий/blob/ветка/путь/звук.mp3");
            }
            // Dropbox's documented dl=1 forces a downloadable response, keeping rlkey.
            if ((h.equals("www.dropbox.com") || h.equals("dropbox.com")) && uri.getRawPath() != null
                    && (uri.getRawPath().startsWith("/s/") || uri.getRawPath().startsWith("/scl/fi/"))) {
                String query = uri.getRawQuery();
                String newQuery;
                if (query == null || query.isBlank()) newQuery = "dl=1";
                else if (Pattern.compile("(^|&)dl=").matcher(query).find()) {
                    newQuery = query.replaceAll("(^|&)dl=[^&]*", "$1dl=1");
                } else newQuery = query + "&dl=1";
                return URI.create(uri.getScheme() + "://" + uri.getRawAuthority()
                        + uri.getRawPath() + "?" + newQuery).toURL();
            }
            return uri.toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException("Некорректная ссылка на аудиофайл", e);
        }
    }

    /** Classifies binary audio independently of the URL extension and Content-Type. */
    public static String detectAudioType(byte[] data) {
        if (data == null || data.length < 4) return null;
        if (ascii(data, 0, "RIFF") && ascii(data, 8, "WAVE")) return "wav";
        if (ascii(data, 0, "fLaC")) return "flac";
        if (ascii(data, 0, "OggS")) {
            return ascii(data, 28, "OpusHead") ? "opus" : "ogg";
        }
        if (ascii(data, 0, "ID3")) return "mp3";
        if (ascii(data, 4, "ftyp")) return "m4a";
        if (ascii(data, 0, "OpusHead")) return "opus";
        // MPEG Layer III vs AAC ADTS sync words.
        int first = Byte.toUnsignedInt(data[0]), second = Byte.toUnsignedInt(data[1]);
        if (first == 0xFF && (second & 0xE0) == 0xE0) {
            int layer = (second >> 1) & 0x03;
            if (layer == 0) return "aac";
            if (layer == 1) return "mp3";
        }
        return null;
    }

    private static boolean ascii(byte[] data, int index, String magic) {
        if (index + magic.length() > data.length) return false;
        for (int i = 0; i < magic.length(); i++) {
            if (data[index + i] != (byte) magic.charAt(i)) return false;
        }
        return true;
    }

    private static void validatePublic(URL url) throws IOException {
        if (!("https".equalsIgnoreCase(url.getProtocol()) || "http".equalsIgnoreCase(url.getProtocol())))
            throw new IOException("Разрешены только https:// и http://");
        if (URI.create(url.toString()).getRawUserInfo() != null || url.getHost() == null || url.getHost().isBlank())
            throw new IOException("Ссылка содержит некорректный адрес или учетные данные");
        // Prevent reaching private, loopback, and link-local destinations through the admin downloader.
        InetAddress[] all = InetAddress.getAllByName(url.getHost());
        for (InetAddress ip : all) {
            if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isSiteLocalAddress()
                    || ip.isLinkLocalAddress() || ip.isMulticastAddress())
                throw new IOException("Ссылки на локальные и частные адреса запрещены");
            byte[] bytes = ip.getAddress();
            if (bytes.length == 4) {
                int a = Byte.toUnsignedInt(bytes[0]), b = Byte.toUnsignedInt(bytes[1]);
                if (a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && b == 254
                        || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168
                        || a == 100 && b >= 64 && b <= 127 || a == 198 && (b == 18 || b == 19))
                    throw new IOException("Ссылка ведёт на служебный IP");
            } else if (bytes.length == 16 && (bytes[0] & (byte) 0xfe) == (byte) 0xfc) {
                throw new IOException("Приватные IPv6 запрещены");
            }
        }
    }
}
