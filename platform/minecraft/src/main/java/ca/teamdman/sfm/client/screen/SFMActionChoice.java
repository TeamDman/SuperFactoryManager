package ca.teamdman.sfm.client.screen;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One exact registered client-action invocation shown by a bounded chooser. */
public record SFMActionChoice(ResourceLocation actionId, String command) {
    public SFMActionChoice {
        Objects.requireNonNull(actionId);
        command = Objects.requireNonNull(command).strip();
        String expectedPrefix = "sfm action invoke " + actionId;
        if (!command.equals(expectedPrefix) && !command.startsWith(expectedPrefix + " ")) {
            throw new IllegalArgumentException(
                    "Choice command must invoke its declared action " + actionId + ": " + command);
        }
    }

    public static SFMActionChoice invoke(ResourceLocation actionId, String arguments) {
        String suffix = Objects.requireNonNull(arguments).strip();
        return new SFMActionChoice(
                actionId,
                "sfm action invoke " + actionId + (suffix.isEmpty() ? "" : " " + suffix));
    }
}
