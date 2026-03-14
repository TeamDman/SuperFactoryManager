package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.common.label.LabelPositionHolder;

import java.util.function.Consumer;

public record SFMTextEditScreenOpenContext(
        String initialValue,

        LabelPositionHolder labelPositionHolder,

        TextEditScreenContentLanguage contentLanguage,

        Consumer<String> saveWriter

) implements ISFMTextEditScreenOpenContext {
    public SFMTextEditScreenOpenContext(
            String content,
            TextEditScreenContentLanguage language
    ) {

        this(
                content,
                LabelPositionHolder.empty(),
                language,
                (unusedNewText) -> {
                }
        );
    }

    public SFMTextEditScreenOpenContext(
            String content,
            TextEditScreenContentLanguage language,
            Consumer<String> saveWriter
    ) {

        this(
                content,
                LabelPositionHolder.empty(),
                language,
                saveWriter
        );
    }

}
