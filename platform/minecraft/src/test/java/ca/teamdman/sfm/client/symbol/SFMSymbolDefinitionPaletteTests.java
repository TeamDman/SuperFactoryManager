package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.text_editor.SFMDeferredTextEditorPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolDefinitionPaletteTests {
    private static final ResourceLocation TEST_EDITOR_ID =
            new ResourceLocation("sfm", "standalone_test_editor");

    @Test
    void choicesAreStableLabelledExactAndOneShot() throws Exception {
        SFMDefinitionChoiceSessionService service = new SFMDefinitionChoiceSessionService();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(7);
        SFMDefinitionResult.Definition first = SFMJumpToDefinitionActionTests.definition(
                "a.Target", "a/Target.java", 6, "Target");
        SFMDefinitionResult.Definition second = SFMJumpToDefinitionActionTests.definition(
                "b.Target", "b/Target.java", 6, "Target");

        SFMDefinitionChoiceSessionService.ChoiceSet choices = service.create(
                SFMJumpToDefinitionAction.ID,
                workspace,
                panelId,
                hello(),
                List.of(second, first, first)
        );
        assertEquals(2, choices.choices().size());
        assertTrue(choices.choices().get(0).command().contains("a.Target@a/Target.java:1:7"));
        assertTrue(choices.choices().get(1).command().contains("b.Target@b/Target.java:1:7"));
        assertEquals(1, service.activeSessionCount());

        String[] tail = choices.choices().get(0).command().split(" ");
        SFMClientActionContext matching = new SFMClientActionContext(
                workspace, () -> true, panelId);
        assertTrue(service.consume(
                choices.sessionId(), 0, tail[tail.length - 1], matching).isPresent());
        assertFalse(service.consume(
                choices.sessionId(), 0, tail[tail.length - 1], matching).isPresent());
        assertEquals(0, service.activeSessionCount());
    }

    @Test
    void existingImmutableTargetIsFocusedAtExactRangeWithoutOpeningOrReplacing() {
        SFMPath root = SFMPath.parse("file:///D:/workspace/src");
        SFMPath target = SFMPath.parse("file:///D:/workspace/src/Target.java");
        String text = "class Target {}\n";
        SFMDefinitionResult.Definition definition = SFMJumpToDefinitionActionTests.definition(
                "example.Target", "Target.java", 6, "Target");
        SFMTextDocumentRange range = SFMDefinitionNavigation.range(definition.identifierSpan());
        TrackingDocumentPanel document = new TrackingDocumentPanel(snapshot(text, target, root));
        SFMWorkspacePanelId targetPanelId = new SFMWorkspacePanelId(3);
        TrackingWorkspace workspace = new TrackingWorkspace(targetPanelId, document);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                new SFMWorkspacePanelId(1),
                hello(),
                definition
        );

        assertEquals(SFMDefinitionNavigation.Status.FOCUSED_EXISTING, result.status());
        assertEquals(targetPanelId, result.panelId());
        assertEquals(range, document.navigatedRange);
        assertEquals(Optional.of(range), document.documentSnapshot().orElseThrow().targetRange());
        assertEquals(targetPanelId, workspace.focused);
        assertFalse(workspace.openedInSourceStack,
                "navigation must not replace or add a panel when the immutable target is already open");
    }

    @Test
    void existingDeferredEditorAtomicallyPublishesTheNewExactRangeWithoutOpeningDuplicate() throws Exception {
        SFMPath root = SFMPath.parse("file:///D:/workspace/src");
        SFMPath target = SFMPath.parse("file:///D:/workspace/src/Target.java");
        String text = "class Target {}\n";
        SFMTextDocumentRange originalRange = new SFMTextDocumentRange(
                SFMTextDocumentRange.positionAtByteOffset(text, 0),
                SFMTextDocumentRange.positionAtByteOffset(text, "class".length())
        );
        SFMDefinitionResult.Definition definition = SFMJumpToDefinitionActionTests.definition(
                "example.Target", "Target.java", 6, "Target");
        SFMTextDocumentRange destinationRange = SFMDefinitionNavigation.range(definition.identifierSpan());
        SFMTextDocumentSnapshot baseline = withTargetRange(snapshot(text, target, root), originalRange);
        TrackingDocumentPanel delegate = new TrackingDocumentPanel(baseline);
        SFMDeferredTextEditorPanel deferred = new SFMDeferredTextEditorPanel(new SFMTextEditorPanelRecipe(
                new ResourceLocation("sfm", "text_editor"),
                TEST_EDITOR_ID,
                new SFMTextDocumentSource.Literal(text),
                true,
                "Target.java"
        ));
        setField(deferred, "snapshot", Optional.of(baseline));
        setField(deferred, "delegate", delegate);
        SFMWorkspacePanelId targetPanelId = new SFMWorkspacePanelId(3);
        TrackingWorkspace workspace = new TrackingWorkspace(targetPanelId, deferred);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                new SFMWorkspacePanelId(1),
                hello(),
                definition
        );

        assertEquals(SFMDefinitionNavigation.Status.FOCUSED_EXISTING, result.status());
        assertEquals(targetPanelId, result.panelId());
        assertEquals(targetPanelId, workspace.focused);
        assertEquals(Optional.of(destinationRange), delegate.documentSnapshot().orElseThrow().targetRange());
        assertEquals(Optional.of(destinationRange), deferred.documentSnapshot().orElseThrow().targetRange(),
                "the deferred panel observed by diagnostics and puppets must publish the new destination");
        assertFalse(workspace.openedInSourceStack,
                "reusing an existing deferred editor must not open a duplicate panel");
        assertEquals(List.of(targetPanelId), workspace.panelIds());
    }

    @Test
    void unseenDefinitionOpensAsATabInTheOriginatingPanelStack() {
        SFMPath root = SFMPath.parse("file:///D:/workspace/src");
        SFMWorkspacePanelId sourcePanelId = new SFMWorkspacePanelId(1);
        TrackingDocumentPanel source = new TrackingDocumentPanel(snapshot(
                "class Source {}\n",
                SFMPath.parse("file:///D:/workspace/src/Source.java"),
                root
        ));
        TrackingWorkspace workspace = new TrackingWorkspace(sourcePanelId, source);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                sourcePanelId,
                hello(),
                SFMJumpToDefinitionActionTests.definition(
                        "example.Target", "Target.java", 6, "Target"),
                () -> TEST_EDITOR_ID
        );

        assertEquals(SFMDefinitionNavigation.Status.OPENED_IN_SOURCE_STACK, result.status());
        assertTrue(workspace.openedInSourceStack);
        assertEquals(sourcePanelId, workspace.openedIntoSourcePanelId);
        assertEquals(new SFMWorkspacePanelId(9), result.panelId());
    }

    @Test
    void jdkDefinitionUsesTheExactWorkerManagedRootAndCurrentPanelStack() {
        SFMWorkspacePanelId sourcePanelId = new SFMWorkspacePanelId(1);
        TrackingWorkspace workspace = new TrackingWorkspace(sourcePanelId, null, true);
        SFMDefinitionResult.SymbolIdentity symbol = new SFMDefinitionResult.SymbolIdentity(
                "class", "java.lang.String", "String", Optional.empty(), "java.lang.String");
        SFMDefinitionResult.DefinitionSourceSpan span = new SFMDefinitionResult.DefinitionSourceSpan(
                "jdk-source://jdk-java-17-abc123/java.base/java/lang/String.java",
                "jdk-source",
                "jdk-java-17-abc123",
                "java.base/java/lang/String.java",
                "jdk/java-17/abc123/java.base/java/lang/String.java",
                "jdk:java-17",
                "blake3:" + "a".repeat(64),
                Optional.of("sha256:" + "b".repeat(64)),
                13, 19, 1, 14, 1, 20);
        SFMDefinitionResult.Definition definition = new SFMDefinitionResult.Definition(
                symbol, span, span, "resolved");

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace, sourcePanelId, helloWithJdk(), definition, () -> TEST_EDITOR_ID);

        assertEquals(SFMDefinitionNavigation.Status.OPENED_IN_SOURCE_STACK, result.status());
        assertTrue(workspace.managedRootAuthorized);
        assertTrue(workspace.openedInSourceStack);
        assertEquals(sourcePanelId, workspace.openedIntoSourcePanelId);
    }

    @Test
    void portableWorkspaceTargetResolvesThroughNegotiatedRootBeforeOpening() {
        SFMDefinitionResult.Definition fileDefinition = SFMJumpToDefinitionActionTests.definition(
                "example.Target", "Target.java", 6, "Target");
        SFMDefinitionResult.DefinitionSourceSpan fileSpan = fileDefinition.identifierSpan();
        SFMDefinitionResult.DefinitionSourceSpan workspaceSpan = new SFMDefinitionResult.DefinitionSourceSpan(
                "workspace://main/Target.java", "workspace", "main", "Target.java",
                fileSpan.reportPath(), fileSpan.sourceSet(), fileSpan.sourceHash(), fileSpan.sourceSha256(),
                fileSpan.startByte(), fileSpan.endByte(), fileSpan.startLine(), fileSpan.startColumn(),
                fileSpan.endLine(), fileSpan.endColumn()
        );
        SFMPath authorizedRoot = SFMPath.parse("file:///D:/workspace/src");

        SFMPath target = SFMDefinitionNavigation.resolveTarget(
                SFMPath.parse(workspaceSpan.address()), workspaceSpan, authorizedRoot);

        assertEquals(SFMPath.parse("file:///D:/workspace/src/Target.java"), target);
    }

    @Test
    void definitionReadReusesOriginatingDocumentAuthorityWithoutGrantingWorkerRoot() {
        SFMPath documentRoot = SFMPath.parse("file:///D:/workspace");
        SFMPath analysisRoot = SFMPath.parse("file:///D:/workspace/src");
        SFMPath target = SFMPath.parse("file:///D:/workspace/src/Target.java");
        TrackingDocumentPanel source = new TrackingDocumentPanel(snapshot(
                "class Source {}\n",
                SFMPath.parse("file:///D:/workspace/src/Source.java"),
                documentRoot
        ));

        assertEquals(
                Optional.of(documentRoot),
                SFMDefinitionNavigation.sourceReadAuthority(source, analysisRoot, target)
        );
        assertTrue(SFMDefinitionNavigation.sourceReadAuthority(
                source,
                SFMPath.parse("file:///D:/other/src"),
                SFMPath.parse("file:///D:/other/src/Target.java")
        ).isEmpty());
    }

    @Test
    void definitionReadUsesTheIntersectionOfNarrowResolverAndWorkerRoots() {
        SFMPath analysisRoot = SFMPath.parse("file:///D:/workspace/src");
        SFMPath packageGrant = SFMPath.parse("file:///D:/workspace/src/example");
        TrackingDocumentPanel source = new TrackingDocumentPanel(snapshot(
                "class Source {}\n",
                SFMPath.parse("file:///D:/workspace/src/example/Source.java"),
                packageGrant
        ));

        assertEquals(
                Optional.of(packageGrant),
                SFMDefinitionNavigation.sourceReadAuthority(
                        source,
                        analysisRoot,
                        SFMPath.parse("file:///D:/workspace/src/example/Target.java")
                )
        );
        assertTrue(SFMDefinitionNavigation.sourceReadAuthority(
                source,
                analysisRoot,
                SFMPath.parse("file:///D:/workspace/src/other/Target.java")
        ).isEmpty(), "the worker root must not broaden the originating resolver grant");
    }

    @Test
    void dependencySourceTargetResolvesOnlyWithinItsAdvertisedManagedRoot() {
        SFMDefinitionResult.Definition fileDefinition = SFMJumpToDefinitionActionTests.definition(
                "net.minecraftforge.ForgeType", "ForgeType.java", 6, "ForgeType");
        SFMDefinitionResult.DefinitionSourceSpan fileSpan = fileDefinition.identifierSpan();
        SFMDefinitionResult.DefinitionSourceSpan dependencySpan = new SFMDefinitionResult.DefinitionSourceSpan(
                "dependency-source://dependency-source-0/net/minecraftforge/ForgeType.java",
                "dependency-source",
                "dependency-source-0",
                "net/minecraftforge/ForgeType.java",
                "dependency/forge/userdev/loader-pipeline/net/minecraftforge/ForgeType.java",
                "dependency:forge:userdev",
                fileSpan.sourceHash(),
                fileSpan.sourceSha256(),
                fileSpan.startByte(), fileSpan.endByte(), fileSpan.startLine(), fileSpan.startColumn(),
                fileSpan.endLine(), fileSpan.endColumn()
        );
        SFMPath managedRoot = SFMPath.parse("file:///D:/managed/forge");

        assertEquals(
                SFMPath.parse("file:///D:/managed/forge/net/minecraftforge/ForgeType.java"),
                SFMDefinitionNavigation.resolveTarget(
                        SFMPath.parse(dependencySpan.address()), dependencySpan, managedRoot)
        );
    }

    @Test
    void workspaceTargetWithMismatchedRootIdentityFailsBeforePanelMutation() {
        SFMDefinitionResult.Definition fileDefinition = SFMJumpToDefinitionActionTests.definition(
                "example.Target", "Target.java", 6, "Target");
        SFMDefinitionResult.DefinitionSourceSpan fileSpan = fileDefinition.identifierSpan();
        SFMDefinitionResult.DefinitionSourceSpan workspaceSpan = new SFMDefinitionResult.DefinitionSourceSpan(
                "workspace://other/Target.java", "workspace", "main", "Target.java",
                fileSpan.reportPath(), fileSpan.sourceSet(), fileSpan.sourceHash(), fileSpan.sourceSha256(),
                fileSpan.startByte(), fileSpan.endByte(), fileSpan.startLine(), fileSpan.startColumn(),
                fileSpan.endLine(), fileSpan.endColumn()
        );
        SFMDefinitionResult.Definition definition = new SFMDefinitionResult.Definition(
                fileDefinition.symbol(), workspaceSpan, workspaceSpan, fileDefinition.confidence());
        TrackingWorkspace workspace = new TrackingWorkspace(new SFMWorkspacePanelId(3), null);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                new SFMWorkspacePanelId(1),
                hello(),
                definition
        );

        assertEquals(SFMDefinitionNavigation.Status.UNAVAILABLE, result.status());
        assertTrue(result.message().contains("root identity"));
        assertFalse(workspace.openedInSourceStack);
    }

    @Test
    void targetOutsideWorkerRootFailsBeforeAnyPanelMutation() {
        SFMDefinitionResult.Definition outside = SFMJumpToDefinitionActionTests.definitionAtAddress(
                "example.Target",
                "file:///D:/outside/Target.java",
                "Target.java",
                6,
                "Target"
        );
        TrackingWorkspace workspace = new TrackingWorkspace(new SFMWorkspacePanelId(3), null);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                new SFMWorkspacePanelId(1),
                hello(),
                outside
        );

        assertEquals(SFMDefinitionNavigation.Status.UNAVAILABLE, result.status());
        assertFalse(workspace.openedInSourceStack);
        assertEquals(null, workspace.focused);
    }

    @Test
    void missingSha256WitnessFailsClosedBeforeExistingOrNewPanelsAreTouched() {
        SFMDefinitionResult.Definition unpinned = withSha256Witness(
                SFMJumpToDefinitionActionTests.definition(
                        "example.Target", "Target.java", 6, "Target"),
                Optional.empty()
        );
        TrackingWorkspace workspace = new TrackingWorkspace(new SFMWorkspacePanelId(3), null);

        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                workspace,
                new SFMWorkspacePanelId(1),
                hello(),
                unpinned
        );

        assertEquals(SFMDefinitionNavigation.Status.UNAVAILABLE, result.status());
        assertTrue(result.message().contains("no SHA-256 witness"));
        assertFalse(workspace.openedInSourceStack);
        assertNull(workspace.focused);
    }

    @Test
    void staleExistingPanelIsNotReusableAndTheAsynchronousSourceRetainsTheExactWitness() {
        SFMPath root = SFMPath.parse("file:///D:/workspace/src");
        SFMPath target = SFMPath.parse("file:///D:/workspace/src/Target.java");
        String staleText = "class Target {}\n// stale panel\n";
        SFMDefinitionResult.Definition definition = SFMJumpToDefinitionActionTests.definition(
                "example.Target", "Target.java", 6, "Target");
        SFMTextDocumentRange range = SFMDefinitionNavigation.range(definition.identifierSpan());
        String expectedSha256 = definition.identifierSpan().sourceSha256().orElseThrow()
                .substring("sha256:".length());

        assertFalse(SFMDefinitionNavigation.exactOpenDocumentMatches(
                snapshot(staleText, target, root),
                target,
                expectedSha256,
                range,
                definition.symbol().name()
        ), "a same-path document with different bytes is not authoritative");
        SFMTextDocumentSource.PathAddress source = SFMDefinitionNavigation.pinnedSource(
                target,
                root,
                expectedSha256,
                range
        );
        assertEquals(
                definition.identifierSpan().sourceSha256()
                        .map(value -> value.substring("sha256:".length())),
                source.expectedSha256(),
                "the deferred resolver read must verify the Rust witness off the client thread"
        );
    }

    private static SFMDefinitionResult.Definition withSha256Witness(
            SFMDefinitionResult.Definition definition,
            Optional<String> witness
    ) {
        SFMDefinitionResult.DefinitionSourceSpan identifier = withSha256Witness(
                definition.identifierSpan(), witness);
        SFMDefinitionResult.DefinitionSourceSpan declaration = withSha256Witness(
                definition.declarationSpan(), witness);
        return new SFMDefinitionResult.Definition(
                definition.symbol(), identifier, declaration, definition.confidence());
    }

    private static SFMDefinitionResult.DefinitionSourceSpan withSha256Witness(
            SFMDefinitionResult.DefinitionSourceSpan span,
            Optional<String> witness
    ) {
        return new SFMDefinitionResult.DefinitionSourceSpan(
                span.address(), span.resolverId(), span.rootId(), span.rootRelativePath(),
                span.reportPath(), span.sourceSet(), span.sourceHash(), witness,
                span.startByte(), span.endByte(), span.startLine(), span.startColumn(),
                span.endLine(), span.endColumn()
        );
    }

    private static SFMSymbolServerProtocol.ServerHello hello() {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2", SFMDefinitionRequest.ClasspathMode.BRANCH, List.of(root),
                "blake3:classpath", Optional.empty(),
                "blake3:0000000000000000000000000000000000000000000000000000000000000000", 1);
        return new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "sfm-symbol-server", "1", Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1024 * 1024, 8,
                new SFMSymbolServerProtocol.WorkspaceMetadata(
                        workspace,
                        List.of(new SFMSymbolServerProtocol.SourceRootMapping(
                                "D:\\workspace\\src", "main", "main", "src"))
                ),
                "{}"
        );
    }

    private static SFMSymbolServerProtocol.ServerHello helloWithJdk() {
        SFMDefinitionRequest.SourceRoot main = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        SFMDefinitionRequest.SourceRoot jdk = new SFMDefinitionRequest.SourceRoot(
                "jdk-java-17-abc123", "jdk:java-17", "jdk/java-17/abc123", "jdk", true);
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2", SFMDefinitionRequest.ClasspathMode.BRANCH, List.of(main, jdk),
                "blake3:classpath", Optional.empty(),
                "blake3:" + "0".repeat(64), 1);
        return new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "sfm-symbol-server", "1", Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1024 * 1024, 8,
                new SFMSymbolServerProtocol.WorkspaceMetadata(
                        workspace,
                        List.of(
                                new SFMSymbolServerProtocol.SourceRootMapping(
                                        "D:\\workspace\\src", "main", "main", "src"),
                                new SFMSymbolServerProtocol.SourceRootMapping(
                                        "D:\\cache\\jdk\\tree", "jdk-java-17-abc123",
                                        "jdk:java-17", "jdk/java-17/abc123")
                        ),
                        List.of(),
                        List.of(new SFMSymbolServerProtocol.ManagedSourceRootMapping(
                                "jdk-source", "jdk-source", "jdk/java-17/abc123",
                                "D:\\cache\\jdk\\tree", "jdk-java-17-abc123", "jdk:java-17",
                                Optional.of("jdk/java-17/abc123"), Optional.empty()))
                ),
                "{}"
        );
    }

    private static SFMTextDocumentSnapshot snapshot(String text, SFMPath path, SFMPath root) {
        String sha256 = SFMTextDocumentSnapshot.literal(text).sha256().orElseThrow();
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path), Optional.of(root), Optional.of(sha256),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.of(Instant.EPOCH),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(), List.of()
        );
    }

    private static SFMTextDocumentSnapshot withTargetRange(
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
        return new SFMTextDocumentSnapshot(
                document.state(),
                document.text(),
                document.mutationCapability(),
                document.path(),
                document.authorizedRoot(),
                document.sha256(),
                document.byteLength(),
                document.lastModified(),
                document.lineEndingKind(),
                Optional.of(range),
                document.diagnostics()
        );
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SFMScreenMultiplexer uninitializedWorkspace() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (SFMScreenMultiplexer) ((Unsafe) field.get(null)).allocateInstance(SFMScreenMultiplexer.class);
    }

    private static final class TrackingWorkspace implements SFMDefinitionNavigation.Workspace {
        private final SFMWorkspacePanelId targetPanelId;
        private final SFMScreenPanel targetPanel;
        private boolean openedInSourceStack;
        private SFMWorkspacePanelId openedIntoSourcePanelId;
        private SFMWorkspacePanelId focused;
        private final boolean allowManagedRoot;
        private boolean managedRootAuthorized;

        private TrackingWorkspace(SFMWorkspacePanelId targetPanelId, SFMScreenPanel targetPanel) {
            this(targetPanelId, targetPanel, false);
        }

        private TrackingWorkspace(
                SFMWorkspacePanelId targetPanelId,
                SFMScreenPanel targetPanel,
                boolean allowManagedRoot
        ) {
            this.targetPanelId = targetPanelId;
            this.targetPanel = targetPanel;
            this.allowManagedRoot = allowManagedRoot;
        }

        @Override public List<SFMWorkspacePanelId> panelIds() {
            return targetPanel == null ? List.of() : List.of(targetPanelId);
        }
        @Override public SFMScreenPanel panel(SFMWorkspacePanelId panelId) { return targetPanel; }
        @Override public boolean focus(SFMWorkspacePanelId panelId) { focused = panelId; return true; }
        @Override public SFMWorkspacePanelIntentResult openInSourceStack(
                SFMWorkspacePanelId sourcePanelId,
                SFMScreenPanel panel,
                SFMPanelReopenRecipe recipe
        ) {
            openedInSourceStack = true;
            openedIntoSourcePanelId = sourcePanelId;
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        @Override public SFMWorkspacePanelId focusedPanelId() { return new SFMWorkspacePanelId(9); }
        @Override public boolean authorizeManagedReadRoot(SFMPath root) {
            managedRootAuthorized = true;
            return allowManagedRoot;
        }
    }

    private static final class TrackingDocumentPanel implements SFMScreenPanel, SFMTextDocumentPanelState {
        private SFMTextDocumentSnapshot snapshot;
        private SFMTextDocumentRange navigatedRange;

        private TrackingDocumentPanel(SFMTextDocumentSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public Component title() { return Component.literal("Target.java"); }
        @Override public boolean isReadOnly() { return true; }
        @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() { return Optional.of(snapshot); }
        @Override public boolean navigateToRange(SFMTextDocumentRange range) {
            navigatedRange = range;
            snapshot = withTargetRange(snapshot, range);
            return true;
        }
        @Override public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                     int mouseX, int mouseY, float partialTick, boolean focused) { }
    }
}
