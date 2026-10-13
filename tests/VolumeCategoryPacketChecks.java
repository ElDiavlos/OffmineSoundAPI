import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline tests with Simple Voice Chat API stubs, not a Minecraft client integration test. */
public final class VolumeCategoryPacketChecks {
    private static VolumeCategory category(String id, String name, String desc) {
        return (VolumeCategory) Proxy.newProxyInstance(
                VolumeCategory.class.getClassLoader(), new Class<?>[]{VolumeCategory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "getDescription" -> desc;
                    default -> null;
                });
    }

    private static void check(boolean result, String error) {
        if (!result) throw new AssertionError(error);
    }

    public static void main(String[] args) {
        String name = VoiceService.VOLUME_CATEGORY_NAME;
        String description = VoiceService.VOLUME_CATEGORY_DESCRIPTION;
        check(name.getBytes(StandardCharsets.UTF_8).length <= 16, "Name longer than 16 UTF-8 bytes");
        check(description.getBytes(StandardCharsets.UTF_8).length <= 16, "Description longer than 16 UTF-8 bytes");
        check("Звуки сервера".getBytes(StandardCharsets.UTF_8).length == 25, "Old bug not reproduced");

        List<VolumeCategory> categories = new ArrayList<>();
        AtomicInteger registrations = new AtomicInteger();
        String[] values = new String[3];
        VolumeCategory.Builder builder = (VolumeCategory.Builder) Proxy.newProxyInstance(
                VolumeCategory.Builder.class.getClassLoader(),
                new Class<?>[]{VolumeCategory.Builder.class},
                (proxy, method, methodArgs) -> switch (method.getName()) {
                    case "setId" -> { values[0] = (String) methodArgs[0]; yield proxy; }
                    case "setName" -> { values[1] = (String) methodArgs[0]; yield proxy; }
                    case "setDescription" -> { values[2] = (String) methodArgs[0]; yield proxy; }
                    case "build" -> category(values[0], values[1], values[2]);
                    default -> proxy;
                });
        VoicechatServerApi api = (VoicechatServerApi) Proxy.newProxyInstance(
                VoicechatServerApi.class.getClassLoader(), new Class<?>[]{VoicechatServerApi.class},
                (proxy, method, methodArgs) -> switch (method.getName()) {
                    case "volumeCategoryBuilder" -> builder;
                    case "getVolumeCategories" -> categories;
                    case "registerVolumeCategory" -> {
                        registrations.incrementAndGet();
                        VolumeCategory incoming = (VolumeCategory) methodArgs[0];
                        categories.removeIf(c -> incoming.getId().equals(c.getId()));
                        categories.add(incoming);
                        yield null;
                    }
                    default -> null;
                });
        VoiceService voice = VoiceService.get();
        voice.attach(api);
        check(voice.ensureVolumeCategory(), "Registration failed");
        check(registrations.get() == 1, "Missing registration");
        check("osv".equals(categories.getFirst().getId()), "ID changed");
        check(name.equals(categories.getFirst().getName()), "Name mismatch");
        check(description.equals(categories.getFirst().getDescription()), "Description mismatch");
        check(voice.isVolumeCategoryRegistered(), "Missing category in registry");
        check(voice.ensureVolumeCategory() && registrations.get() == 1, "Duplicate registration");

        // Simulate old 2.3.2 category remaining in registry until hot-reload.
        categories.clear();
        categories.add(category("osv", "Звуки сервера", "Громкость звуков OffmineSoundAPI"));
        check(!voice.isVolumeCategoryRegistered(), "Old invalid category accepted");
        check(voice.ensureVolumeCategory() && registrations.get() == 2, "Old name was not replaced");
        check(name.equals(categories.getFirst().getName()), "Stale name not fixed");
        voice.detach();
        check(!voice.isVolumeCategoryRegistered(), "Voice API should be detached");
        System.out.println("PASS: UTF-8 limits, registration, stale category replacement, API detach");
    }
}
