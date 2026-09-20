package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSurfaceDetailsActionTests {
    @Test
    void multilineDetailsRemainExactExecutableArguments() {
        String payload = "schema: sfm.review-surface-ui-region/1\n"
                + "region-kind: diagnostic\n"
                + "message: Unsupported language \\\"rust\\\" at C:\\\\work\\\\cli.rs\n";

        List<SFMActionChoice> choices = SFMReleaseReviewSurfaceDetailsAction.choices(
                "generated diff diagnostic", payload);

        assertEquals(List.of(
                "Copy generated diff diagnostic details",
                "Open generated diff diagnostic details as text"
        ), choices.stream().map(SFMActionChoice::displayText).toList());
        assertTrue(choices.stream().allMatch(choice -> choice.command().startsWith(
                "sfm action invoke sfm:review/surface/details ")));
        assertTrue(executable(choices.get(0)));
        assertTrue(executable(choices.get(1)));
    }

    private static boolean executable(SFMActionChoice choice) {
        String prefix = "sfm action invoke sfm:review/surface/details ";
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("surface");
        new SFMReleaseReviewSurfaceDetailsAction().configureCommandNode(node);
        dispatcher.register(node);
        ParseResults<SFMClientActionSource> parsed = dispatcher.parse(
                "surface " + choice.command().substring(prefix.length()),
                new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null))
        );
        return SFMClientActionExecutor.isExecutable(parsed);
    }
}
