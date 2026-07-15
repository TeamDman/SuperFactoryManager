package ca.teamdman.sfm;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The single access point for JVM system properties understood by SFM.
 */
public final class SFMProperties {
    private static final String CLIENT_RUN_MODE_PROPERTY = "sfm.clientRun.mode";
    private static final String CLIENT_RUN_KEEP_OPEN_SECONDS_PROPERTY = "sfm.clientRun.keepOpenSeconds";
    private static final String CLIENT_RUN_TITLE_SCREEN_PROPERTY = "sfm.clientRun.titleScreen";
    private static final String CLIENT_RUN_OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY = "sfm.clientRun.openTextEditorOnTitleScreen";
    private static final String GAME_TEST_SELECTION_PROPERTY = "sfm.gametestSelection";
    private static final String GAME_TEST_MAX_PROGRAM_RUN_MILLIS_PROPERTY = "sfm.gametest.maxProgramRunMillis";
    private static final String GAME_PUPPET_SELECTION_PROPERTY = "sfm.gamePuppetSelection";
    private static final String USER_DIRECTORY_PROPERTY = "user.dir";

    private SFMProperties() {
    }

    public static ClientRunMode clientRunMode() {
        return ClientRunMode.fromPropertyValue(property(CLIENT_RUN_MODE_PROPERTY));
    }

    public static int clientRunKeepOpenSeconds(int defaultValue) {
        return Integer.getInteger(CLIENT_RUN_KEEP_OPEN_SECONDS_PROPERTY, defaultValue);
    }

    public static Optional<SFMTitleScreenDevScreen> clientRunTitleScreen() {
        String screenId = property(CLIENT_RUN_TITLE_SCREEN_PROPERTY);
        if (screenId.isEmpty() && Boolean.getBoolean(CLIENT_RUN_OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY)) {
            return Optional.of(SFMTitleScreenDevScreen.TEXT_EDITOR);
        }
        if (screenId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(SFMTitleScreenDevScreen.byId(screenId)
                                   .orElseThrow(() -> new IllegalStateException(
                                           "Unsupported SFM title screen launch screen: " + screenId
                                   )));
    }

    public static String gameTestSelection() {
        return property(GAME_TEST_SELECTION_PROPERTY);
    }

    public static long gameTestMaxProgramRunMillis(long defaultValue) {
        return Long.getLong(GAME_TEST_MAX_PROGRAM_RUN_MILLIS_PROPERTY, defaultValue);
    }

    public static String gamePuppetSelection() {
        return property(GAME_PUPPET_SELECTION_PROPERTY);
    }

    public static Path userDirectory() {
        return Path.of(System.getProperty(USER_DIRECTORY_PROPERTY));
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }

    public enum ClientRunMode {
        NONE(""),
        SMOKE("smoke"),
        PUPPET("puppet"),
        GAME_PUPPET("game-puppet");

        private final String propertyValue;

        ClientRunMode(String propertyValue) {
            this.propertyValue = propertyValue;
        }

        public static ClientRunMode fromPropertyValue(String value) {
            for (ClientRunMode mode : values()) {
                if (mode.propertyValue.equals(value)) {
                    return mode;
                }
            }
            return NONE;
        }

        public boolean isPuppet() {
            return this == PUPPET || this == GAME_PUPPET;
        }
    }
}
