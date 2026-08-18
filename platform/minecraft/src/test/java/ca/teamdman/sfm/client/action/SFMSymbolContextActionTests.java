package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDefaults;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolContextActionTests {
    private static final String OFFER_COMMAND = "sfm action invoke sfm:context/actions/open";

    @Test
    void referencesActionHasCanonicalDirectCommandAndFocusedEditorRequirement() {
        SFMFindReferencesAction action = new SFMFindReferencesAction();
        LiteralArgumentBuilder<SFMClientActionSource> builder =
                LiteralArgumentBuilder.literal(SFMFindReferencesAction.ID.toString());
        action.configureCommandNode(builder);

        assertEquals("sfm:symbol/references/open", SFMFindReferencesAction.ID.toString());
        assertNotNull(builder.build().getCommand());
        assertFalse(action.requirement().resolve(
                new SFMClientActionContext(null, () -> true, null)).isAvailable());
        assertFalse(action.requirement().resolve(
                new SFMClientActionContext(null, () -> false, null)).isAvailable());

        DocumentPanel document = new DocumentPanel();
        SFMScreenMultiplexer workspace = headlessWorkspace(document);
        assertTrue(action.requirement().resolve(SFMClientActionContext.create(workspace, () -> true)).isAvailable());

        SFMScreenMultiplexer nonEditor = headlessWorkspace(new PlainPanel());
        assertFalse(action.requirement().resolve(
                SFMClientActionContext.create(nonEditor, () -> true)).isAvailable());
    }

    @Test
    void altF7IsTheSingleTextEditorScopedDefaultForPersistentReferences() {
        List<SFMKeyBinding> bindings = SFMKeyBindingDefaults.definitions().stream()
                .filter(binding -> binding.actionId().equals(SFMFindReferencesAction.ID.toString()))
                .toList();
        assertEquals(1, bindings.size());
        SFMKeyBinding binding = bindings.get(0);
        assertEquals("sfm action invoke sfm:symbol/references/open", binding.commandDraft());
        assertEquals(SFMKeyboardUsageSituations.TEXT_EDITOR, binding.situationId());
        assertEquals(GLFW.GLFW_KEY_F7, binding.sequence().strokes().get(0).keyCode());
        assertEquals(Set.of(SFMKeyModifier.ALT), binding.sequence().strokes().get(0).modifiers());
    }

    @Test
    void altEnterAndRightClickUseTheSameOfferAndOfferOrdersDefinitionBeforeReferences()
            throws IOException {
        List<SFMKeyBinding> offerBindings = SFMKeyBindingDefaults.definitions().stream()
                .filter(binding -> binding.commandDraft().equals(OFFER_COMMAND))
                .toList();
        assertEquals(1, offerBindings.size());
        assertEquals(GLFW.GLFW_KEY_ENTER, offerBindings.get(0).sequence().strokes().get(0).keyCode());
        assertEquals(Set.of(SFMKeyModifier.ALT), offerBindings.get(0).sequence().strokes().get(0).modifiers());

        String panelSource = Files.readString(source(
                "ca/teamdman/sfm/client/screen/text_editor/SFMTextEditorPanel.java"));
        assertTrue(panelSource.contains("executeEditorAction(SFMContextActionsOpenAction.ID)"),
                "right-click must invoke the same constrained action surface as Alt+Enter");
        assertTrue(panelSource.contains("executeEditorAction(SFMJumpToDefinitionAction.ID)"),
                "Ctrl+click must invoke definition through the typed panel-action seam");

        String actionSource = Files.readString(source(
                "ca/teamdman/sfm/client/context/SFMJavaSymbolContextActionProvider.java"));
        int definition = actionSource.indexOf("SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, \"\")");
        int references = actionSource.indexOf("SFMActionChoice.invoke(SFMFindReferencesAction.ID, \"\")");
        assertTrue(definition >= 0 && references > definition,
                "the stable editor action offer must list definition before references");
    }

    @Test
    void symbolContributorRegistersBothCanonicalActionPaths() throws IOException {
        String source = Files.readString(source("ca/teamdman/sfm/client/action/SFMSymbolActions.java"));
        assertEquals(1, occurrences(source, "register(\"symbol/definition/open\""));
        assertEquals(1, occurrences(source, "register(\"symbol/references/open\""));
        assertEquals(1, occurrences(source, "register(\"context/actions/open\""));
        assertEquals(7, occurrences(source, "copy(SFMSymbolInspectionFormatters.Projection."));

        String definition = Files.readString(source(
                "ca/teamdman/sfm/client/action/SFMJumpToDefinitionAction.java"));
        assertFalse(definition.contains("literal(\"offer\")"),
                "the unreleased hard-coded definition submenu must be removed after the provider cutover");
    }

    private static Path source(String relative) {
        Path direct = Path.of("src", "main", "java").resolve(relative);
        if (Files.isRegularFile(direct)) return direct;
        Path module = Path.of("platform", "minecraft", "src", "main", "java").resolve(relative);
        if (Files.isRegularFile(module)) return module;
        throw new AssertionError("Could not locate production source " + relative);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMScreenPanel panel) {
        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            SFMScreenMultiplexer workspace = (SFMScreenMultiplexer)
                    unsafe.allocateInstance(SFMScreenMultiplexer.class);
            java.lang.reflect.Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
            unsafe.putObject(
                    workspace,
                    unsafe.objectFieldOffset(layoutField),
                    SFMWorkspaceLayout.single(panel)
            );
            return workspace;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not construct a headless workspace", failure);
        }
    }

    private static class PlainPanel implements SFMScreenPanel {
        @Override public Component title() {
            return Component.literal("plain");
        }

        @Override public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }

    private static final class DocumentPanel extends PlainPanel implements SFMTextDocumentPanelState {
        @Override public boolean isReadOnly() {
            return true;
        }

        @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
            return Optional.of(SFMTextDocumentSnapshot.literal("class A {}\n"));
        }

        @Override public boolean navigateToRange(SFMTextDocumentRange range) {
            return true;
        }
    }
}
