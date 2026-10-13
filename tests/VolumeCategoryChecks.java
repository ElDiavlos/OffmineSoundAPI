import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit-like verification against stubbed SVC API; no real client is involved. */
public final class VolumeCategoryChecks {
    public static void main(String[] args) {
        List<VolumeCategory> categories = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        VolumeCategory category = (VolumeCategory) Proxy.newProxyInstance(
                VolumeCategory.class.getClassLoader(), new Class<?>[]{VolumeCategory.class},
                (proxy, method, ignored) -> switch (method.getName()) {
                    case "getId" -> "osv";
                    case "getName" -> VoiceService.VOLUME_CATEGORY_NAME;
                    case "getDescription" -> VoiceService.VOLUME_CATEGORY_DESCRIPTION;
                    default -> null;
                });
        VolumeCategory.Builder builder = (VolumeCategory.Builder) Proxy.newProxyInstance(
                VolumeCategory.Builder.class.getClassLoader(),
                new Class<?>[]{VolumeCategory.Builder.class},
                (proxy, method, ignored) -> switch (method.getName()) {
                    case "build" -> category;
                    default -> proxy;
                });
        VoicechatServerApi api = (VoicechatServerApi) Proxy.newProxyInstance(
                VoicechatServerApi.class.getClassLoader(), new Class<?>[]{VoicechatServerApi.class},
                (proxy, method, methodArgs) -> switch (method.getName()) {
                    case "volumeCategoryBuilder" -> builder;
                    case "getVolumeCategories" -> categories;
                    case "registerVolumeCategory" -> {
                        attempts.incrementAndGet();
                        categories.add((VolumeCategory) methodArgs[0]);
                        yield null;
                    }
                    default -> null;
                });
        VoiceService voice = VoiceService.get();
        voice.attach(api);
        if (voice.isVolumeCategoryRegistered()) throw new AssertionError("Unexpected category");
        if (!voice.ensureVolumeCategory() || !voice.isVolumeCategoryRegistered()) throw new AssertionError("Category not registered");
        if (!voice.ensureVolumeCategory() || attempts.get() != 1) throw new AssertionError("Duplicate registration");
        categories.clear();
        if (!voice.ensureVolumeCategory() || attempts.get() != 2) throw new AssertionError("Recovery failed");
        if (!voice.volumeCategoryStatus().contains("зарегистрирована")) throw new AssertionError("Wrong diagnosis");
        voice.detach();
        if (voice.isVolumeCategoryRegistered()) throw new AssertionError("Category remains after stop");
        System.out.println("Volume category checks passed: register / idempotent / recovery / detach");
    }
}
