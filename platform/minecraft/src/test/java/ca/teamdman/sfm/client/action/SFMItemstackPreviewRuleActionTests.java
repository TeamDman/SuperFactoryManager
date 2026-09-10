package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.screen.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SFMItemstackPreviewRuleActionTests {
    @Test public void genericAndEveryContextPredicateRoundTripThroughActualBrigadierBoundary() {
        var action=new SFMItemstackPreviewRuleAction();
        var dispatcher=new CommandDispatcher<SFMClientActionSource>();
        dispatcher.register(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm")
                .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("action")
                        .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("invoke")
                                .then(action.createCommandNode(SFMItemstackPreviewRuleAction.ID)))));
        var inspection=SFMItemstackPreviewFixtures.inspection("abc.json");
        var context=new SFMClientActionContext(new Object(),()->true,null);
        var choices=SFMItemstackPreviewRuleAction.contextualPredicates(inspection.subject());
        assertTrue(choices.stream().anyMatch(value->value.description().contains("\"a\"")));
        assertTrue(choices.stream().anyMatch(value->value.description().contains("\"ab\"")));
        for(var choice:choices) {
            var request=new SFMItemstackPreviewRuleAction.Request(inspection.theme().orElseThrow(),choice.predicate(),new ResourceLocation("minecraft:bell"));
            var parsed=dispatcher.parse(request.command(),new SFMClientActionSource(context));
            assertFalse(parsed.getReader().canRead(),request.command());
            assertNotNull(parsed.getContext().getCommand());
            assertEquals(request,SFMItemstackPreviewRuleAction.request(parsed.getContext().build(request.command())));
            String incomplete=request.command().substring(0,request.command().lastIndexOf(' '));
            assertNull(dispatcher.parse(incomplete,new SFMClientActionSource(context)).getContext().getCommand());
        }
    }
    @Test public void capturedContextAndThemeRemainFixedWhileEarlierPredicateEditsRetainItem() {
        var action=new SFMItemstackPreviewRuleAction(); var context=new SFMClientActionContext(new Object(),()->true,null);
        var inspection=SFMItemstackPreviewFixtures.inspection("abc.json");
        SFMItemstackPreviewCaptures.retain(context,inspection);
        var continuations=action.contextualContinuations(context);
        assertTrue(continuations.stream().allMatch(value->value.arguments().startsWith(inspection.theme().orElseThrow().argument())));
        String command=SFMItemstackPreviewRuleAction.PREFIX+" "+inspection.theme().orElseThrow().argument()+" sfm:string/ends_with sfm:entry/name \".j\" minecraft:bell";
        int cursor=command.indexOf(".j")+2;
        var candidates=action.argumentCandidates(command,SFMItemstackPreviewRuleAction.PREFIX.length()+1,cursor,context).orElseThrow();
        var suffix=candidates.stream().filter(value->value.replacementText().equals("\".json\"")).findFirst().orElseThrow();
        assertTrue(suffix.apply(command).afterValue().endsWith("\".json\" minecraft:bell"));
        assertTrue(candidates.stream().anyMatch(value->value.kind()==SFMPaletteCandidate.Kind.USAGE_HINT && value.displayText().contains("STRING")));
        assertTrue(SFMItemstackPreviewCaptures.forContext(new SFMClientActionContext(new Object(),()->true,null)).isEmpty());
    }
    @Test public void iconTargetsHaveFlatAspectsWhileTextKeepsItsOwnActions() {
        var inspection=SFMItemstackPreviewFixtures.inspection("abc.json"); var context=new SFMClientActionContext(new Object(),()->true,null);
        var entry=SFMExplorerEntry.simple(inspection.row().rowAddress(),"abc.json",false,Optional.empty());
        var registry=new SFMExplorerContextActionRegistry(List.of());
        for(var target:SFMExplorerContextActionProvider.Target.values()) {
            var choices=registry.resolve(new SFMExplorerContextActionProvider.Request(context,inspection.row().explorerId(),entry.path(),entry,inspection.row(),target,Optional.of(inspection)));
            assertEquals(1,choices.stream().filter(choice->choice.actionId().equals(SFMExplorerRowCopyDetailsAction.ID)).count());
            if(target==SFMExplorerContextActionProvider.Target.ROW) assertEquals(2,choices.size());
            else {
                for(var kind:SFMItemstackPreviewInspectionAction.Kind.values()) assertTrue(choices.stream().anyMatch(choice->choice.actionId().equals(kind.id())));
                assertTrue(choices.stream().filter(choice->choice.actionId().equals(SFMItemstackPreviewRuleAction.ID)).allMatch(ca.teamdman.sfm.client.screen.SFMActionChoice::continuation));
            }
        }
        assertFalse(inspection.renderingExplanation().contains("Rect["));
        assertTrue(inspection.renderingExplanation().contains("chests CAN render"));
        assertTrue(inspection.boundsPayload().contains("Rect["));
        assertTrue(inspection.cachePayload().contains("no persistent"));
    }
    @Test public void iconHitRegionIsHalfOpenAndIndependentFromTextAcrossViews() {
        var entry=SFMExplorerEntry.simple(SFMItemstackPreviewFixtures.inspection("abc.json").subject().path(),"abc.json",false,Optional.empty());
        var row=new SFMExplorerProjection.Row(entry.path(),entry,1,false,false,entry.sortKey(SFMExplorerEntry.SORT_NAME));
        for(var view:SFMExplorerProjection.View.values()) {
            var cell=SFMExplorerPanelViewport.calculate(new SFMScreenPanelBounds(13,19,320,200),view,List.of(row),0).cells().get(0);
            var icon=SFMExplorerPanelViewport.iconBounds(cell,view);
            assertTrue(icon.contains(icon.x(),icon.y())); assertFalse(icon.contains(icon.x()+icon.width(),icon.y()));
            assertFalse(icon.contains(cell.bounds().x()+cell.bounds().width()-5,icon.y()));
        }
    }
    @Test public void detailsOnlyExportUsesExactlyTheSharedInspectionPayload() throws Exception {
        var inspection=SFMItemstackPreviewFixtures.inspection("a\"\\\n😀.json");
        var choice=SFMExplorerRowCopyDetailsAction.captureChoice(inspection);
        long id=Long.parseLong(choice.command().substring(choice.command().lastIndexOf(' ')+1));
        var clipboard=new ArrayList<String>();
        new SFMExplorerRowCopyDetailsAction(clipboard::add).copyCapture(id,ignored->{});
        assertEquals(List.of(inspection.detailsPayload()),clipboard);
        assertTrue(clipboard.get(0).startsWith(inspection.row().detailsPayload()));
        assertTrue(clipboard.get(0).contains("subject.name: "+SFMItemstackPreviewExpression.quote(inspection.subject().name())));
    }
}
