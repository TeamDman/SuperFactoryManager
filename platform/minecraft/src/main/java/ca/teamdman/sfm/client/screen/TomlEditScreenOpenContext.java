package ca.teamdman.sfm.client.screen;

import java.util.function.Consumer;

public record TomlEditScreenOpenContext(
        String textContents,
        Consumer<String> saveCallback
) {
}
