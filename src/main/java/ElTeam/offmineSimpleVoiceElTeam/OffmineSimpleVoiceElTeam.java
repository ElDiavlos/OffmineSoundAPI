package ElTeam.offmineSimpleVoiceElTeam;

import ElTeam.offmineSimpleVoiceElTeam.api.SoundApi;
import ElTeam.offmineSimpleVoiceElTeam.cmd.SvsCommand;
import ElTeam.offmineSimpleVoiceElTeam.library.SoundLibrary;
import ElTeam.offmineSimpleVoiceElTeam.listener.ServerEvents;
import ElTeam.offmineSimpleVoiceElTeam.placed.Beacons;
import ElTeam.offmineSimpleVoiceElTeam.playback.GlobalPlaybackManager;
import ElTeam.offmineSimpleVoiceElTeam.playback.PersonalPlaybackManager;
import ElTeam.offmineSimpleVoiceElTeam.ui.SoundAdminMenu;
import ElTeam.offmineSimpleVoiceElTeam.voice.VoiceService;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

/** Sound library and public API; radio subsystem intentionally removed. */
public final class OffmineSimpleVoiceElTeam extends JavaPlugin implements VoicechatPlugin {
    public static final String CATEGORY_ID = "osv";
    public static final String PERM = "osv.admin";

    private static OffmineSimpleVoiceElTeam instance;
    private SoundApi api;
    private SoundLibrary library;
    private SoundAdminMenu menu;
    private Beacons beacons;

    public static OffmineSimpleVoiceElTeam get() { return Objects.requireNonNull(instance, "OffmineSoundAPI не запущен"); }
    public SoundApi sounds() { return api; }
    public SoundLibrary library() { return library; }
    public SoundAdminMenu menu() { return menu; }
    public Beacons beacons() { return beacons; }
    public java.util.logging.Logger logger() { return getLogger(); }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        File dir = new File(getDataFolder(), "sounds");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать каталог sounds");
        library = new SoundLibrary(this, dir);
        library.reload();
        beacons = new Beacons(dir);
        api = new SoundApi(this);
        menu = new SoundAdminMenu(this);
        Bukkit.getServicesManager().register(SoundApi.class, api, this, ServicePriority.Normal);
        SvsCommand commands = new SvsCommand(this);
        Objects.requireNonNull(getCommand("osv"), "Не зарегистрирована команда osv").setExecutor(commands);
        getCommand("osv").setTabCompleter(commands);
        Objects.requireNonNull(getCommand("soundadmin"), "Не зарегистрирована команда soundadmin").setExecutor(commands);
        getServer().getPluginManager().registerEvents(new ServerEvents(), this);
        getServer().getPluginManager().registerEvents(menu, this);
        BukkitVoicechatService svc = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (svc == null) {
            getLogger().severe("Simple Voice Chat не найден. Проверьте depend: [voicechat]");
        } else {
            svc.registerPlugin(this);
        }
        getLogger().info("OffmineSoundAPI включен. Звуков: " + library.ids().size());
    }

    @Override
    public void onDisable() {
        if (menu != null) menu.shutdown();
        if (api != null) api.shutdown();
        try { if (beacons != null) { beacons.saveToConfig(); beacons.registry().stopAll(); } }
        catch (Throwable t) { getLogger().warning("Маяки: " + t.getMessage()); }
        try { PersonalPlaybackManager.get().snapshotAll(); PersonalPlaybackManager.get().saveToConfig(); }
        catch (Throwable t) { getLogger().warning("Персональные звуки: " + t.getMessage()); }
        try { GlobalPlaybackManager.get().saveToConfig(); }
        catch (Throwable t) { getLogger().warning("Глобальные звуки: " + t.getMessage()); }
        GlobalPlaybackManager.get().stopAll();
        PersonalPlaybackManager.get().clearAll();
        VoiceService.get().shutdown();
        Bukkit.getServicesManager().unregisterAll(this);
        instance = null;
    }

    @Override public String getPluginId() { return "offmine-sound-api"; }
    @Override public void initialize(VoicechatApi api) { }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
            VoiceService voice = VoiceService.get();
            voice.attach(e.getVoicechat());
            if (voice.ensureVolumeCategory()) {
                getLogger().info("Категория громкости Simple Voice Chat зарегистрирована: "
                        + CATEGORY_ID + " (Звуки сервера)");
            } else {
                getLogger().warning("Не удалось зарегистрировать категорию громкости: "
                        + voice.volumeCategoryStatus());
            }
            try { beacons.loadFromConfig(); }
            catch (Throwable t) { getLogger().warning("Маяки: " + t.getMessage()); }
            try { GlobalPlaybackManager.get().loadFromConfig(); }
            catch (Throwable t) { getLogger().warning("Глобальные звуки: " + t.getMessage()); }
            try { PersonalPlaybackManager.get().loadFromConfig(); }
            catch (Throwable t) { getLogger().warning("Персональные звуки: " + t.getMessage()); }
            getLogger().info("Simple Voice Chat готов");
        });
        // A category registered on the server must be synced to both current and future clients.
        // If voice chat was temporarily unavailable during startup, retry when a client connects.
        registration.registerEvent(PlayerConnectedEvent.class, e -> {
            VoiceService voice = VoiceService.get();
            if (!voice.isVolumeCategoryRegistered() && voice.ensureVolumeCategory()) {
                getLogger().info("Категория громкости " + CATEGORY_ID
                        + " восстановлена при подключении Voice Chat клиента");
            }
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            if (api != null) {
                if (Bukkit.isPrimaryThread()) api.stopAllAt();
                else {
                    try { Bukkit.getScheduler().runTask(this, () -> api.stopAllAt()); }
                    catch (IllegalStateException ignored) { /* Server is shutting down. */ }
                }
            }
            VoiceService.get().detach();
        });
    }
}
