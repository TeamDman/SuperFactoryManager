package ca.teamdman.sfm.client.screen;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One exact registered client-action invocation exposed by a constrained palette. */
public record SFMActionChoice(ResourceLocation actionId, String command, String displayText) {
    private static final String ACTION_PREFIX = "sfm action invoke ";

    public SFMActionChoice(ResourceLocation actionId, String command) {
        this(actionId, command, defaultDisplayText(command));
    }

    public SFMActionChoice {
        Objects.requireNonNull(actionId);
        command = Objects.requireNonNull(command).strip();
        displayText = Objects.requireNonNull(displayText).strip();
        if (displayText.isEmpty()) throw new IllegalArgumentException("Choice display text must not be empty");
        String expectedPrefix = ACTION_PREFIX + actionId;
        if (!command.equals(expectedPrefix) && !command.startsWith(expectedPrefix + " ")) {
            throw new IllegalArgumentException(
                    "Choice command must invoke its declared action " + actionId + ": " + command);
        }
    }

    public static SFMActionChoice invoke(ResourceLocation actionId, String arguments) {
        String suffix = Objects.requireNonNull(arguments).strip();
        return new SFMActionChoice(
                actionId,
                ACTION_PREFIX + actionId + (suffix.isEmpty() ? "" : " " + suffix));
    }

    public static SFMActionChoice invoke(
            ResourceLocation actionId,
            String arguments,
            String displayText
    ) {
        String suffix = Objects.requireNonNull(arguments).strip();
        return new SFMActionChoice(
                actionId,
                ACTION_PREFIX + actionId + (suffix.isEmpty() ? "" : " " + suffix),
                displayText
        );
    }

    private static String defaultDisplayText(String command) {
        String canonical = Objects.requireNonNull(command).strip();
        return canonical.startsWith(ACTION_PREFIX)
                ? canonical.substring(ACTION_PREFIX.length())
                : canonical;
    }
}
