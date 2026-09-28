package ca.teamdman.sfm.client.review.repository;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentKernelDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Managed AppData inbox implementation; arbitrary host paths never enter the action surface. */
public final class SFMManagedReviewBundleRepository implements SFMRepositoryReviewRepository {
    private static final SFMManagedReviewBundleRepository DEFAULT = new SFMManagedReviewBundleRepository(
            defaultInbox(), SFMReviewSessionV1Store::forSessionId);

    private final Path inbox;
    private final Function<String, SFMReviewSessionV1Store> storeFactory;

    public SFMManagedReviewBundleRepository(Path inbox, Function<String, SFMReviewSessionV1Store> storeFactory) {
        this.inbox = inbox.toAbsolutePath().normalize();
        this.storeFactory = storeFactory;
    }

    public static SFMManagedReviewBundleRepository getDefault() { return DEFAULT; }

    public static Path defaultInbox() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(localAppData);
        return root.resolve("teamdman").resolve("SFM").resolve("review-bundles");
    }

    public Path inbox() { return inbox; }

    @Override
    public List<BundleSummary> listBundles() {
        Scan scan = scan();
        if (scan.entries().isEmpty() && !scan.diagnostics().isEmpty()) {
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                    "No valid review bundles were found", scan.diagnostics());
        }
        return scan.entries().stream().map(Entry::summary).toList();
    }

    @Override
    public OpenBundle open(String bundleIdOrName) {
        if (bundleIdOrName == null || bundleIdOrName.isBlank())
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.NOT_FOUND,
                    "Bundle id or name must not be blank");
        Scan scan = scan();
        if (scan.entries().isEmpty()) {
            if (!scan.diagnostics().isEmpty())
                throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                        "No valid review bundles were found", scan.diagnostics());
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.NO_BUNDLES,
                    "No review bundles in managed inbox " + inbox);
        }
        List<Entry> idMatches = scan.entries().stream()
                .filter(entry -> entry.bundle().id().equals(bundleIdOrName)).toList();
        Entry selected;
        if (idMatches.size() == 1) selected = idMatches.get(0);
        else {
            List<Entry> nameMatches = scan.entries().stream()
                    .filter(entry -> entry.bundle().name().equals(bundleIdOrName)).toList();
            if (nameMatches.size() > 1)
                throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.AMBIGUOUS_NAME,
                        "Review bundle name is ambiguous: " + bundleIdOrName,
                        nameMatches.stream().map(entry -> entry.path().getFileName() + " → " + entry.bundle().id()).toList());
            if (nameMatches.isEmpty())
                throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.NOT_FOUND,
                        "Review bundle not found: " + bundleIdOrName,
                        scan.entries().stream().map(entry -> entry.bundle().name()).toList());
            selected = nameMatches.get(0);
        }
        SFMRepositoryReviewSessionProjector.Projection projection =
                SFMRepositoryReviewSessionProjector.project(selected.bundle());
        String sessionId = projection.session().id();
        SFMReviewSessionV1Store store = storeFactory.apply(sessionId);
        SFMReviewSessionV1Store.LoadResult loaded = store.load();
        boolean restored = loaded.session().isPresent();
        SFMReviewSessionV1 session = restored
                ? mergePersisted(projection.session(), loaded.session().orElseThrow())
                : projection.session();
        try { store.save(session); }
        catch (IOException exception) {
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.IO_ERROR,
                    "Unable to persist review session: " + exception.getMessage());
        }
        List<String> diagnostics = new ArrayList<>(scan.diagnostics());
        diagnostics.addAll(projection.diagnostics());
        if (restored) diagnostics.addAll(loaded.diagnostics());
        List<ChangedFile> changedFiles = selected.bundle().comparison().fileChanges().stream()
                .map(change -> new ChangedFile(change.kind(), change.beforePath(), change.afterPath(),
                        change.binary(), change.diagnostics())).toList();
        return new OpenBundle(selected.summary(), sessionId, selected.bundle(), changedFiles,
                new SFMReviewCommentKernelDataSource(session, store, List.of()), restored, diagnostics);
    }

    private static SFMReviewSessionV1 mergePersisted(SFMReviewSessionV1 generated, SFMReviewSessionV1 persisted) {
        if (!generated.id().equals(persisted.id()))
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                    "Persisted review session id does not match bundle");
        SFMReviewSessionV1.RevisionLane generatedLane = generated.revisionLanes().get(0);
        if (persisted.revisionLanes().size() != 1
                || !generatedLane.before().id().equals(persisted.revisionLanes().get(0).before().id())
                || !generatedLane.after().id().equals(persisted.revisionLanes().get(0).after().id()))
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                    "Persisted review session snapshots do not match bundle");
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>(generated.comments());
        persisted.comments().stream().filter(comment -> !"diff_engine".equals(comment.provenance().kind()))
                .forEach(comments::add);
        return new SFMReviewSessionV1(generated.schema(), generated.id(), generated.title(),
                generated.coordinateSystem(), generated.revisionLanes(), comments, persisted.styleRules(),
                persisted.completionPolicy());
    }

    private Scan scan() {
        if (!Files.isDirectory(inbox)) return new Scan(List.of(), List.of());
        List<Path> paths;
        try (var stream = Files.list(inbox)) {
            paths = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (IOException exception) {
            throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.IO_ERROR,
                    "Unable to enumerate review-bundle inbox " + inbox + ": " + exception.getMessage());
        }
        List<Entry> entries = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path path : paths) {
            try {
                SFMRepositoryReviewBundleV1 bundle = SFMRepositoryReviewBundleV1Codec.parse(path);
                if (!ids.add(bundle.id())) {
                    entries.removeIf(entry -> entry.bundle().id().equals(bundle.id()));
                    throw new IllegalArgumentException("Duplicate bundle id " + bundle.id());
                }
                entries.add(new Entry(path, bundle, summary(bundle)));
            } catch (RuntimeException | IOException exception) {
                String diagnostic = path.getFileName() + ": " + exception.getMessage();
                diagnostics.add(diagnostic);
                SFM.LOGGER.warn("Invalid repository review bundle {}: {}", path, exception.getMessage());
            }
        }
        return new Scan(entries, diagnostics);
    }

    private static BundleSummary summary(SFMRepositoryReviewBundleV1 bundle) {
        return new BundleSummary(bundle.id(), bundle.name(), bundle.repository().name(),
                bundle.before().source().label(), bundle.after().source().label(),
                bundle.comparison().fileChanges().size());
    }

    private record Entry(Path path, SFMRepositoryReviewBundleV1 bundle, BundleSummary summary) {}
    private record Scan(List<Entry> entries, List<String> diagnostics) {
        private Scan { entries = List.copyOf(entries); diagnostics = List.copyOf(diagnostics); }
    }
}
