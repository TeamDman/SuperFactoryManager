package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSourceRootIdentity;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Safe addressed navigation for one worker-resolved definition. */
public final class SFMDefinitionNavigation {
    public enum Status { FOCUSED_EXISTING, OPENED_IN_SOURCE_STACK, UNAVAILABLE }

    public record Result(Status status, @Nullable SFMWorkspacePanelId panelId, String message) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }

        public boolean applied() {
            return status != Status.UNAVAILABLE;
        }
    }

    interface Workspace {
        List<SFMWorkspacePanelId> panelIds();

        @Nullable SFMScreenPanel panel(SFMWorkspacePanelId panelId);

        boolean focus(SFMWorkspacePanelId panelId);

        SFMWorkspacePanelIntentResult openInSourceStack(
                SFMWorkspacePanelId sourcePanelId,
                SFMScreenPanel panel,
                SFMPanelReopenRecipe recipe
        );

        SFMWorkspacePanelId focusedPanelId();

        default boolean authorizeManagedReadRoot(SFMPath root) {
            return false;
        }
    }

    private SFMDefinitionNavigation() {
    }

    public static Result open(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            SFMDefinitionResult.Definition definition
    ) {
        Objects.requireNonNull(workspace, "workspace");
        return open(new MultiplexerWorkspace(workspace), sourcePanelId, hello, definition);
    }

    static Result open(
            Workspace workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            SFMDefinitionResult.Definition definition
    ) {
        return open(
                workspace,
                sourcePanelId,
                hello,
                definition,
                () -> SFMTextEditors.V3.getId().orElseThrow().location()
        );
    }

    /**
     * Test seam for navigation placement. The production supplier remains lazy
     * so focusing an already-open document does not initialize the editor
     * registry, and standalone tests can exercise placement without ModLauncher.
     */
    static Result open(
            Workspace workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            SFMDefinitionResult.Definition definition,
            Supplier<ResourceLocation> editorIdSupplier
    ) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(sourcePanelId, "sourcePanelId");
        Objects.requireNonNull(hello, "hello");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(editorIdSupplier, "editorIdSupplier");

        SFMDefinitionResult.DefinitionSourceSpan span = definition.identifierSpan();
        SFMPath portableAddress;
        try {
            portableAddress = SFMPath.parse(span.address());
        } catch (IllegalArgumentException failure) {
            return unavailable("Definition target address is invalid: " + rootMessage(failure));
        }
        boolean dependencySourceRoot = span.resolverId().equals("dependency-source");
        List<SFMSymbolServerProtocol.ManagedSourceRootMapping> managedMappings =
                hello.workspace().managedSourceRootMappings().stream()
                        .filter(candidate -> candidate.resolverId().equals(span.resolverId()))
                        .filter(candidate -> candidate.addressScheme().equals(portableAddress.scheme()))
                        .filter(candidate -> candidate.rootId().equals(span.rootId()))
                        .filter(candidate -> candidate.sourceSet().equals(span.sourceSet()))
                        .toList();
        boolean managedSourceRoot = managedMappings.size() == 1;
        List<SourceRootSelection> rootSelections;
        if (span.resolverId().equals("workspace")) {
            rootSelections = hello.workspace().rootMappings().stream()
                    .filter(candidate -> candidate.rootId().equals(span.rootId()))
                    .filter(candidate -> candidate.sourceSet().equals(span.sourceSet()))
                    .map(candidate -> new SourceRootSelection(
                            candidate.canonicalAbsolutePath(),
                            new SFMTextDocumentSourceRootIdentity(
                                    "workspace",
                                    "file",
                                    candidate.rootId(),
                                    candidate.sourceSet(),
                                    candidate.reportRootPath()
                            )
                    ))
                    .sorted(Comparator.comparing(SourceRootSelection::canonicalAbsolutePath))
                    .toList();
        } else if (dependencySourceRoot) {
            rootSelections = hello.workspace().dependencySourceRootMappings().stream()
                    .filter(candidate -> candidate.rootId().equals(span.rootId()))
                    .filter(candidate -> candidate.sourceSet().equals(span.sourceSet()))
                    .filter(candidate -> dependencyReportPathMatches(candidate, span))
                    .map(candidate -> new SourceRootSelection(
                            candidate.canonicalAbsolutePath(),
                            new SFMTextDocumentSourceRootIdentity(
                                    "dependency-source",
                                    "dependency-source",
                                    candidate.rootId(),
                                    candidate.sourceSet(),
                                    candidate.reportPrefix()
                            )
                    ))
                    .sorted(Comparator.comparing(SourceRootSelection::canonicalAbsolutePath))
                    .toList();
        } else if (managedSourceRoot) {
            rootSelections = managedMappings.stream()
                    .map(candidate -> new SourceRootSelection(
                            candidate.canonicalAbsolutePath(),
                            new SFMTextDocumentSourceRootIdentity(
                                    candidate.resolverId(),
                                    candidate.addressScheme(),
                                    candidate.rootId(),
                                    candidate.sourceSet(),
                                    candidate.reportPrefix()
                                            .or(() -> candidate.portableRootPath())
                                            .orElse("")
                            )
                    ))
                    .sorted(Comparator.comparing(SourceRootSelection::canonicalAbsolutePath))
                    .toList();
        } else {
            return unavailable("Definition target uses unsupported resolver: " + span.resolverId());
        }
        if (rootSelections.size() != 1) {
            return unavailable("Definition root is unavailable or ambiguous: " + span.rootId());
        }
        SourceRootSelection rootSelection = rootSelections.get(0);
        SFMPath analysisRoot;
        try {
            analysisRoot = SFMPath.fromNative(
                    java.nio.file.Path.of(rootSelection.canonicalAbsolutePath()));
        } catch (IllegalArgumentException failure) {
            return unavailable("Definition root path is invalid: " + rootMessage(failure));
        }
        SFMPath target;
        try {
            target = resolveTarget(portableAddress, span, analysisRoot);
        } catch (IllegalArgumentException failure) {
            return unavailable("Definition target address is invalid: " + rootMessage(failure));
        }
        if (!target.toNativePath().toAbsolutePath().normalize().startsWith(
                analysisRoot.toNativePath().toAbsolutePath().normalize())) {
            return unavailable("Definition target lies outside its worker-authorized root");
        }

        SFMTextDocumentRange range;
        try {
            range = range(span);
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return unavailable("Definition range is not representable: " + rootMessage(failure));
        }
        if (span.sourceSha256().isEmpty()
                || !span.sourceSha256().orElseThrow().startsWith("sha256:")
                || span.sourceSha256().orElseThrow().length() <= "sha256:".length()) {
            return unavailable("Definition target has no SHA-256 witness for its Rust source identity");
        }
        String expectedSha256 = span.sourceSha256().orElseThrow()
                .substring("sha256:".length());

        for (SFMWorkspacePanelId panelId : workspace.panelIds().stream()
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value)).toList()) {
            SFMScreenPanel panel = workspace.panel(panelId);
            if (!(panel instanceof SFMTextDocumentPanelState editor) || !editor.isReadOnly()) continue;
            Optional<SFMTextDocumentSnapshot> snapshot = editor.documentSnapshot();
            if (snapshot.isEmpty() || !snapshot.orElseThrow().ready()) continue;
            SFMTextDocumentSnapshot document = snapshot.orElseThrow();
            if (!exactOpenDocumentMatches(
                    document,
                    target,
                    expectedSha256,
                    range,
                    definition.symbol().name(),
                    rootSelection.identity()
            )) continue;
            // Once an exact immutable document is found, never fall through
            // to opening a duplicate. Focus it first, then update and verify
            // the exact destination before reporting FOCUSED_EXISTING.
            if (!workspace.focus(panelId)) {
                return unavailable("The existing definition editor could not be focused");
            }
            if (workspace.panel(panelId) != panel) {
                return unavailable("The existing definition editor changed while it was focused");
            }
            if (!editor.navigateToRange(range)) {
                return unavailable("The existing definition editor could not apply the exact target range");
            }
            boolean exactRangePublished = editor.documentSnapshot()
                    .filter(SFMTextDocumentSnapshot::ready)
                    .flatMap(SFMTextDocumentSnapshot::targetRange)
                    .filter(range::equals)
                    .isPresent();
            if (!exactRangePublished) {
                return unavailable("The existing definition editor did not publish the exact target range");
            }
            return new Result(Status.FOCUSED_EXISTING, panelId,
                    "Focused " + span.address() + ":" + span.startLine());
        }

        Optional<SFMPath> readAuthority;
        if (dependencySourceRoot || managedSourceRoot) {
            readAuthority = workspace.authorizeManagedReadRoot(analysisRoot)
                    ? Optional.of(analysisRoot)
                    : Optional.empty();
        } else {
            readAuthority = sourceReadAuthority(
                    workspace.panel(sourcePanelId), analysisRoot, target);
        }
        if (readAuthority.isEmpty()) {
            return unavailable("The originating document no longer grants the definition target");
        }
        SFMTextDocumentSource.PathAddress source = pinnedSource(
                target,
                readAuthority.orElseThrow(),
                expectedSha256,
                range,
                rootSelection.identity()
        );
        ResourceLocation editorId = Objects.requireNonNull(
                editorIdSupplier.get(),
                "editorIdSupplier returned null"
        );
        String title = target.segments().isEmpty()
                ? target.canonical()
                : target.segments().get(target.segments().size() - 1);
        SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                editorId,
                source,
                true,
                title
        );
        SFMWorkspacePanelIntentResult opened = workspace.openInSourceStack(
                sourcePanelId, recipe.reopen(), recipe);
        if (opened != SFMWorkspacePanelIntentResult.APPLIED) {
            return unavailable("No safe source-panel stack placement is available for the definition");
        }
        SFMWorkspacePanelId openedId = workspace.focusedPanelId();
        return new Result(Status.OPENED_IN_SOURCE_STACK, openedId,
                "Opened " + span.address() + ":" + span.startLine());
    }

    /** Resolve a portable worker span only through its negotiated root authority. */
    static SFMPath resolveTarget(
            SFMPath portableAddress,
            SFMDefinitionResult.DefinitionSourceSpan span,
            SFMPath authorizedRoot
    ) {
        Objects.requireNonNull(portableAddress, "portableAddress");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(authorizedRoot, "authorizedRoot");
        if (portableAddress.kind() == SFMPath.Kind.FILE) return portableAddress;
        if (portableAddress.kind() != SFMPath.Kind.CONTRIBUTED
                || !portableAddress.scheme().equals(span.resolverId())) {
            throw new IllegalArgumentException(
                    "Definition target uses unsupported resolver scheme: " + portableAddress.scheme());
        }
        if (!portableAddress.authority().equals(span.rootId())) {
            throw new IllegalArgumentException("Definition address authority disagrees with its root identity");
        }
        String addressedRelativePath = String.join("/", portableAddress.segments());
        if (!addressedRelativePath.equals(span.rootRelativePath())) {
            throw new IllegalArgumentException("Workspace address disagrees with its root-relative path");
        }
        java.nio.file.Path nativeTarget = authorizedRoot.toNativePath().toAbsolutePath().normalize();
        for (String segment : portableAddress.segments()) nativeTarget = nativeTarget.resolve(segment);
        nativeTarget = nativeTarget.toAbsolutePath().normalize();
        if (!nativeTarget.startsWith(authorizedRoot.toNativePath().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Workspace address escapes its authorized root");
        }
        return SFMPath.fromNative(nativeTarget);
    }

    static SFMTextDocumentRange range(SFMDefinitionResult.DefinitionSourceSpan span) {
        return new SFMTextDocumentRange(
                new SFMTextDocumentPosition(
                        Math.toIntExact(span.startLine() - 1),
                        Math.toIntExact(span.startColumn() - 1),
                        Math.toIntExact(span.startByte())
                ),
                new SFMTextDocumentPosition(
                        Math.toIntExact(span.endLine() - 1),
                        Math.toIntExact(span.endColumn() - 1),
                        Math.toIntExact(span.endByte())
                )
        );
    }

    /**
     * Reuses the originating document's existing resolver grant after proving
     * that both it and the worker-negotiated analysis root contain the target.
     * The two roots may be nested in either direction; their intersection is
     * the usable authority. A worker response therefore narrows authority but
     * can never create it.
     */
    static Optional<SFMPath> sourceReadAuthority(
            @Nullable SFMScreenPanel sourcePanel,
            SFMPath analysisRoot,
            SFMPath target
    ) {
        Objects.requireNonNull(analysisRoot, "analysisRoot");
        Objects.requireNonNull(target, "target");
        if (!(sourcePanel instanceof SFMTextDocumentPanelState editor)) return Optional.empty();
        return editor.documentSnapshot()
                .filter(SFMTextDocumentSnapshot::ready)
                .flatMap(SFMTextDocumentSnapshot::authorizedRoot)
                .filter(root -> contains(analysisRoot, target))
                .filter(root -> contains(root, target));
    }

    static boolean exactOpenDocumentMatches(
            SFMTextDocumentSnapshot document,
            SFMPath target,
            String expectedSha256,
            SFMTextDocumentRange range,
            String symbolName
    ) {
        return exactOpenDocumentMatches(
                document,
                target,
                expectedSha256,
                range,
                symbolName,
                null
        );
    }

    static boolean exactOpenDocumentMatches(
            SFMTextDocumentSnapshot document,
            SFMPath target,
            String expectedSha256,
            SFMTextDocumentRange range,
            String symbolName,
            @Nullable SFMTextDocumentSourceRootIdentity expectedRootIdentity
    ) {
        return document.ready()
                && document.path().isPresent()
                && document.path().orElseThrow().equals(target)
                && document.sha256().isPresent()
                && document.sha256().orElseThrow().equals(expectedSha256)
                && rootIdentityMatches(document, expectedRootIdentity)
                && rangeStillNamesSymbol(document.text(), range, symbolName);
    }

    private static boolean rootIdentityMatches(
            SFMTextDocumentSnapshot document,
            @Nullable SFMTextDocumentSourceRootIdentity expectedRootIdentity
    ) {
        if (expectedRootIdentity == null) return true;
        if (document.sourceRootIdentity().isPresent()) {
            return document.sourceRootIdentity().orElseThrow().equals(expectedRootIdentity);
        }
        // Ordinary workspace files can be opened directly from an explorer,
        // before any symbol worker has supplied provenance. Managed and
        // dependency documents must never be reused without exact provenance.
        return expectedRootIdentity.resolverId().equals("workspace");
    }

    static SFMTextDocumentSource.PathAddress pinnedSource(
            SFMPath target,
            SFMPath authorizedRoot,
            String expectedSha256,
            SFMTextDocumentRange range
    ) {
        return pinnedSource(target, authorizedRoot, expectedSha256, range, null);
    }

    static SFMTextDocumentSource.PathAddress pinnedSource(
            SFMPath target,
            SFMPath authorizedRoot,
            String expectedSha256,
            SFMTextDocumentRange range,
            @Nullable SFMTextDocumentSourceRootIdentity sourceRootIdentity
    ) {
        return new SFMTextDocumentSource.PathAddress(
                target,
                authorizedRoot,
                Optional.of(expectedSha256),
                SFMTextDocumentSource.DEFAULT_MAXIMUM_BYTES,
                Optional.of(range),
                Optional.ofNullable(sourceRootIdentity)
        );
    }

    private static boolean rangeStillNamesSymbol(
            String text,
            SFMTextDocumentRange range,
            String symbolName
    ) {
        try {
            range.validateAgainst(text);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            int start = range.start().byteOffset();
            int end = range.end().byteOffset();
            return new String(bytes, start, end - start, StandardCharsets.UTF_8).equals(symbolName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean contains(SFMPath root, SFMPath path) {
        if (root.kind() != SFMPath.Kind.FILE || path.kind() != SFMPath.Kind.FILE) return false;
        java.nio.file.Path nativeRoot = root.toNativePath().toAbsolutePath().normalize();
        java.nio.file.Path nativePath = path.toNativePath().toAbsolutePath().normalize();
        return nativePath.startsWith(nativeRoot);
    }

    private static boolean dependencyReportPathMatches(
            SFMSymbolServerProtocol.DependencySourceRootMapping mapping,
            SFMDefinitionResult.DefinitionSourceSpan span
    ) {
        String prefix = mapping.reportPrefix() + "/";
        return span.reportPath().startsWith(prefix)
                && span.reportPath().substring(prefix.length()).equals(span.rootRelativePath());
    }

    private static Result unavailable(String message) {
        return new Result(Status.UNAVAILABLE, null, message);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record SourceRootSelection(
            String canonicalAbsolutePath,
            SFMTextDocumentSourceRootIdentity identity
    ) {
        private SourceRootSelection {
            Objects.requireNonNull(canonicalAbsolutePath, "canonicalAbsolutePath");
            Objects.requireNonNull(identity, "identity");
        }
    }

    private record MultiplexerWorkspace(SFMScreenMultiplexer delegate) implements Workspace {
        private MultiplexerWorkspace {
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override public List<SFMWorkspacePanelId> panelIds() { return delegate.panelIds(); }
        @Override public @Nullable SFMScreenPanel panel(SFMWorkspacePanelId panelId) {
            return delegate.panelInstance(panelId);
        }
        @Override public boolean focus(SFMWorkspacePanelId panelId) { return delegate.focusPanel(panelId); }
        @Override public SFMWorkspacePanelIntentResult openInSourceStack(
                SFMWorkspacePanelId sourcePanelId,
                SFMScreenPanel panel,
                SFMPanelReopenRecipe recipe
        ) {
            return delegate.openIntoSlot(
                    sourcePanelId,
                    panel,
                    SFMWorkspacePanelMetadata.ordinary(),
                    recipe
            );
        }
        @Override public SFMWorkspacePanelId focusedPanelId() { return delegate.focusedPanelId(); }
        @Override public boolean authorizeManagedReadRoot(SFMPath root) {
            return SFMExplorerRuntime.get().authorizeManagedReadOnlyRoot(root);
        }
    }
}
