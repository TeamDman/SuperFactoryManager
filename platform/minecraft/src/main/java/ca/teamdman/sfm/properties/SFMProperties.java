package ca.teamdman.sfm.properties;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The single access point for JVM system properties understood by SFM.
 */
public final class SFMProperties {
    private static final String CLIENT_RUN_MODE_PROPERTY = "sfm.clientRun.mode";
    private static final String CLIENT_RUN_KEEP_OPEN_SECONDS_PROPERTY = "sfm.clientRun.keepOpenSeconds";
    private static final String CLIENT_RUN_TITLE_EXIT_SECONDS_PROPERTY = "sfm.clientRun.titleExitSeconds";
    private static final String CLIENT_RUN_TITLE_SCREEN_PROPERTY = "sfm.clientRun.titleScreen";
    private static final String CLIENT_RUN_OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY = "sfm.clientRun.openTextEditorOnTitleScreen";
    private static final String GAME_TEST_SELECTION_PROPERTY = "sfm.gametestSelection";
    private static final String GAME_TEST_MAX_PROGRAM_RUN_MILLIS_PROPERTY = "sfm.gametest.maxProgramRunMillis";
    private static final String GAME_PUPPET_SELECTION_PROPERTY = "sfm.gamePuppetSelection";
    private static final String GAME_PUPPET_GAME_TEST_PROPERTY = "sfm.gamePuppet.gameTest";
    private static final String GAME_PUPPET_VIEWPORT_SELECTION_PROPERTY = "sfm.gamePuppet.viewportSelection";
    private static final String USER_DIRECTORY_PROPERTY = "user.dir";

    private SFMProperties() {
    }

    public static ClientRunMode clientRunMode() {
        return ClientRunMode.fromPropertyValue(property(CLIENT_RUN_MODE_PROPERTY));
    }

    public static int clientRunKeepOpenSeconds(int defaultValue) {
        return integerProperty(CLIENT_RUN_KEEP_OPEN_SECONDS_PROPERTY, defaultValue);
    }

    public static int clientRunTitleExitSeconds(int defaultValue) {
        return integerProperty(CLIENT_RUN_TITLE_EXIT_SECONDS_PROPERTY, defaultValue);
    }

    public static Optional<SFMTitleScreenDevScreen> clientRunTitleScreen() {
        String screenId = property(CLIENT_RUN_TITLE_SCREEN_PROPERTY);
        if (screenId.isEmpty() && booleanProperty(CLIENT_RUN_OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY)) {
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
        return longProperty(GAME_TEST_MAX_PROGRAM_RUN_MILLIS_PROPERTY, defaultValue);
    }

    public static String gamePuppetSelection() {
        return property(GAME_PUPPET_SELECTION_PROPERTY);
    }

    public static String gamePuppetViewportSelection() {
        return property(GAME_PUPPET_VIEWPORT_SELECTION_PROPERTY);
    }

    /**
     * Gets the exact SFM GameTest selected as input for a parameterized game puppet.
     */
    public static SFMGameTestId requiredGamePuppetGameTest() {
        return SFMGameTestId.fromPropertyValue(requiredProperty(GAME_PUPPET_GAME_TEST_PROPERTY));
    }

    public static Path userDirectory() {
        return Path.of(requiredProperty(USER_DIRECTORY_PROPERTY));
    }

    private static boolean booleanProperty(String name) {
        return Boolean.parseBoolean(property(name));
    }

    private static int integerProperty(String name, int defaultValue) {
        String value = property(name);
        return value.isEmpty() ? defaultValue : Integer.parseInt(value);
    }

    private static long longProperty(String name, long defaultValue) {
        String value = property(name);
        return value.isEmpty() ? defaultValue : Long.parseLong(value);
    }

    private static String requiredProperty(String name) {
        String value = property(name);
        if (value.isEmpty()) {
            throw new IllegalStateException("Required JVM property is blank: " + name);
        }
        return value;
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

    /**
     * Canonical, unqualified identifier for an SFM GameTest.
     */
    public record SFMGameTestId(String value) {
        public SFMGameTestId {
            if (value.isBlank()) {
                throw new IllegalArgumentException("SFM GameTest id must not be blank");
            }
            if (value.indexOf(':') >= 0) {
                throw new IllegalArgumentException("SFM GameTest id must use the sfm namespace: " + value);
            }
            if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf(',') >= 0) {
                throw new IllegalArgumentException("SFM GameTest id must be exact: " + value);
            }
        }

        static SFMGameTestId fromPropertyValue(String value) {
            String normalized = value.startsWith("sfm:") ? value.substring("sfm:".length()) : value;
            return new SFMGameTestId(normalized);
        }
    }
}
