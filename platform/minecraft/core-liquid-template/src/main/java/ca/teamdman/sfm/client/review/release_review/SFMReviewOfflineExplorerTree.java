package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable generic-Explorer mount; never resolves current targets or mutates an active review. */
public record SFMReviewOfflineExplorerTree(SFMPath root, List<SFMInMemoryTextExplorerResolver.Node> nodes) {
    public static final String SCHEME = "review-evidence";
    public SFMReviewOfflineExplorerTree { nodes = List.copyOf(nodes); }

    public static SFMReviewOfflineExplorerTree prepare(String authority, String backingFile,
                                                      SFMReleaseReviewLedgerV3 ledger) {
        var inspection = SFMReviewOfflineEvidence.inspect(ledger);
        var nodes = new ArrayList<SFMInMemoryTextExplorerResolver.Node>();
        SFMPath root = path(authority, List.of());
        SFMPath status = child(root, "00-status.txt");
        String report = "Retained comment evidence only\nReview file: " + backingFile
                + "\nCurrent source domain: unavailable.\nNo current release completion claim."
                + "\nThis view performs no Git, source-file or network reads."
                + "\nComments: " + inspection.session().comments().size()
                + "\nMissing embedded documents: " + inspection.missingDocumentIds().size()
                + "\n\nA missing body is not an empty source file or an approval."
                + "\nGit-referenced evidence must be embedded before it can be read\nwithout the repository.\n";
        nodes.add(text(status, "Status · retained evidence only", report, Optional.empty()));
        SFMPath comments = child(root, "01-comments");
        append(nodes, comments, SFMReviewExplorerModel.comments(inspection.session()).root(), "Retained comments · offline evidence");
        SFMPath hashtags = child(root, "02-hashtags");
        append(nodes, hashtags, SFMReviewExplorerModel.hashtags(inspection.session()).root(), "Retained hashtags · offline evidence");
        SFMPath missing = child(root, "03-missing.txt");
        nodes.add(text(missing, inspection.missingDocumentIds().isEmpty() ? "All retained evidence is embedded"
                        : "Missing embedded evidence (" + inspection.missingDocumentIds().size() + ")",
                missingReport(ledger, inspection.missingDocumentIds()), Optional.empty()));
        nodes.add(SFMInMemoryTextExplorerResolver.Node.directory(entry(root,
                "Retained evidence · current source status unavailable", true, Optional.empty()),
                List.of(status, comments, hashtags, missing)));
        return new SFMReviewOfflineExplorerTree(root, nodes);
    }

    private static String missingReport(SFMReleaseReviewLedgerV3 ledger, List<String> missingIds) {
        if (missingIds.isEmpty()) return "All retained document bodies are embedded.\n"
                + "No Git or source-file access is needed to read these comments.\n"
                + "This does not establish current release coverage.\n";
        var report = new StringBuilder("Some captured source bytes are stored in Git, not this file.\n")
                .append("This offline view deliberately does not read Git.\n")
                .append("The comments remain saved; missing bytes are not empty files.\n")
                .append("\nOpen this file as a release review to use its repository.\n")
                .append("Then preview a self-contained evidence copy to embed the bytes.\n")
                .append("Git availability has not been checked by this offline view.\n");
        for (String id : missingIds) {
            var document = ledger.evidence().documents().stream().filter(d -> d.revisionId().equals(id)).findFirst().orElseThrow();
            String path = document.path();
            report.append("\nFile: ").append(path.substring(path.lastIndexOf('/') + 1)).append('\n')
                    .append("Original path: ").append(path).append('\n');
            var storage = ledger.evidence().gitStorage().stream().filter(s -> s.sha256().equals(document.sha256())).findFirst();
            var git = document.git().orElseGet(() -> storage.orElseThrow().git());
            report.append("Storage: ").append(document.git().isPresent() ? "original Git reference" : "separate Git reference; original target unchanged")
                    .append('\n').append("Repository: ").append(git.repositoryId()).append('\n')
                    .append("Commit: ").append(git.commit()).append('\n')
                    .append("Storage path: ").append(document.git().isPresent() ? path : storage.orElseThrow().path()).append('\n')
                    .append("Blob: ").append(git.blob()).append('\n')
                    .append("Original revision: ").append(id).append('\n')
                    .append("Content SHA256: ").append(document.sha256()).append('\n');
        }
        return report.toString();
    }

    private static void append(List<SFMInMemoryTextExplorerResolver.Node> nodes, SFMPath path,
                               SFMReviewExplorerModel.Node model) {
        append(nodes, path, model, model.label());
    }

    private static void append(List<SFMInMemoryTextExplorerResolver.Node> nodes, SFMPath path,
                               SFMReviewExplorerModel.Node model, String label) {
        var children = new ArrayList<SFMPath>();
        int index = 0;
        for (var child : model.children()) {
            var leaf = child.leaf();
            String extension = leaf == null ? "" : extension(leaf.path());
            SFMPath childPath = child(path, String.format(java.util.Locale.ROOT, "%06d", index++) + extension);
            children.add(childPath);
            append(nodes, childPath, child);
        }
        // Some model nodes both carry source text and contain properties. Keep both accessible.
        if (!children.isEmpty() && model.leaf() != null) {
            SFMPath contents = child(path, "source" + extension(model.leaf().path()));
            children.add(contents);
            nodes.add(leaf(contents, model.leaf()));
        }
        if (!children.isEmpty()) nodes.add(SFMInMemoryTextExplorerResolver.Node.directory(
                entry(path, label, true, Optional.empty()), children));
        else if (model.leaf() != null) nodes.add(leaf(path, model.leaf()));
        else nodes.add(text(path, label, label + "\n", Optional.empty()));
    }

    private static SFMInMemoryTextExplorerResolver.Node leaf(SFMPath path, SFMReviewExplorerModel.SourceLeaf leaf) {
        if (leaf.missing() || leaf.generatedSurface().isPresent()) return text(path, leaf.title(),
                "Unavailable in this evidence-only view\nSource: " + leaf.path() + "\n" + leaf.text(), Optional.empty());
        return text(path, leaf.title(), leaf.text(), Optional.of(leaf.path()));
    }

    private static SFMInMemoryTextExplorerResolver.Node text(SFMPath path, String label, String text,
                                                             Optional<String> sourcePath) {
        return SFMInMemoryTextExplorerResolver.Node.text(entry(path, label, false, sourcePath), text);
    }

    private static SFMExplorerEntry entry(SFMPath path, String label, boolean directory, Optional<String> sourcePath) {
        var keys = new TreeMap<String, SFMExplorerEntry.SortKey>();
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label));
        keys.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available(directory ? "minecraft:chest" : "minecraft:paper"));
        keys.put(SFMExplorerEntry.SUBJECT_KIND, SFMExplorerEntry.SortKey.available(directory ? "directory" : "file"));
        sourcePath.ifPresent(value -> keys.put("sfm:evidence/source-path", SFMExplorerEntry.SortKey.available(value)));
        return new SFMExplorerEntry(path, label, directory, keys, List.of(label), List.of());
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') && path.substring(dot).matches("\\.[A-Za-z0-9]{1,16}")
                ? path.substring(dot) : ".txt";
    }
    private static SFMPath child(SFMPath parent, String segment) {
        var segments = new ArrayList<>(parent.segments()); segments.add(segment);
        return path(parent.authority(), segments);
    }
    private static SFMPath path(String authority, List<String> segments) {
        return new SFMPath(SFMPath.Kind.CONTRIBUTED, SCHEME, authority, segments, Optional.empty(), false);
    }
}
