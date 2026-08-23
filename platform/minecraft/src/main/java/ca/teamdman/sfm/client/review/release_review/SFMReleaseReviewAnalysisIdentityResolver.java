package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Borrows an exact candidate-checkout file identity for language analysis while
 * the editor and durable comment continue to use their pinned review:// address.
 */
public final class SFMReleaseReviewAnalysisIdentityResolver {
    private SFMReleaseReviewAnalysisIdentityResolver() {
    }

    public static Optional<SFMTextDocumentSnapshot.AnalysisIdentity> resolve(
            SFMReleaseReviewV1 review,
            Path reviewPath,
            String documentRevisionId
    ) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(reviewPath, "reviewPath");
        Objects.requireNonNull(documentRevisionId, "documentRevisionId");
        SFMReleaseReviewV1.CorpusDocument corpus = review.corpusDocuments().stream()
                .filter(value -> value.documentRevisionId().equals(documentRevisionId))
                .findFirst().orElse(null);
        if (corpus == null
                || corpus.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.AFTER
                || corpus.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE) {
            return Optional.empty();
        }
        SFMReleaseReviewV1.RepositoryBinding repository = review.repositoryBindings().stream()
                .filter(value -> value.laneId().equals(corpus.laneId()))
                .findFirst().orElse(null);
        if (repository == null) return Optional.empty();
        Path hint;
        try {
            hint = Path.of(repository.rootHint());
        } catch (InvalidPathException invalidHint) {
            return Optional.empty();
        }
        if (hint.isAbsolute()) return identityAt(hint, corpus);
        Path cursor = reviewPath.toAbsolutePath().normalize().getParent();
        while (cursor != null) {
            Path candidate = cursor.resolve(hint).toAbsolutePath().normalize();
            Optional<SFMTextDocumentSnapshot.AnalysisIdentity> identity = identityAt(candidate, corpus);
            if (identity.isPresent()) return identity;
            cursor = cursor.getParent();
        }
        return Optional.empty();
    }

    private static Optional<SFMTextDocumentSnapshot.AnalysisIdentity> identityAt(
            Path root,
            SFMReleaseReviewV1.CorpusDocument corpus
    ) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot.resolve(".git"))) return Optional.empty();
        Path document = normalizedRoot.resolve(corpus.path()).normalize();
        if (!document.startsWith(normalizedRoot) || !Files.isRegularFile(document)) return Optional.empty();
        try {
            if (!SFMReleaseReviewKernel.sha256(Files.readAllBytes(document)).equals(corpus.sha256())) {
                return Optional.empty();
            }
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        return Optional.of(new SFMTextDocumentSnapshot.AnalysisIdentity(
                SFMPath.fromNative(document),
                SFMPath.fromNative(normalizedRoot),
                Optional.empty()
        ));
    }
}
