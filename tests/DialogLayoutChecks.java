import java.nio.file.Files;
import java.nio.file.Path;

/** Source-order smoke check; does not simulate the actual Minecraft dialog renderer. */
public class DialogLayoutChecks {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/ElTeam/offmineSimpleVoiceElTeam/ui/SoundAdminMenu.java"));
        String section = source.substring(source.indexOf("public void open(Player p, int requestedPage)"),
                source.indexOf("private void openUpload("));
        int counts = section.indexOf("Звуков:");
        int soundEntries = section.indexOf("Component entry =");
        int hint = section.indexOf("Нажми на название звука");
        int buttons = section.indexOf("List<ActionButton> actions");
        if (!(0 <= counts && counts < soundEntries && soundEntries < hint && hint < buttons)) {
            throw new AssertionError("Expected: counters -> sound rows -> explanation -> footer buttons");
        }
        if (!section.contains("ClickEvent.custom(") || !section.contains("sound/")) {
            throw new AssertionError("Sound entries must be clickable without command confirmation");
        }
        if (section.contains("actions.add(ActionButton.create(")) {
            throw new AssertionError("Sound entries must not be footer action buttons");
        }
        System.out.println("PASS: body order and custom-click sound rows");
    }
}
