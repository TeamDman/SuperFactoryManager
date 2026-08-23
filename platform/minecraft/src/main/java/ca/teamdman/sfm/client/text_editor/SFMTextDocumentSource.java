package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Immutable source descriptor resolved independently for each editor panel. */
public sealed interface SFMTextDocumentSource permits
        SFMTextDocumentSource.Literal,
        SFMTextDocumentSource.PinnedSnapshot,
        SFMTextDocumentSource.ResourceAddress,
        SFMTextDocumentSource.PathAddress {
    int DEFAULT_MAXIMUM_BYTES = 4 * 1024 * 1024;

    CompletableFuture<SFMTextDocumentSnapshot> load(SFMExplorerCancellationToken cancellation);

    record Literal(String text) implements SFMTextDocumentSource {
        public Literal {
            Objects.requireNonNull(text);
        }

        @Override
        public CompletableFuture<SFMTextDocumentSnapshot> load(SFMExplorerCancellationToken cancellation) {
            Objects.requireNonNull(cancellation, "cancellation");
            return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.literal(text));
        }
    }

    /** Immutable addressed bytes already materialized by an owning snapshot/review corpus. */
    record PinnedSnapshot(
            SFMPath path,
            SFMPath authorizedRoot,
            String text,
            String expectedSha256,
            Optional<SFMTextDocumentRange> targetRange,
            Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity,
            Optional<SFMTextDocumentSnapshot.AnalysisIdentity> analysisIdentity
    ) implements SFMTextDocumentSource {
        public PinnedSnapshot {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(expectedSha256, "expectedSha256");
            Objects.requireNonNull(targetRange, "targetRange");
            Objects.requireNonNull(sourceRootIdentity, "sourceRootIdentity");
            Objects.requireNonNull(analysisIdentity, "analysisIdentity");
        }

        public PinnedSnapshot(
                SFMPath path,
                SFMPath authorizedRoot,
                String text,
                String expectedSha256,
                Optional<SFMTextDocumentRange> targetRange,
                Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
        ) {
            this(path, authorizedRoot, text, expectedSha256, targetRange, sourceRootIdentity, Optional.empty());
        }

        @Override
        public CompletableFuture<SFMTextDocumentSnapshot> load(SFMExplorerCancellationToken cancellation) {
            Objects.requireNonNull(cancellation, "cancellation");
            return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.pinned(
                    path, authorizedRoot, text, expectedSha256, targetRange, sourceRootIdentity, analysisIdentity));
        }
    }

    record ResourceAddress(ResourceLocation address) implements SFMTextDocumentSource {
        public ResourceAddress {
            Objects.requireNonNull(address);
        }

        @Override
        public CompletableFuture<SFMTextDocumentSnapshot> load(SFMExplorerCancellationToken cancellation) {
            Objects.requireNonNull(cancellation, "cancellation");
            Map<ResourceLocation, Resource> resources = Minecraft.getInstance().getResourceManager()
                    .listResources(address.getPath(), location -> location.equals(address));
            Resource resource = resources.get(address);
            if (resource == null) {
                return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.literal(
                        "Missing runtime resource: " + address
                ));
            }
            try (BufferedReader reader = resource.openAsReader()) {
                return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.literal(
                        reader.lines().collect(Collectors.joining("\n"))
                ));
            } catch (IOException exception) {
                return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.literal(
                        "Failed to read runtime resource: " + address + "\n" + exception.getMessage()
                ));
            }
        }
    }

    record PathAddress(
            SFMPath path,
            SFMPath authorizedRoot,
            Optional<String> expectedSha256,
            int maximumBytes,
            Optional<SFMTextDocumentRange> targetRange,
            Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
    ) implements SFMTextDocumentSource {
        public PathAddress {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            expectedSha256 = Objects.requireNonNull(expectedSha256, "expectedSha256");
            targetRange = Objects.requireNonNull(targetRange, "targetRange");
            sourceRootIdentity = Objects.requireNonNull(sourceRootIdentity, "sourceRootIdentity");
            // Reuse request validation now so malformed immutable recipes fail
            // before they can be attached to a panel host.
            new SFMResolverTextRequest(
                    path,
                    authorizedRoot,
                    expectedSha256,
                    maximumBytes,
                    0,
                    new SFMExplorerCancellationToken()
            );
        }

        public PathAddress(
                SFMPath path,
                SFMPath authorizedRoot,
                Optional<String> expectedSha256,
                int maximumBytes,
                Optional<SFMTextDocumentRange> targetRange
        ) {
            this(path, authorizedRoot, expectedSha256, maximumBytes, targetRange, Optional.empty());
        }

        public PathAddress(SFMPath path, SFMPath authorizedRoot) {
            this(path, authorizedRoot, Optional.empty(), DEFAULT_MAXIMUM_BYTES, Optional.empty(), Optional.empty());
        }

        public PathAddress withExpectedSha256(String sha256) {
            return new PathAddress(
                    path,
                    authorizedRoot,
                    Optional.of(sha256),
                    maximumBytes,
                    targetRange,
                    sourceRootIdentity
            );
        }

        @Override
        public CompletableFuture<SFMTextDocumentSnapshot> load(SFMExplorerCancellationToken cancellation) {
            Objects.requireNonNull(cancellation, "cancellation");
            SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
            Optional<Long> generation = runtime.resolverGeneration(path.scheme());
            if (generation.isEmpty()) {
                return CompletableFuture.completedFuture(SFMTextDocumentSnapshot.failure(
                        SFMTextDocumentSnapshot.State.UNSUPPORTED_RESOLVER,
                        path,
                        authorizedRoot,
                        java.util.List.of("No explorer resolver is registered for scheme `" + path.scheme() + "`"),
                        sourceRootIdentity
                ));
            }
            SFMResolverTextRequest request = new SFMResolverTextRequest(
                    path,
                    authorizedRoot,
                    expectedSha256,
                    maximumBytes,
                    generation.orElseThrow(),
                    cancellation
            );
            return runtime.readText(request).thenApply(result ->
                    SFMTextDocumentSnapshot.fromResolver(result, targetRange, sourceRootIdentity));
        }
    }
}
