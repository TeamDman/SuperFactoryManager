package ca.teamdman.sfm.client.screen;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record TomlEditScreenOpenContext(
                                        String textContents,
                                        Consumer<String> saveCallback) {}
