package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.screen.theme_settings.SFMItemstackPreviewDraftPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SFMItemstackPreviewDraftTests {
    @Test public void draftAndAllControlsUseOneTypedRequestWithoutMutatingActiveTheme() {
        var evidence=SFMItemstackPreviewFixtures.inspection("abc.json");var baseline=SFMClientThemeService.active();
        var request=new SFMItemstackPreviewRuleAction.Request(evidence.theme().orElseThrow(),SFMItemstackPreviewExpression.call("sfm:entry/is_file"),new ResourceLocation("minecraft:bell"));
        var draft=new SFMItemstackPreviewDraft(request,baseline,SFMItemstackPreviewRegistry.snapshot(),Optional.of(evidence.subject()));
        assertEquals(request.rule(),draft.previewTheme().previewRules().get(draft.previewTheme().previewRules().size()-1));
        assertSame(baseline,SFMClientThemeService.active());assertEquals(request.rule(),draft.decision().orElseThrow().winner().orElseThrow());
        var panel=new SFMItemstackPreviewDraftPanel(draft);assertEquals(request.command(),panel.commandFor(0));
        assertTrue(panel.commandFor(1).startsWith(SFMItemstackPreviewRuleToolsAction.Kind.PICK.prefix()));
        assertTrue(panel.commandFor(2).endsWith("minecraft:bell"));assertFalse(panel.activate(0));panel.closed();assertFalse(panel.activate(0));
        for(int width:List.of(140,300,960)) {
            var bounds=new SFMScreenPanelBounds(11,17,width,190);var controls=SFMItemstackPreviewDraftPanel.controls(bounds);
            assertEquals(4,controls.size());for(var cell:controls) {assertTrue(bounds.contains(cell.x(),cell.y()));assertTrue(bounds.contains(cell.x()+cell.width()-1,cell.y()+cell.height()-1));}
        }
    }
    @Test public void unknownItemsAndStaleTargetsFailBeforeAnyWriteAndResetIsUserOnly() {
        var evidence=SFMItemstackPreviewFixtures.inspection("abc.json");var request=new SFMItemstackPreviewRuleAction.Request(evidence.theme().orElseThrow(),SFMItemstackPreviewExpression.call("sfm:entry/is_file"),new ResourceLocation("example:not_installed"));
        assertThrows(IllegalArgumentException.class,()->SFMItemstackPreviewRuleAction.validateRequest(request,id->false,evidence.theme()));
        assertThrows(IllegalArgumentException.class,()->SFMItemstackPreviewRuleAction.validateRequest(request,id->true,Optional.empty()));
        assertDoesNotThrow(()->SFMItemstackPreviewRuleAction.validateRequest(request,id->true,evidence.theme()));
        var rule=request.rule();assertTrue(SFMItemstackPreviewRuleToolsAction.resetRules(List.of(rule),rule.id()).isEmpty());
        var inherited=new SFMItemstackPreviewRules.Rule(rule.id(),SFMItemstackPreviewRules.Layer.MOD,rule.predicate(),rule.icon());
        assertThrows(IllegalArgumentException.class,()->SFMItemstackPreviewRuleToolsAction.resetRules(List.of(inherited),rule.id()));
    }
    @Test public void continuationInheritanceKeepsImmutableRowAndDoesNotResampleFocus() {
        var original=new SFMClientActionContext(new Object(),()->true,null);var next=new SFMClientActionContext(original.originatingHost(),()->true,null);
        var captured=SFMItemstackPreviewFixtures.inspection("abc.json");SFMItemstackPreviewCaptures.retain(original,captured);SFMItemstackPreviewCaptures.inherit(original,next);
        SFMItemstackPreviewCaptures.retain(original,SFMItemstackPreviewFixtures.inspection("different.json"));
        assertSame(captured,SFMItemstackPreviewCaptures.forContext(next).orElseThrow().inspection());
    }
}
