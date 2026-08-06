package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenCatalog;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalResponse;
import ca.teamdman.sfm.client.terminal.SFMTerminalService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelActionTests {
    private static final ResourceLocation TEST_SCENE = new ResourceLocation("sfm", "test_scene");

    @Test
    void duplicateCreatesIndependentPanelIdentityScaleAndLifecycle() {
        SFMPanelReopenRecipe recipe = new TrackingRecipe(TEST_SCENE, "copy");
        TrackingPanel original = (TrackingPanel) recipe.reopen();
        SFMPanelReopenCatalog catalog = new SFMPanelReopenCatalog();
        catalog.register(original, recipe);
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(original);
        SFMWorkspacePanelId originalId = layout.focusedPanel();
        assertTrue(layout.setGuiScale(originalId, 3));

        SFMPanelReopenCatalog.ReopenedPanel reopened = catalog.reopen(original).orElseThrow();
        TrackingPanel duplicate = (TrackingPanel) reopened.panel();
        SFMWorkspacePanelId duplicateId = layout.insert(
                originalId,
                SFMWorkspaceSide.RIGHT,
                duplicate,
                layout.metadata(originalId));
        catalog.register(duplicate, reopened.recipe());

        assertNotEquals(originalId, duplicateId);
        assertNotSame(original, duplicate);
        assertEquals(3, layout.metadata(originalId).guiScaleOverride());
        assertEquals(3, layout.metadata(duplicateId).guiScaleOverride());
        assertTrue(layout.setGuiScale(duplicateId, 5));
        assertEquals(3, layout.metadata(originalId).guiScaleOverride());
        assertEquals(5, layout.metadata(duplicateId).guiScaleOverride());

        assertTrue(layout.remove(duplicateId));
        catalog.remove(duplicate);
        duplicate.closed();
        assertEquals(0, original.closeCount);
        assertEquals(1, duplicate.closeCount);
        assertEquals(originalId, layout.focusedPanel());
    }

    @Test
    void panelsWithoutTypedRecipeAreExplicitlyUnsupported() {
        TrackingPanel unsupported = new TrackingPanel("unsupported");
        SFMPanelReopenCatalog catalog = new SFMPanelReopenCatalog();

        assertFalse(catalog.contains(unsupported));
        assertTrue(catalog.reopen(unsupported).isEmpty());
    }

    @Test
    void terminalRecipeOpensDistinctSessions() {
        AtomicInteger openedSessions = new AtomicInteger();
        SFMTerminalService service = () -> {
            openedSessions.incrementAndGet();
            return new SFMTerminalService.SFMTerminalSession() {
                @Override
                public SFMTerminalResponse execute(String command) {
                    return SFMTerminalResponse.ok(java.util.List.of(), "/");
                }

                @Override
                public String workingDirectory() {
                    return "/";
                }
            };
        };
        SFMPanelReopenRecipe recipe = new TestTerminalRecipe(
                new ResourceLocation("sfm", "terminal"),
                service);
        SFMTerminalPanel original = (SFMTerminalPanel) recipe.reopen();
        SFMPanelReopenCatalog catalog = new SFMPanelReopenCatalog();
        catalog.register(original, recipe);

        SFMTerminalPanel duplicate = (SFMTerminalPanel) catalog.reopen(original).orElseThrow().panel();

        assertEquals(2, openedSessions.get());
        assertNotSame(original, duplicate);
    }

    @Test
    void recipeCatalogRejectsMutablePanelAliasing() {
        TrackingPanel panel = new TrackingPanel("aliased");
        SFMPanelReopenCatalog catalog = new SFMPanelReopenCatalog();
        catalog.register(panel, new AliasingRecipe(TEST_SCENE, panel));

        assertThrows(IllegalStateException.class, () -> catalog.reopen(panel));
    }

    private static final class TrackingPanel implements SFMScreenPanel {
        private final String name;
        private int closeCount;

        private TrackingPanel(String name) {
            this.name = name;
        }

        @Override
        public Component title() {
            return Component.literal(name);
        }

        @Override
        public void closed() {
            closeCount++;
        }

        @Override
        public void render(
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

    private record TrackingRecipe(
            ResourceLocation sceneTypeId,
            String name
    ) implements SFMPanelReopenRecipe {
        @Override
        public SFMScreenPanel reopen() {
            return new TrackingPanel(name);
        }
    }

    private record TestTerminalRecipe(
            ResourceLocation sceneTypeId,
            SFMTerminalService service
    ) implements SFMPanelReopenRecipe {
        @Override
        public SFMScreenPanel reopen() {
            return new SFMTerminalPanel(service);
        }
    }

    private record AliasingRecipe(
            ResourceLocation sceneTypeId,
            SFMScreenPanel panel
    ) implements SFMPanelReopenRecipe {
        @Override
        public SFMScreenPanel reopen() {
            return panel;
        }
    }
}
