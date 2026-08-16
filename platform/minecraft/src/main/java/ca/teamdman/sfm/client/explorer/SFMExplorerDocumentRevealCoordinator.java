package ca.teamdman.sfm.client.explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Deterministic, host-independent policy for revealing one addressed document.
 * The coordinator never closes or replaces a panel; a host may only focus a
 * compatible target or append a new explorer.
 */
public final class SFMExplorerDocumentRevealCoordinator {
    public record Document(SFMPath path, SFMPath authorizedRoot) {
        public Document {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            if (!SFMPathHierarchy.contains(authorizedRoot, path)) {
                throw new IllegalArgumentException(
                        "Document path is outside its resolver authority: " + path.canonical()
                );
            }
        }
    }

    @FunctionalInterface
    public interface RevealTarget {
        CompletionStage<?> reveal(SFMPath containingRoot, SFMPath path);
    }

    public record ExplorerTarget(
            SFMExplorerId explorerId,
            SFMWorkspacePanelId panelId,
            Set<SFMPath> roots,
            boolean visible,
            long focusRecency,
            RevealTarget revealTarget
    ) {
        public ExplorerTarget {
            Objects.requireNonNull(explorerId, "explorerId");
            Objects.requireNonNull(panelId, "panelId");
            roots = Set.copyOf(roots);
            if (roots.isEmpty()) throw new IllegalArgumentException("An explorer target requires at least one root");
            if (focusRecency < 0) throw new IllegalArgumentException("Focus recency must not be negative");
            Objects.requireNonNull(revealTarget, "revealTarget");
        }
    }

    public interface Host {
        List<ExplorerTarget> existingTargets();

        /** Appends one explorer rooted at the exact resolver authority. */
        ExplorerTarget openNew(SFMPath authorizedRoot);

        boolean focus(SFMWorkspacePanelId panelId);
    }

    public record Outcome(
            SFMExplorerId explorerId,
            SFMWorkspacePanelId panelId,
            SFMPath containingRoot,
            SFMPath path,
            boolean openedNew
    ) {
    }

    private record Match(ExplorerTarget target, SFMPath containingRoot) {
    }

    private static final Comparator<Match> MATCH_ORDER = Comparator
            .<Match>comparingInt(match -> match.target().visible() ? 1 : 0).reversed()
            .thenComparing(Comparator.comparingLong(
                    (Match match) -> match.target().focusRecency()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (Match match) -> SFMPathHierarchy.depth(match.containingRoot())).reversed())
            .thenComparing(match -> match.target().explorerId().value())
            .thenComparingLong(match -> match.target().panelId().value());

    public CompletionStage<Outcome> reveal(Document document, Host host) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(host, "host");
        Match match = bestMatch(document.path(), host.existingTargets());
        boolean openedNew = match == null;
        if (match == null) {
            ExplorerTarget opened = Objects.requireNonNull(
                    host.openNew(document.authorizedRoot()),
                    "opened explorer target"
            );
            SFMPath root = SFMPathHierarchy.deepestContainingRoot(opened.roots(), document.path())
                    .orElseThrow(() -> new IllegalStateException(
                            "New explorer cannot represent the document path: " + document.path().canonical()
                    ));
            match = new Match(opened, root);
        }
        if (!host.focus(match.target().panelId())) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Explorer panel is no longer available: " + match.target().panelId().value()
            ));
        }

        Match selected = match;
        CompletionStage<?> reveal;
        try {
            reveal = Objects.requireNonNull(
                    selected.target().revealTarget().reveal(selected.containingRoot(), document.path()),
                    "reveal completion"
            );
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return reveal.thenApply(ignored -> new Outcome(
                selected.target().explorerId(),
                selected.target().panelId(),
                selected.containingRoot(),
                document.path(),
                openedNew
        ));
    }

    private static Match bestMatch(SFMPath path, List<ExplorerTarget> candidates) {
        return Objects.requireNonNull(candidates, "candidates").stream()
                .map(candidate -> SFMPathHierarchy.deepestContainingRoot(candidate.roots(), path)
                        .map(root -> new Match(candidate, root))
                        .orElse(null))
                .filter(Objects::nonNull)
                .min(MATCH_ORDER)
                .orElse(null);
    }
}
