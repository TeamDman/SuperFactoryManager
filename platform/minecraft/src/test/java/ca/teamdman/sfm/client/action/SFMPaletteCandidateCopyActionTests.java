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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPaletteCandidateCopyActionTests {
    private static final long CAPTURE_ID = 9L;
    private static final String INPUT = "sfm action invoke term";
    private static final String CANONICAL = "sfm action invoke sfm:panel/open sfm:terminal";

    @Test
    void candidateDetailsRetainEveryDisplayReplacementExecutionAndHelpProjection() {
        SFMPaletteCandidateInspection inspection = inspection();

        assertEquals("Open terminal in the focused panel", inspection.displayTextPayload());
        assertTrue(inspection.replacementSurfacePayload().contains(
                "replacement-text: \"sfm:panel/open sfm:terminal\""));
        assertTrue(inspection.replacementSurfacePayload().contains(
                "surface-text: \"sfm action invoke sfm:panel/open sfm:terminal\""));
        assertEquals(Optional.of(CANONICAL), inspection.canonicalCommandPayload());

        String details = inspection.detailsPayload();
        assertTrue(details.startsWith("schema: " + SFMPaletteCandidateInspection.SCHEMA));
        assertTrue(details.contains("display-text: \"Open terminal in the focused panel\""));
        assertTrue(details.contains("replacement-range: [18,22)"));
        assertTrue(details.contains("canonical-command: \"" + CANONICAL + "\""));
        assertTrue(details.contains("action-id: \"sfm:panel/open\""));
        assertTrue(details.contains("help-title: \"Open panel\""));
        assertTrue(details.contains("help-description: \"Open addressed content in the focused pane\""));
        assertTrue(details.contains("key-binding[0]: \"Ctrl Shift T\""));
        assertEquals("Candidate: Open terminal in the focused panel",
                inspection.accessibleDescriptionLines().get(0));
    }

    @Test
    void contextualSurfaceOffersFourExactActionBackedCopyChoices() {
        List<SFMActionChoice> choices = SFMPaletteCandidateCopyAction.choices(CAPTURE_ID, inspection());

        assertEquals(List.of(
                "Copy display text",
                "Copy replacement / surface text",
                "Copy canonical executable command",
                "Copy complete candidate details / help"
        ), choices.stream().map(SFMActionChoice::displayText).toList());
        assertEquals(List.of(
                "sfm action invoke sfm:palette/candidate/copy 9 display",
                "sfm action invoke sfm:palette/candidate/copy 9 surface",
                "sfm action invoke sfm:palette/candidate/copy 9 command",
                "sfm action invoke sfm:palette/candidate/copy 9 details"
        ), choices.stream().map(SFMActionChoice::command).toList());
        assertTrue(choices.stream().allMatch(choice -> choice.actionId().equals(
                SFMPaletteCandidateCopyAction.ID)));
    }

    @Test
    void contextualSurfaceOmitsCanonicalCommandWhenCandidateIsNotExecutable() {
        SFMPaletteCandidateInspection inspection = inspection(null);

        assertEquals(List.of(
                "Copy display text",
                "Copy replacement / surface text",
                "Copy complete candidate details / help"
        ), SFMPaletteCandidateCopyAction.choices(CAPTURE_ID, inspection).stream()
                .map(SFMActionChoice::displayText)
                .toList());
    }

    @Test
    void copyActionReadsTheImmutableCaptureAndWritesTheSelectedProjection() throws Exception {
        SFMPaletteCandidateInspection inspection = inspection();
        SFMPaletteCandidateCopyAction.Host host = id -> id == CAPTURE_ID
                ? Optional.of(inspection)
                : Optional.empty();
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();
        SFMPaletteCandidateCopyAction action = new SFMPaletteCandidateCopyAction(clipboard::add);

        assertEquals(1, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateCopyAction.Projection.DISPLAY, feedback::add));
        assertEquals(1, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateCopyAction.Projection.SURFACE, feedback::add));
        assertEquals(1, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateCopyAction.Projection.COMMAND, feedback::add));
        assertEquals(1, action.copyProjection(
                host, CAPTURE_ID, SFMPaletteCandidateCopyAction.Projection.DETAILS, feedback::add));

        assertEquals("Open terminal in the focused panel", clipboard.get(0));
        assertEquals(CANONICAL, clipboard.get(2));
        assertTrue(clipboard.get(3).contains("schema: " + SFMPaletteCandidateInspection.SCHEMA));
        assertEquals(4, feedback.size());
    }

    private static SFMPaletteCandidateInspection inspection() {
        return inspection(CANONICAL);
    }

    private static SFMPaletteCandidateInspection inspection(String canonicalCommand) {
        SFMPaletteCandidate candidate = SFMPaletteCandidate.activatable(
                new Suggestion(
                        StringRange.between(18, INPUT.length()),
                        "sfm:panel/open sfm:terminal"
                ),
                "Open terminal in the focused panel",
                SFMPaletteCandidate.Kind.ARGUMENT_VALUE,
                SFMPaletteCandidate.Origin.CHOICE_SURFACE,
                new ResourceLocation("sfm", "panel/open"),
                "sfm action invoke sfm:panel/open ",
                SFMPaletteCandidate.NO_HISTORY,
                null
        );
        return SFMPaletteCandidateInspection.capture(
                candidate,
                INPUT,
                canonicalCommand,
                "sfm:panel/open",
                "Open panel",
                "Open addressed content in the focused pane",
                "minecraft:paper",
                List.of("Ctrl Shift T")
        );
    }
}
