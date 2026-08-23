package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMHeadlessWorkspaceTestSupport;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMRevealHereActionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath FIRST = SFMPath.parse("registry://minecraft/item/minecraft/first");
    private static final SFMPath SECOND = SFMPath.parse("registry://minecraft/item/minecraft/second");

    @Test
    void captureUsesNewestStillCurrentAddressedDocumentAndExactDestinationExplorer() {
        DocumentPanel first = new DocumentPanel("first", snapshot(FIRST, ROOT));
        DocumentPanel second = new DocumentPanel("second", snapshot(SECOND, ROOT));
        SFMExplorerPanel explorer = explorer();
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(
                        0,
                        SFMWorkspaceLayout.panel(first),
                        SFMWorkspaceLayout.panel(second)
                ),
                SFMWorkspaceLayout.panel(explorer)
        ));
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId firstId = id(layout, first);
        SFMWorkspacePanelId secondId = id(layout, second);
        SFMWorkspacePanelId explorerId = id(layout, explorer);

        assertTrue(workspace.focusPanel(firstId));
        assertTrue(workspace.focusPanel(secondId));
        assertTrue(workspace.focusPanel(explorerId));
        SFMClientActionContext context = new SFMClientActionContext(workspace, () -> true, explorerId);

        SFMClientActionAvailability<SFMRevealHereAction.Target> availability =
                SFMRevealHereAction.capture(context);

        assertTrue(availability.isAvailable());
        SFMRevealHereAction.Target target = availability.target();
        assertEquals(explorerId, target.explorerPanelId());
        assertEquals(secondId, target.documentPanelId());
        assertEquals(SECOND, target.documentSnapshot().path().orElseThrow());
        assertEquals(ROOT, target.directContainingRoot().orElseThrow());
        assertTrue(target.stillCurrent());
    }

    @Test
    void captureFailsClosedForNoDocumentUnauthorizedPathAndStaleHost() {
        SFMExplorerPanel explorer = explorer();
        SFMWorkspaceLayout explorerOnly = SFMWorkspaceLayout.single(explorer);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(explorerOnly);
        SFMWorkspacePanelId explorerId = id(explorerOnly, explorer);
        assertFalse(SFMRevealHereAction.capture(new SFMClientActionContext(
                workspace, () -> true, explorerId)).isAvailable());
        assertFalse(SFMRevealHereAction.capture(new SFMClientActionContext(
                workspace, () -> false, explorerId)).isAvailable());

        SFMPath foreignRoot = SFMPath.parse("registry://minecraft/block/");
        SFMPath foreignPath = SFMPath.parse("registry://minecraft/block/minecraft/stone");
        DocumentPanel foreign = new DocumentPanel("foreign", snapshot(foreignPath, foreignRoot));
        explorer = explorer();
        SFMWorkspaceLayout incompatible = SFMWorkspaceLayout.sideBySide(foreign, explorer);
        workspace = SFMHeadlessWorkspaceTestSupport.create(incompatible);
        SFMWorkspacePanelId foreignId = id(incompatible, foreign);
        explorerId = id(incompatible, explorer);
        assertTrue(workspace.focusPanel(foreignId));
        assertTrue(workspace.focusPanel(explorerId));

        SFMClientActionAvailability<SFMRevealHereAction.Target> unavailable =
                SFMRevealHereAction.capture(new SFMClientActionContext(workspace, () -> true, explorerId));
        assertFalse(unavailable.isAvailable());
        assertTrue(unavailable.unavailableReason().getString().contains("not authorized"));
    }

    @Test
    void semanticControlFailsWithItsExactReasonInsteadOfABrigadierParseArtifact() {
        SFMExplorerPanel explorer = explorer();
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(explorer);
        SFMScreenMultiplexer workspace = SFMHeadlessWorkspaceTestSupport.create(layout);
        SFMWorkspacePanelId explorerId = id(layout, explorer);
        SFMClientActionContext context = new SFMClientActionContext(workspace, () -> true, explorerId);
        ArrayList<Component> feedback = new ArrayList<>();

        assertFalse(SFMRevealHereAction.isControlVisible(context));
        assertFalse(SFMRevealHereAction.invokeFromControl(context, feedback::add));
        assertEquals(1, feedback.size());
        assertTrue(feedback.get(0).getString().contains("No recently focused addressed document"));
        assertFalse(feedback.get(0).getString().contains("Incorrect argument"));
    }

    private static SFMExplorerPanel explorer() {
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        new SFMInMemoryRegistryExplorerResolver.Node(
                                SFMExplorerEntry.simple(ROOT, "Items", true, Optional.of("minecraft:chest")),
                                List.of(FIRST, SECOND)
                        ),
                        new SFMInMemoryRegistryExplorerResolver.Node(
                                SFMExplorerEntry.simple(FIRST, "First", false, Optional.of("minecraft:paper")),
                                List.of()
                        ),
                        new SFMInMemoryRegistryExplorerResolver.Node(
                                SFMExplorerEntry.simple(SECOND, "Second", false, Optional.of("minecraft:paper")),
                                List.of()
                        )
                ),
                Runnable::run,
                32
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 32).completion().join();
        return new SFMExplorerPanel(
                new SFMExplorerSession(new SFMExplorerId("reveal-here"), ROOT, new SFMSelectionRepository()),
                loader,
                ignored -> { }
        );
    }

    private static SFMTextDocumentSnapshot snapshot(SFMPath path, SFMPath root) {
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                "document",
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.of("sha256:test"),
                OptionalLong.of(8),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.NONE),
                Optional.empty(),
                List.of()
        );
    }

    private static SFMWorkspacePanelId id(SFMWorkspaceLayout layout, SFMScreenPanel panel) {
        return layout.panels().stream()
                .filter(entry -> entry.panel() == panel)
                .findFirst().orElseThrow().id();
    }

    private record DocumentPanel(String name, SFMTextDocumentSnapshot snapshot)
            implements SFMScreenPanel, SFMTextDocumentPanelState {
        @Override public Component title() {
            return Component.literal(name);
        }

        @Override public boolean isReadOnly() {
            return snapshot.readOnly();
        }

        @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
            return Optional.of(snapshot);
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
}
