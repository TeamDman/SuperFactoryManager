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
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Safe addressed navigation for one worker-resolved definition. */
public final class SFMDefinitionNavigation {
    public enum Status { FOCUSED_EXISTING, OPENED_ADJACENT, UNAVAILABLE }

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

        SFMWorkspacePanelIntentResult openRight(
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
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(sourcePanelId, "sourcePanelId");
        Objects.requireNonNull(hello, "hello");
        Objects.requireNonNull(definition, "definition");

        SFMDefinitionResult.DefinitionSourceSpan span = definition.identifierSpan();
        SFMPath portableAddress;
        try {
            portableAddress = SFMPath.parse(span.address());
        } catch (IllegalArgumentException failure) {
            return unavailable("Definition target address is invalid: " + rootMessage(failure));
        }
        boolean managedDependencyRoot = span.resolverId().equals("dependency-source");
        List<String> rootPaths;
        if (span.resolverId().equals("workspace")) {
            rootPaths = hello.workspace().rootMappings().stream()
                    .filter(candidate -> candidate.rootId().equals(span.rootId()))
                    .filter(candidate -> candidate.sourceSet().equals(span.sourceSet()))
                    .map(SFMSymbolServerProtocol.SourceRootMapping::canonicalAbsolutePath)
                    .sorted()
                    .toList();
        } else if (managedDependencyRoot) {
            rootPaths = hello.workspace().dependencySourceRootMappings().stream()
                    .filter(candidate -> candidate.rootId().equals(span.rootId()))
                    .filter(candidate -> candidate.sourceSet().equals(span.sourceSet()))
                    .filter(candidate -> dependencyReportPathMatches(candidate, span))
                    .map(SFMSymbolServerProtocol.DependencySourceRootMapping::canonicalAbsolutePath)
                    .sorted()
                    .toList();
        } else {
            return unavailable("Definition target uses unsupported resolver: " + span.resolverId());
        }
        if (rootPaths.size() != 1) {
            return unavailable("Definition root is unavailable or ambiguous: " + span.rootId());
        }
        SFMPath analysisRoot;
        try {
            analysisRoot = SFMPath.fromNative(
                    java.nio.file.Path.of(rootPaths.get(0)));
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
                    definition.symbol().name()
            )) continue;
            if (!editor.navigateToRange(range) || !workspace.focus(panelId)) continue;
            return new Result(Status.FOCUSED_EXISTING, panelId,
                    "Focused " + span.address() + ":" + span.startLine());
        }

        Optional<SFMPath> readAuthority;
        if (managedDependencyRoot) {
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
                target, readAuthority.orElseThrow(), expectedSha256, range);
        ResourceLocation editorId = SFMTextEditors.V3.getId().orElseThrow().location();
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
        SFMWorkspacePanelIntentResult opened = workspace.openRight(sourcePanelId, recipe.reopen(), recipe);
        if (opened != SFMWorkspacePanelIntentResult.APPLIED) {
            return unavailable("No safe adjacent panel placement is available for the definition");
        }
        SFMWorkspacePanelId openedId = workspace.focusedPanelId();
        return new Result(Status.OPENED_ADJACENT, openedId,
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
     * that it contains both the worker-negotiated analysis root and target.
     * A worker response therefore narrows authority but can never create it.
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
                .filter(root -> contains(root, analysisRoot))
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
        return document.ready()
                && document.path().isPresent()
                && document.path().orElseThrow().equals(target)
                && document.sha256().isPresent()
                && document.sha256().orElseThrow().equals(expectedSha256)
                && rangeStillNamesSymbol(document.text(), range, symbolName);
    }

    static SFMTextDocumentSource.PathAddress pinnedSource(
            SFMPath target,
            SFMPath authorizedRoot,
            String expectedSha256,
            SFMTextDocumentRange range
    ) {
        return new SFMTextDocumentSource.PathAddress(
                target,
                authorizedRoot,
                Optional.of(expectedSha256),
                SFMTextDocumentSource.DEFAULT_MAXIMUM_BYTES,
                Optional.of(range)
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

    private record MultiplexerWorkspace(SFMScreenMultiplexer delegate) implements Workspace {
        private MultiplexerWorkspace {
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override public List<SFMWorkspacePanelId> panelIds() { return delegate.panelIds(); }
        @Override public @Nullable SFMScreenPanel panel(SFMWorkspacePanelId panelId) {
            return delegate.panelInstance(panelId);
        }
        @Override public boolean focus(SFMWorkspacePanelId panelId) { return delegate.focusPanel(panelId); }
        @Override public SFMWorkspacePanelIntentResult openRight(
                SFMWorkspacePanelId sourcePanelId,
                SFMScreenPanel panel,
                SFMPanelReopenRecipe recipe
        ) {
            return delegate.openToSide(sourcePanelId, SFMWorkspaceSide.RIGHT, panel, recipe);
        }
        @Override public SFMWorkspacePanelId focusedPanelId() { return delegate.focusedPanelId(); }
        @Override public boolean authorizeManagedReadRoot(SFMPath root) {
            return SFMExplorerRuntime.get().authorizeManagedReadOnlyRoot(root);
        }
    }
}
