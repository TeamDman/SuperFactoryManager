package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPaletteCandidateSetCopyActionTests {
    private static final long CAPTURE_ID = 12L;

    @Test
    void candidateSetOffersAggregateCopyChoicesAndOmitsUnavailableCommandProjection() {
        List<SFMActionChoice> complete = SFMPaletteCandidateSetCopyAction.choices(
                CAPTURE_ID,
                List.of(inspection("First", "sfm action invoke sfm:first"), inspection("Second", null))
        );

        assertEquals(List.of(
                "Copy all candidate display texts",
                "Copy all candidate surface values",
                "Copy all canonical candidate commands",
                "Copy complete details for all candidates"
        ), complete.stream().map(SFMActionChoice::displayText).toList());
        assertEquals("sfm action invoke sfm:palette/candidates/copy 12 command", complete.get(2).command());

        assertEquals(List.of(
                "Copy all candidate display texts",
                "Copy all candidate surface values",
                "Copy complete details for all candidates"
        ), SFMPaletteCandidateSetCopyAction.choices(
                CAPTURE_ID,
                List.of(inspection("Only", null))
        ).stream().map(SFMActionChoice::displayText).toList());
    }

    @Test
    void aggregateCopyUsesImmutableCaptureAndLabelsEveryDetailsRecord() throws Exception {
        List<SFMPaletteCandidateInspection> captured = List.of(
                inspection("First", "sfm action invoke sfm:first"),
                inspection("Second", null),
                inspection("Third", "sfm action invoke sfm:third")
        );
        SFMPaletteCandidateSetCopyAction.Host host = id -> id == CAPTURE_ID
                ? Optional.of(captured)
                : Optional.empty();
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();
        SFMPaletteCandidateSetCopyAction action = new SFMPaletteCandidateSetCopyAction(clipboard::add);

        assertEquals(3, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateSetCopyAction.Projection.DISPLAY, feedback::add));
        assertEquals("First\t[itemstack=minecraft:paper; label=Paper]" + System.lineSeparator()
                        + "Second\t[itemstack=minecraft:paper; label=Paper]" + System.lineSeparator()
                        + "Third\t[itemstack=minecraft:paper; label=Paper]",
                clipboard.get(0));

        assertEquals(2, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateSetCopyAction.Projection.COMMAND, feedback::add));
        assertEquals("sfm action invoke sfm:first" + System.lineSeparator() + "sfm action invoke sfm:third",
                clipboard.get(1));

        assertEquals(3, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateSetCopyAction.Projection.DETAILS, feedback::add));
        assertTrue(clipboard.get(2).contains("candidate[0]"));
        assertTrue(clipboard.get(2).contains("candidate[1]"));
        assertTrue(clipboard.get(2).contains("candidate[2]"));
        assertEquals(3, feedback.size());
    }

    @Test
    void expiredCaptureIsRejectedInsteadOfReadingLivePaletteState() {
        SFMPaletteCandidateSetCopyAction action = new SFMPaletteCandidateSetCopyAction(ignored -> {});

        assertThrows(Exception.class, () -> action.copyProjection(
                id -> Optional.empty(),
                CAPTURE_ID,
                SFMPaletteCandidateSetCopyAction.Projection.DISPLAY,
                ignored -> {}
        ));
    }

    private static SFMPaletteCandidateInspection inspection(String display, String canonicalCommand) {
        String input = "sfm action invoke term";
        SFMPaletteCandidate candidate = SFMPaletteCandidate.activatable(
                new Suggestion(StringRange.between(18, input.length()), "sfm:panel/open sfm:terminal"),
                display,
                SFMPaletteCandidate.Kind.ARGUMENT_VALUE,
                SFMPaletteCandidate.Origin.CHOICE_SURFACE,
                new ResourceLocation("sfm", "panel/open"),
                "sfm action invoke sfm:panel/open ",
                SFMPaletteCandidate.NO_HISTORY,
                null
        );
        return SFMPaletteCandidateInspection.capture(
                candidate,
                input,
                canonicalCommand,
                "sfm:panel/open",
                "Open panel",
                "Open addressed content in the focused pane",
                "minecraft:paper",
                "Paper",
                List.of()
        );
    }
}
