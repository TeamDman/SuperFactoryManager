package ca.teamdman.sfm.client.control;

import java.util.Locale;
import java.util.Map;

/** Startup defaults only. Runtime changes are deliberate palette/control actions. */
public record SFMActionServiceSettings(boolean files, boolean vox) {
    public static final String FILES_ENV = "SFM_ACTION_FILES";
    public static final String VOX_ENV = "SFM_ACTION_VOX";

    public static SFMActionServiceSettings fromEnvironment(Map<String, String> environment) {
        return new SFMActionServiceSettings(enabled(environment, FILES_ENV), enabled(environment, VOX_ENV));
    }

    private static boolean enabled(Map<String, String> environment, String name) {
        String value = environment.getOrDefault(name, "").trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "0", "false" -> false;
            case "1", "true" -> true;
            default -> throw new IllegalArgumentException(name + " must be true, false, 1 or 0");
        };
    }
}
