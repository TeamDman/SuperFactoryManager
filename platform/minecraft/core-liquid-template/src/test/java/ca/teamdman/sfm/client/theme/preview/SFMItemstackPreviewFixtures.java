package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.explorer.*;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import java.nio.file.Path;
import java.util.*;

public final class SFMItemstackPreviewFixtures {
    private SFMItemstackPreviewFixtures() {}
    public static SFMItemstackPreviewInspection inspection(String name) {
        SFMPath path=SFMPath.parse("file:///C:/fixtures/abc.json");
        var row=new SFMExplorerRowInspection(new SFMExplorerId("explorer-7"),path,name,SFMExplorerProjection.RowKind.ENTRY,
                SFMExplorerProjection.FilterRole.NONE,1,false,false,false,true,true,"body",SFMExplorerProjection.Settings.defaults(),
                3,4,0,Optional.empty(),List.of(),List.of());
        var subject=SFMItemstackPreviewSubject.of(path,"file",SFMItemstackPreviewSubject.Kind.FILE,name,Map.of("known","value"));
        var theme=new SFMItemstackPreviewThemeTarget(Path.of("C:/fixtures/theme.toml"),"a".repeat(64));
        return new SFMItemstackPreviewInspection(row,subject,Optional.of(theme),
                new SFMItemstackPreviewRules.Decision(SFMItemstackPreviewRules.Status.NO_MATCH,Optional.empty(),List.of(),List.of()),
                "test:provider",Optional.of(SFMItemIcon.vanilla("paper","file")),
                Optional.of(new SFMItemstackPreviewInspection.Rendered("minecraft:paper",false,false,"requested-item-available")),
                Optional.of(new SFMExplorerPanelViewport.Rect(2,3,16,16)));
    }
}
