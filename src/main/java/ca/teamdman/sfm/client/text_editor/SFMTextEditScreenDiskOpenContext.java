package ca.teamdman.sfm.client.text_editor;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

@Desugar
public record SFMTextEditScreenDiskOpenContext(
                                               String initialValue,
                                               LabelPositionHolder labelPositionHolder,
                                               Consumer<String> saveWriter)
        implements ISFMTextEditScreenOpenContext {}
