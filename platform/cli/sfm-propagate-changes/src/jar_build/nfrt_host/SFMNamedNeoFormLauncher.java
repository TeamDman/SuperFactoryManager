package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.artifacts.Artifact;
import net.neoforged.neoform.runtime.artifacts.ArtifactManager;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.cache.CacheKey;
import net.neoforged.neoform.runtime.cache.CacheManager;
import net.neoforged.neoform.runtime.cache.LauncherInstallations;
import net.neoforged.neoform.runtime.cli.LockManager;
import net.neoforged.neoform.runtime.cli.Main;
import net.neoforged.neoform.runtime.downloads.DownloadManager;
import net.neoforged.neoform.runtime.graph.ExecutionNode;
import net.neoforged.neoform.runtime.graph.NodeState;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Original named-only adapter shape for the exact NFRT 2.0.19 API.
 *
 * This source is staged, not wired into the native executor. It never delegates
 * a request to the inherited downloader, local launcher or artifact-manifest
 * fallback. It is NOT an operating-system network sandbox: child tools still
 * require a separate reviewed containment boundary.
 *
 * The Rust exporter binds inputs to the checked project and original pins.
 * Production execution still requires its owned input store, producer sealing
 * and child-tool handoff. The adapter rehashes each supplied snapshot file.
 *
 * No runnable graph entry point is provided at this checkpoint. In particular,
 * this class deliberately refuses main rather than silently falling back to
 * the upstream launcher while child-network/producer-snapshot gates are open.
 */
public final class SFMNamedNeoFormLauncher {
    private static final String NFRT_SHA256 =
            "6db13ff7efaa70cf076f1f5d6a4f116885a3a5fa4e03960da88b9d92b25089bd";
    private static final long MAX_ARTIFACT_BYTES = 1024L * 1024L * 1024L;

    private SFMNamedNeoFormLauncher() {
    }

    /** Package-local host hook; registration does not itself enable node execution. */
    static void bindFreshProducers(NeoFormEngine engine, SFMFreshProducerBindings.Sealer sealer)
            throws IOException {
        if (!(engine.getArtifactManager() instanceof ExactArtifactManager artifacts)
                || !(engine.getCacheManager() instanceof NoIntermediateCache cache)
                || cache.declaredWorkspaces == null || artifacts.producers != null) {
            throw new IOException("Fresh producers require the owned single-registration engine");
        }
        artifacts.producers = new SFMFreshProducerBindings(
                List.copyOf(engine.getGraph().getNodes()), cache.declaredWorkspaces,
                artifacts.contract.snapshotPaths.keySet(), sealer);
    }

    /** Register graph identities before execution; directory creation remains the Rust host's job. */
    public static Map<String, Path> declareFreshWorkspaces(CacheManager manager, List<ExecutionNode> nodes) {
        if (!(manager instanceof NoIntermediateCache cache)) {
            throw new IllegalArgumentException("Workspace declaration requires the owned closed cache");
        }
        return cache.declareFresh(nodes);
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException(
                "Named NFRT execution is not enabled: child-network containment, " +
                "Rust contract export and fresh producer-output receipts remain open."
        );
    }

    /**
     * Hook-shape object for later reviewed integration, not a launch method.
     * Constructing it does not execute the NFRT CLI or create cache directories.
     */
    public static Main adapterForReviewedContract(LaunchContract contract) {
        return new NamedOnlyMain(Objects.requireNonNull(contract));
    }

    /**
     * One exact caller-supplied immutable snapshot. A full SHA-256 here is the
     * Rust-to-Java transfer identity, not a replacement for an original BLAKE3
     * pin. The Rust exporter must first verify the original parent/pin.
     */
    public record ArtifactPin(
            String coordinate,
            String exactUrl,
            Path snapshotPath,
            long bytes,
            String sha256,
            String origin,
            String authorityIdentity
    ) {
        public ArtifactPin {
            // Minecraft manifest snapshots have no Maven coordinate; locally
            // source-built frozen artifacts may have no download URL. Preserve
            // those identities rather than inventing an acquisition route.
            if (coordinate != null) {
                requireText(coordinate, "coordinate");
            }
            if (exactUrl != null) {
                requireText(exactUrl, "exactUrl");
            }
            if (coordinate == null && exactUrl == null) {
                throw new IllegalArgumentException("Artifact has no exact request identity");
            }
            requireText(origin, "origin");
            requireDigest(sha256, "artifact SHA-256");
            requireDigest(authorityIdentity, "authority identity");
            if (!Set.of(
                    "original_schema2_pin",
                    "captured_schema4_pin",
                    "authenticated_minecraft_child",
                    "approved_nfrt_child"
            ).contains(origin)) {
                throw new IllegalArgumentException("Unknown artifact authority origin");
            }
            if (bytes <= 0 || bytes > MAX_ARTIFACT_BYTES) {
                throw new IllegalArgumentException("Artifact length is unavailable or oversized");
            }
            if (exactUrl != null
                    && (!URI.create(exactUrl).isAbsolute() || !exactUrl.startsWith("https://"))) {
                throw new IllegalArgumentException("Artifact URL is not exact HTTPS");
            }
            snapshotPath = requireAbsoluteNormalized(snapshotPath, "snapshot path");
        }
    }

    /**
     * Exact authenticated Minecraft manifest child, never a live URL lookup.
     * Its download record must match the authenticated version JSON in full.
     */
    public record ManifestChild(String key, MinecraftDownload download, ArtifactPin artifact) {
        public ManifestChild {
            requireText(key, "manifest child key");
            Objects.requireNonNull(download);
            Objects.requireNonNull(artifact);
            if (!download.uri().toString().equals(artifact.exactUrl())
                    || download.size() != artifact.bytes()
                    || !"authenticated_minecraft_child".equals(artifact.origin())) {
                throw new IllegalArgumentException("Manifest child lost its authenticated identity");
            }
        }
    }

    /**
     * Read-only in-memory input shape. It is not a permission claim or a schema2
     * lock writer. Production may only instantiate it through a reviewed Rust
     * exporter; no general user-path manifest or CLI parser is implemented.
     */
    public static final class LaunchContract {
        private final String targetId;
        private final String minecraftVersion;
        private final int compilerRelease;
        private final int minimumToolJvm;
        private final String projectIdentity;
        private final String sourceLockSha256;
        private final String supplementSha256;
        private final Path invocationRoot;
        private final Path home;
        private final Path work;
        private final ArtifactPin versionManifest;
        private final Map<String, ArtifactPin> coordinates;
        private final Map<Path, ArtifactPin> snapshotPaths;
        private final Map<String, ManifestChild> libraries;
        private final Map<String, ManifestChild> downloads;
        private final List<String> frozenRuntimeCoordinates;

        public LaunchContract(
                String targetId,
                String minecraftVersion,
                int compilerRelease,
                int minimumToolJvm,
                String projectIdentity,
                String sourceLockSha256,
                String supplementSha256,
                Path invocationRoot,
                ArtifactPin versionManifest,
                List<ArtifactPin> exactArtifacts,
                List<ManifestChild> libraries,
                List<ManifestChild> downloads,
                List<String> frozenRuntimeCoordinates
        ) {
            requireText(targetId, "target");
            requireText(minecraftVersion, "Minecraft version");
            int expectedCompiler = switch (targetId) {
                case "1.20.2", "1.20.3", "1.20.4" -> 17;
                case "1.21.0", "1.21.1" -> 21;
                case "26.1.2" -> 25;
                default -> throw new IllegalArgumentException("Unreviewed named NFRT target");
            };
            String upstream = "1.21.0".equals(targetId) ? "1.21" : targetId;
            if (!upstream.equals(minecraftVersion)
                    || compilerRelease != expectedCompiler
                    || minimumToolJvm != ("26.1.2".equals(targetId) ? 25 : 21)) {
                throw new IllegalArgumentException("Compiler release/tool-JVM/context mismatch");
            }
            requireDigest(projectIdentity, "project identity");
            requireDigest(sourceLockSha256, "source-lock identity");
            requireDigest(supplementSha256, "supplement identity");
            this.targetId = targetId;
            this.minecraftVersion = minecraftVersion;
            this.compilerRelease = compilerRelease;
            this.minimumToolJvm = minimumToolJvm;
            this.projectIdentity = projectIdentity;
            this.sourceLockSha256 = sourceLockSha256;
            this.supplementSha256 = supplementSha256;
            this.invocationRoot = requireAbsoluteNormalized(invocationRoot, "invocation root");
            this.home = this.invocationRoot.resolve("home");
            this.work = this.invocationRoot.resolve("work");
            this.versionManifest = Objects.requireNonNull(versionManifest);
            if (!Set.of("original_schema2_pin", "captured_schema4_pin").contains(versionManifest.origin())) {
                throw new IllegalArgumentException("Version JSON is not an original frozen pin");
            }
            var byCoordinate = new LinkedHashMap<String, ArtifactPin>();
            var byPath = new LinkedHashMap<Path, ArtifactPin>();
            for (ArtifactPin artifact : exactArtifacts) {
                if (Set.of("original_schema2_pin", "captured_schema4_pin").contains(artifact.origin())
                        && (!artifact.origin().equals(versionManifest.origin())
                            || !artifact.authorityIdentity().equals(versionManifest.authorityIdentity()))) {
                    throw new IllegalArgumentException("Mixed source catalog authorities");
                }
                if ((artifact.coordinate() != null
                        && byCoordinate.putIfAbsent(artifact.coordinate(), artifact) != null)
                        || byPath.putIfAbsent(artifact.snapshotPath(), artifact) != null) {
                    throw new IllegalArgumentException("Ambiguous snapshot coordinate/path");
                }
            }
            ArtifactPin previousVersionPath = byPath.putIfAbsent(versionManifest.snapshotPath(), versionManifest);
            if (previousVersionPath != null && !previousVersionPath.equals(versionManifest)) {
                throw new IllegalArgumentException("Version JSON snapshot path has conflicting identities");
            }
            for (ArtifactPin artifact : byPath.values()) {
                if (!artifact.snapshotPath().startsWith(this.invocationRoot.resolve("inputs"))) {
                    throw new IllegalArgumentException("Snapshot is outside the owned invocation input root");
                }
            }
            this.coordinates = Map.copyOf(byCoordinate);
            this.snapshotPaths = Map.copyOf(byPath);
            this.libraries = checkedChildren(libraries, "library");
            this.downloads = checkedChildren(downloads, "download");
            for (ManifestChild child : this.libraries.values()) {
                requireBoundChild(child, this.snapshotPaths);
            }
            for (ManifestChild child : this.downloads.values()) {
                requireBoundChild(child, this.snapshotPaths);
            }
            // Preserve rows and order exactly, including legitimate repeated
            // configuration consumers. Do not infer POM parent edges here.
            this.frozenRuntimeCoordinates = List.copyOf(frozenRuntimeCoordinates);
            this.frozenRuntimeCoordinates.forEach(value -> requireText(value, "runtime coordinate"));
        }

        private ArtifactPin exactCoordinate(String coordinate) throws IOException {
            ArtifactPin pin = coordinates.get(coordinate);
            if (pin == null) {
                throw refused("Unapproved exact artifact coordinate", coordinate);
            }
            return pin;
        }

        private ArtifactPin exactSnapshotPath(Path path) throws IOException {
            ArtifactPin pin = snapshotPaths.get(path);
            if (pin == null) {
                throw refused("Unapproved path classpath item", path.toString());
            }
            return pin;
        }
    }

    private static final class NamedOnlyMain extends Main {
        private final LaunchContract contract;

        private NamedOnlyMain(LaunchContract contract) {
            this.contract = contract;
            if (Runtime.version().feature() < contract.minimumToolJvm) {
                throw new IllegalArgumentException("Actual tool JVM is below the named recipe minimum");
            }
        }

        @Override
        public Path getWorkDir() {
            return contract.work;
        }

        @Override
        public List<URI> getEffectiveRepositories() {
            return List.of();
        }

        @Override
        public LauncherInstallations createLauncherInstallations() throws IOException {
            return new NoLauncherProbing();
        }

        @Override
        public CacheManager createCacheManager() throws IOException {
            return new NoIntermediateCache(contract);
        }

        @Override
        public LockManager createLockManager() throws IOException {
            return new NoLockMaintenance(contract.home);
        }

        @Override
        public ArtifactManager createArtifactManager(
                CacheManager cacheManager,
                DownloadManager unusedInheritedDownloader,
                LockManager lockManager,
                LauncherInstallations launcherInstallations
        ) {
            return new ExactArtifactManager(
                    contract, cacheManager, unusedInheritedDownloader,
                    lockManager, launcherInstallations
            );
        }
    }

    private static final class NoLauncherProbing extends LauncherInstallations {
        private NoLauncherProbing() throws IOException {
            super(List.of());
        }

        @Override
        public List<Path> getInstallationRoots() {
            return List.of();
        }

        @Override
        public List<Path> getAssetRoots() {
            return List.of();
        }

        @Override
        public Path getAssetDirectoryForIndex(String ignored) {
            throw new IllegalArgumentException("Asset/launcher discovery is outside named Compile: " + ignored);
        }
    }

    private static final class NoLockMaintenance extends LockManager {
        private NoLockMaintenance(Path ownedHome) throws IOException {
            super(ownedHome);
        }

        @Override
        public void performMaintenance() {
            // Never clean or touch legacy/shared lock files.
        }
    }

    private static final class NoIntermediateCache extends CacheManager {
        private final Path ownedWork;
        private Map<String, Path> declaredWorkspaces;
        private final Set<String> issuedWorkspaces = new java.util.HashSet<>();

        private NoIntermediateCache(LaunchContract contract) throws IOException {
            super(contract.home, contract.home.resolve("unused-assets"), contract.work);
            ownedWork = contract.work;
            super.setDisabled(true);
        }

        private synchronized Map<String, Path> declareFresh(List<ExecutionNode> nodes) {
            if (declaredWorkspaces != null || nodes.isEmpty() || nodes.size() > 256) {
                throw new IllegalArgumentException("Workspace graph missing, oversized or already registered");
            }
            var paths = new LinkedHashMap<String, Path>();
            var caseIds = new java.util.HashSet<String>();
            for (var node : nodes) {
                if (node.getState() != NodeState.NOT_STARTED
                        || !node.id().matches("[A-Za-z0-9_-]{1,128}")
                        || !caseIds.add(node.id().toLowerCase(java.util.Locale.ROOT))
                        || paths.putIfAbsent(node.id(), ownedWork.resolve(node.id())) != null) {
                    throw new IllegalArgumentException("Workspace graph is not fresh, safe and unambiguous");
                }
            }
            declaredWorkspaces = Map.copyOf(paths);
            return declaredWorkspaces;
        }

        @Override
        public synchronized Path createWorkspace(String nodeId) throws IOException {
            Path path = declaredWorkspaces == null ? null : declaredWorkspaces.get(nodeId);
            if (path == null || issuedWorkspaces.contains(nodeId)) {
                throw new IOException("Workspace is undeclared or already issued: " + nodeId);
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Rust-owned declared workspace does not exist: " + nodeId);
            }
            requireNoSymbolicLinks(path);
            issuedWorkspaces.add(nodeId);
            return path;
        }

        @Override
        public boolean isDisabled() {
            return true;
        }

        @Override
        public void setDisabled(boolean disabled) {
            if (!disabled) {
                throw new IllegalArgumentException("Named NFRT intermediate cache must remain disabled");
            }
            super.setDisabled(true);
        }

        @Override
        public void setAnalyzeMisses(boolean enabled) {
            if (enabled) {
                throw new IllegalArgumentException("Named NFRT cache-miss probing is disabled");
            }
            super.setAnalyzeMisses(false);
        }

        @Override
        public Path getAssetsDir() {
            throw new IllegalArgumentException("Assets are outside named Compile");
        }

        @Override
        public void performMaintenance() {
        }

        @Override
        public void cleanUpAll() throws IOException {
            throw new IOException("Cache cleanup is not authorized by named Compile");
        }

        @Override
        public void cleanUpIntermediateResults() throws IOException {
            throw new IOException("Intermediate cache cleanup is not authorized");
        }

        @Override
        public boolean restoreOutputsFromCache(
                ExecutionNode node,
                CacheKey key,
                Map<String, Path> outputValues
        ) {
            return false;
        }

        @Override
        public void saveOutputs(
                ExecutionNode node,
                CacheKey key,
                HashMap<String, Path> outputValues
        ) {
            // No inherited existence/marker-based output persistence. A future
            // separate producer receipt may record fresh outputs, but the
            // initial classpath adapter deliberately refuses NodeOutputItem.
        }
    }

    private static final class ExactArtifactManager extends ArtifactManager {
        private final LaunchContract contract;
        private MinecraftVersionManifest authenticatedVersion;
        private SFMFreshProducerBindings producers;

        private ExactArtifactManager(
                LaunchContract contract,
                CacheManager cache,
                DownloadManager unusedInheritedDownloader,
                LockManager locks,
                LauncherInstallations installations
        ) {
            super(
                    List.of(), cache, unusedInheritedDownloader, locks,
                    URI.create("https://invalid.invalid/no-launcher-discovery"), installations
            );
            this.contract = contract;
        }

        @Override
        public Artifact get(String location) throws IOException {
            // Do not parse-and-fall-back to a local path, URL or wildcard.
            return verified(contract.exactCoordinate(location));
        }

        @Override
        public Artifact get(MavenCoordinate coordinate) throws IOException {
            return get(coordinate.toString());
        }

        @Override
        public Artifact get(String location, URI repositoryBaseUrl) throws IOException {
            ArtifactPin pin = contract.exactCoordinate(location);
            requireExactRepository(MavenCoordinate.parse(location), repositoryBaseUrl, pin);
            return verified(pin);
        }

        @Override
        public Artifact get(MavenCoordinate coordinate, URI repositoryBaseUrl) throws IOException {
            return get(coordinate.toString(), repositoryBaseUrl);
        }

        @Override
        public Artifact get(MinecraftLibrary library) throws IOException {
            // Lookup and semantic validation happen before any file read.
            String coordinate = library.getMavenCoordinate().toString();
            ManifestChild child = contract.libraries.get(coordinate);
            if (child == null || !Objects.equals(library.getArtifactDownload(), child.download())) {
                throw refused("Unapproved authenticated Minecraft library", coordinate);
            }
            if (!version().libraries().contains(library)) {
                throw refused("Library did not originate in the exact version JSON", coordinate);
            }
            return verified(child.artifact());
        }

        @Override
        public List<Path> resolveClasspath(Collection<ClasspathItem> items) throws IOException {
            // Entire request sequence is preflighted before a single snapshot
            // byte is read. Preserve input order and duplicate consumers.
            List<ArtifactPin> selected = new ArrayList<>(items.size());
            List<ClasspathItem.NodeOutputItem> generated = new ArrayList<>();
            List<Integer> generatedPositions = new ArrayList<>();
            for (ClasspathItem item : items) {
                if (item instanceof ClasspathItem.MavenCoordinateItem maven) {
                    String coordinate = maven.coordinate().toString();
                    ArtifactPin pin = contract.coordinates.get(coordinate);
                    // NeoForm's source library list represents some version-
                    // manifest libraries as Maven items. Resolve only the exact
                    // already authenticated child, never a repository fallback.
                    if (pin == null) {
                        ManifestChild child = contract.libraries.get(coordinate);
                        if (child == null) {
                            throw refused("Unapproved exact classpath coordinate", coordinate);
                        }
                        pin = child.artifact();
                    }
                    requireExactRepository(maven.coordinate(), maven.repositoryBaseUrl(), pin);
                    selected.add(pin);
                } else if (item instanceof ClasspathItem.MinecraftLibraryItem library) {
                    String coordinate = library.library().getMavenCoordinate().toString();
                    ManifestChild child = contract.libraries.get(coordinate);
                    if (child == null
                            || !Objects.equals(library.library().getArtifactDownload(), child.download())) {
                        throw refused("Unapproved library classpath item", coordinate);
                    }
                    selected.add(child.artifact());
                } else if (item instanceof ClasspathItem.PathItem path) {
                    selected.add(contract.exactSnapshotPath(path.path()));
                } else if (item instanceof ClasspathItem.NodeOutputItem output) {
                    if (producers == null) {
                        throw new IOException("Fresh NodeOutput needs the Rust-owned producer sealer");
                    }
                    generated.add(output);
                    generatedPositions.add(selected.size());
                    selected.add(null);
                } else {
                    throw new IOException("Unreviewed classpath item type");
                }
            }
            // Reject the entire producer batch before any input read or host request.
            var preflighted = generated.isEmpty() ? List.<SFMFreshProducerBindings.Request>of()
                    : producers.preflight(generated);
            var requests = new ArrayList<SFMFreshProducerBindings.Request>();
            for (int index = 0; index < preflighted.size(); index++) {
                var request = preflighted.get(index);
                if (request.originalInput()) {
                    selected.set(generatedPositions.get(index), contract.exactSnapshotPath(request.outputPath()));
                } else {
                    requests.add(request);
                }
            }
            // Authentication of library entries uses one already selected
            // version JSON, never launcher discovery or live metadata.
            if (items.stream().anyMatch(item -> item instanceof ClasspathItem.MinecraftLibraryItem)) {
                MinecraftVersionManifest version = version();
                for (ClasspathItem item : items) {
                    if (item instanceof ClasspathItem.MinecraftLibraryItem library
                            && !version.libraries().contains(library.library())) {
                        throw new IOException("Library classpath is detached from version JSON");
                    }
                }
            }
            List<Path> result = new ArrayList<>(selected.size());
            var snapshots = requests.isEmpty() ? List.<SFMFreshProducerBindings.Snapshot>of()
                    : producers.seal(requests);
            // Validate every returned path before opening any generated snapshot.
            Path generatedRoot = contract.invocationRoot.resolve("inputs/generated");
            var generatedIdentities = new HashMap<Path, String>();
            for (var snapshot : snapshots) {
                if (snapshot.path() == null || snapshot.bytes() <= 0 || snapshot.bytes() > MAX_ARTIFACT_BYTES
                        || snapshot.sha256() == null || !snapshot.sha256().matches("[a-f0-9]{64}")
                        || !snapshot.path().isAbsolute() || !snapshot.path().normalize().equals(snapshot.path())
                        || !generatedRoot.equals(snapshot.path().getParent())
                        || !snapshot.path().getFileName().toString().matches("[0-9]{4}\\.bin")) {
                    throw new IOException("Invalid Rust-owned generated snapshot response");
                }
                String previous = generatedIdentities.putIfAbsent(snapshot.path(),
                        snapshot.sha256() + ":" + snapshot.bytes());
                if (previous != null && !previous.equals(snapshot.sha256() + ":" + snapshot.bytes())) {
                    throw new IOException("Repeated generated snapshot has conflicting identities");
                }
            }
            int generatedIndex = 0;
            for (ArtifactPin pin : selected) {
                if (pin != null) {
                    result.add(verified(pin).path());
                } else {
                    var snapshot = snapshots.get(generatedIndex++);
                    result.add(verifiedSnapshot(snapshot.path(), snapshot.bytes(), snapshot.sha256(),
                            "fresh producer").path());
                }
            }
            return List.copyOf(result);
        }

        @Override
        public Artifact getVersionManifest(String minecraftVersion) throws IOException {
            if (!contract.minecraftVersion.equals(minecraftVersion)) {
                throw refused("Unapproved actual Minecraft version", minecraftVersion);
            }
            return verified(contract.versionManifest);
        }

        @Override
        public Artifact getLauncherManifest() throws IOException {
            throw new IOException("Launcher version discovery is not authorized");
        }

        @Override
        public Artifact downloadFromManifest(
                MinecraftVersionManifest supplied,
                String type
        ) throws IOException {
            ManifestChild child = contract.downloads.get(type);
            if (child == null || !contract.minecraftVersion.equals(supplied.id())
                    || !Objects.equals(supplied.downloads().get(type), child.download())) {
                throw refused("Unapproved Minecraft manifest child", type);
            }
            if (!version().equals(supplied)) {
                throw new IOException("Supplied manifest differs from the exact authenticated version JSON");
            }
            return verified(child.artifact());
        }

        @Override
        public void loadArtifactManifest(Path ignored) throws IOException {
            throw new IOException("Inherited mutable/wildcard artifact manifests are disabled");
        }

        @Override
        public void setWarnOnArtifactManifestMiss(boolean ignored) {
            throw new IllegalArgumentException("Warning-based manifest fallback is disabled");
        }

        private synchronized MinecraftVersionManifest version() throws IOException {
            // Rehash the original version JSON each time even when its parsed
            // value is cached. This does not make later path opens atomic.
            Artifact versionArtifact = verified(contract.versionManifest);
            if (authenticatedVersion == null) {
                authenticatedVersion = MinecraftVersionManifest.from(versionArtifact.path());
            }
            if (!contract.minecraftVersion.equals(authenticatedVersion.id())) {
                throw new IOException("Authenticated version JSON has unexpected upstream ID");
            }
            return authenticatedVersion;
        }
    }

    private static Artifact verified(ArtifactPin pin) throws IOException {
        return verifiedSnapshot(pin.snapshotPath(), pin.bytes(), pin.sha256(), pin.coordinate());
    }

    private static Artifact verifiedSnapshot(Path path, long expectedBytes, String expectedSha256,
            String description) throws IOException {
        requireNoSymbolicLinks(path);
        BasicFileAttributes before = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (!before.isRegularFile() || before.size() != expectedBytes) {
            throw refused("Snapshot type/length differs from contract", description);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
        long readBytes = 0;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                readBytes = Math.addExact(readBytes, count);
                if (readBytes > expectedBytes) {
                    throw new IOException("Snapshot grew while hashing");
                }
                digest.update(buffer, 0, count);
            }
        }
        BasicFileAttributes after = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (readBytes != expectedBytes
                || !HexFormat.of().formatHex(digest.digest()).equals(expectedSha256)
                || after.size() != before.size()
                || !Objects.equals(after.fileKey(), before.fileKey())
                || !after.lastModifiedTime().equals(before.lastModifiedTime())) {
            throw refused("Snapshot full SHA-256 or read identity changed", description);
        }
        return new Artifact(path, after.lastModifiedTime().toMillis(), after.size());
    }

    private static void requireExactRepository(
            MavenCoordinate coordinate,
            URI repositoryBaseUrl,
            ArtifactPin pin
    ) throws IOException {
        if (repositoryBaseUrl != null
                && !coordinate.toRepositoryUri(repositoryBaseUrl).toString().equals(pin.exactUrl())) {
            throw refused("Repository override differs from the exact artifact URL", coordinate.toString());
        }
    }

    private static void requireNoSymbolicLinks(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic-link snapshot ancestry is not allowed");
            }
        }
        if (!path.toRealPath().equals(path)) {
            throw new IOException("Snapshot path changed under real-path resolution");
        }
        // This is not a transactional Windows reparse/TOCTOU proof. The future
        // Rust exporter must independently reject all reparse ancestors and
        // own immutable private input snapshots before Java startup.
    }

    private static Map<String, ManifestChild> checkedChildren(
            List<ManifestChild> children,
            String kind
    ) {
        var result = new LinkedHashMap<String, ManifestChild>();
        for (ManifestChild child : children) {
            if (result.putIfAbsent(child.key(), child) != null) {
                throw new IllegalArgumentException("Duplicate " + kind + " child identity");
            }
        }
        return Map.copyOf(result);
    }

    private static void requireBoundChild(ManifestChild child, Map<Path, ArtifactPin> snapshots) {
        if (!Objects.equals(snapshots.get(child.artifact().snapshotPath()), child.artifact())) {
            throw new IllegalArgumentException("Manifest child has no exact bound input snapshot");
        }
    }

    private static Path requireAbsoluteNormalized(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException(field + " must already be an exact absolute normalized path");
        }
        return path;
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " is not an exact SHA-256");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isEmpty() || value.length() > 4096
                || value.codePoints().anyMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException(field + " is empty, oversized or contains whitespace/control text");
        }
    }

    private static IOException refused(String reason, String request) {
        return new IOException(reason + ": " + request);
    }
}
