package ca.teamdman.sfm.client.screen;

import com.github.bsideup.jabel.Desugar;

import java.util.function.Consumer;

@Desugar
public record TomlEditScreenOpenContext(
        String textContents,
        Consumer<String> saveCallback
) {
}
