package ca.teamdman.sfm.client.action;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerFinderActionGrammarTests {
    @Test void explicitMatchOptionsHaveACompleteCommandPathWithoutChangingLegacySyntax() {
        var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(new ResourceLocation("sfm", "explorer/find/match"), new SFMExplorerAction(SFMExplorerAction.Operation.FIND_MATCH)),
                Map.entry(new ResourceLocation("sfm", "explorer/filter/match"), new SFMExplorerAction(SFMExplorerAction.Operation.FILTER_MATCH)),
                Map.entry(new ResourceLocation("sfm", "explorer/filter/set"), new SFMExplorerAction(SFMExplorerAction.Operation.FILTER_SET))));
        var source = new SFMClientActionSource(SFMClientActionContext.create(new Object(), () -> true));
        for (String mode : List.of("literal", "fuzzy", "regex")) {
            for (String lane : List.of("find", "filter")) {
                String prefix = "sfm action invoke sfm:explorer/" + lane + "/match focused " + mode;
                assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(prefix + " false true false MyFile.java", source)));
                assertFalse(SFMClientActionExecutor.isExecutable(tree.parse(prefix + " false true false", source)));
                assertFalse(SFMClientActionExecutor.isExecutable(tree.parse(prefix + " false", source)));
            }
        }
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse("sfm action invoke sfm:explorer/filter/set focused stne", source)));
        assertTrue(new ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest.FilterSet("stne").options()
                .equals(ca.teamdman.sfm.client.search.SFMTextMatchOptions.legacyFuzzy()));
        String spaced = ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelActions.matchQuery(
                new ca.teamdman.sfm.client.explorer.SFMExplorerId("test"), false, " ^[a-z]+$ ",
                ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults().toggleRegex());
        var parsed = tree.parse(spaced, source);
        assertTrue(SFMClientActionExecutor.isExecutable(parsed));
        org.junit.jupiter.api.Assertions.assertEquals(" ^[a-z]+$ ",
                parsed.getContext().build(spaced).getArgument("query", String.class));
    }

    @Test
    public void hierarchicalFinderActionsRequireAnExplicitExplorerSelector() {
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(
                        new ResourceLocation("sfm", "explorer/find/set"),
                        new SFMExplorerAction(SFMExplorerAction.Operation.FIND_SET)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "explorer/find/next"),
                        new SFMExplorerAction(SFMExplorerAction.Operation.FIND_NEXT)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "explorer/find/previous"),
                        new SFMExplorerAction(SFMExplorerAction.Operation.FIND_PREVIOUS)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "explorer/find/clear"),
                        new SFMExplorerAction(SFMExplorerAction.Operation.FIND_CLEAR)
                )
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(new Object(), () -> true)
        );

        String set = "sfm action invoke sfm:explorer/find/set focused java source";
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(set, source)));
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/next focused",
                source
        )));
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/previous all",
                source
        )));
        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/clear focused",
                source
        )));
        assertFalse(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/next",
                source
        )));
        assertFalse(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/set focused",
                source
        )));

        assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(
                "sfm action invoke sfm:explorer/find/set id(explorer%20one) exact match",
                source
        )), "exact identity selectors remain fully explicit and grammar-addressable");
    }
}
