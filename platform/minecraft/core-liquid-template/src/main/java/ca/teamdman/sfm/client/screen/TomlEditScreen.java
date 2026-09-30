package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;

public class TomlEditScreen extends SFMTextEditScreenV1 {
    public TomlEditScreen(
            TomlEditScreenOpenContext openContext
    ) {
        super(new SFMTextEditScreenDiskOpenContext(
                openContext.textContents(),
                LabelPositionHolder.empty(),
                openContext.saveCallback()
        ));
    }
}
