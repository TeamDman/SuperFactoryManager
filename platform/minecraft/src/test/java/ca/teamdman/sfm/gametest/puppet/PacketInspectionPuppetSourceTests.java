package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Guard evidence boundaries; real input/render acceptance belongs to the opt-in file puppet. */
class PacketInspectionPuppetSourceTests {
    @Test void hoverUsesVanillaRenderingAfterTheVirtualCallback() throws Exception {
        String action = action();
        assertTrue(action.contains("new ContainerScreen(ChestMenu.threeRows("));
        assertTrue(action.contains("SFMGamePuppetPointer.moveVirtual(fixture, targetX, targetY)"));
        assertTrue(action.contains("SFMGamePuppetRenderHarness.completedFrames() <= frame + 2"));
        assertTrue(action.contains("fixture.getSlotUnderMouse() =="));
        assertTrue(action.contains("callbackIsWithin(targetX, targetY, 1D)"));
        assertFalse(action.contains(".hoveredSlot ="));
        assertTrue(action.contains("runtime.captureWithHud"));
        String pointer = source("src/gametest/java/ca/teamdman/sfm/gametest/puppet/SFMGamePuppetPointer.java");
        assertTrue(pointer.contains("moveVirtual(Screen screen, double logicalX, double logicalY)"));
        assertTrue(pointer.contains("sfm$invokeOnMove"));
    }

    @Test void altDUsesRawIngressAndCannotBypassTheInspectionAction() throws Exception {
        String action = action();
        assertTrue(action.contains("try { rawKey(GLFW.GLFW_KEY_D, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_ALT); }"));
        assertTrue(action.contains("finally { rawKey(GLFW.GLFW_KEY_D, GLFW.GLFW_RELEASE, GLFW.GLFW_MOD_ALT); }"));
        assertTrue(action.contains("sfm$invokeKeyPress("));
        assertFalse(action.contains("captureHovered("));
        assertFalse(action.contains("recipeFor("));
        assertFalse(action.contains("SFMItemInspectionDocument.capture("));
        assertFalse(action.contains("executeCommandPalette("));
    }

    @Test void acceptanceRequiresSnapshotOrdinaryAndWritablePositiveControl() throws Exception {
        String action = action();
        assertTrue(action.contains("\"hover_packet\", \"inspect_packet\", \"prove_snapshot\""));
        assertTrue(action.contains("\"hover_ordinary\", \"inspect_ordinary\", \"hover_empty\", \"inspect_empty\", \"finish\""));
        assertTrue(action.contains("OPERATIONS.get(completed).equals(operation)"));
        assertTrue(action.contains("require(failedRequests == 0"));
        assertTrue(action.contains("TagParser.parseTag("));
        assertTrue(action.contains("SFMValueJsonCodec.encodePretty(PACKET_VALUE)"));
        assertTrue(action.contains("mutated-after-capture"));
        assertTrue(action.contains("editor().currentText().equals(capturedPacketText)"));
        assertTrue(action.contains("editor.currentText().equals(\"x\")"),
                "Unchanged read-only text is not evidence unless the same input path edits the blank fallback");
        assertTrue(action.contains("editorId().equals(\"sfm:text_editor_v3\")"));
        assertTrue(action.contains("addProperty(\"physical_shift_proven\", false)"));
        assertFalse(action.contains("GLFW_KEY_LEFT_SHIFT"));
        assertFalse(action.contains("appendTooltipLines("));
    }

    @Test void fixtureIsOptInBoundedAndRestoresOnlyItsOwnedState() throws Exception {
        String action = action();
        assertTrue(action.contains("minecraft.player.containerMenu == minecraft.player.inventoryMenu"));
        assertFalse(action.matches("(?s).*containerMenu\\s*=\\s*[^=].*"));
        assertFalse(action.contains("fixture.onClose("));
        assertTrue(action.contains("minecraft.screen == ownedWorkspace"));
        assertTrue(action.contains("minecraft.screen == fixture"));
        assertTrue(action.contains("preferredEditor.set(previousEditor)"));
        assertTrue(action.contains("void abort() { cleanup(); }"));
        assertTrue(action.contains("readNBytes(1025)"));
        assertTrue(action.contains("value.size() != 1"));
        assertTrue(action.contains("20_000_000_000L"));
        String definition = source("src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/InWorldPacketInspectionGamePuppet.java");
        assertTrue(definition.contains("@SFMGamePuppet("));
        assertFalse(definition.contains("extends SFMGameTestDefinition"));
        assertTrue(definition.contains("puppet.explorePacketInspectionInteractively()"));
    }

    private static String action() throws Exception {
        return source("src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/ExplorePacketInspectionPuppetAction.java");
    }

    private static String source(String relative) throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/gametest/java"))) return Files.readString(current.resolve(relative));
            Path nested = current.resolve("platform/minecraft");
            if (Files.isDirectory(nested.resolve("src/gametest/java"))) return Files.readString(nested.resolve(relative));
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Minecraft project root");
    }
}
