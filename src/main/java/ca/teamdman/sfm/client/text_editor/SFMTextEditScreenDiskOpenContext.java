package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.github.bsideup.jabel.Desugar;

import java.util.function.Consumer;

@Desugar
public record SFMTextEditScreenDiskOpenContext(
        String initialValue,
        LabelPositionHolder labelPositionHolder,
        Consumer<String> saveWriter
) implements ISFMTextEditScreenOpenContext {
}
