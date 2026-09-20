package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Provider-neutral facade over the supervised local {@code symbol serve} worker. */
public final class SFMSymbolServerNavigationProvider
        implements SFMSymbolNavigationProvider, SFMSymbolReferenceProvider {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "symbol_server");

    private final SFMSymbolServerSupervisor supervisor;

    public SFMSymbolServerNavigationProvider(SFMSymbolServerSupervisor.Configuration configuration) {
        this(new SFMSymbolServerSupervisor(configuration));
    }

    SFMSymbolServerNavigationProvider(SFMSymbolServerSupervisor supervisor) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean available() {
        return supervisor.lifecycle() != SFMSymbolServerSupervisor.Lifecycle.CLOSED
                && supervisor.lifecycle() != SFMSymbolServerSupervisor.Lifecycle.STOPPING;
    }

    @Override
    public Query query(SFMDefinitionRequest request) {
        SFMSymbolServerSupervisor.Submission submission = supervisor.submit(request);
        return new Query(request, submission.result(), submission.cancellation());
    }

    @Override
    public ReferenceQuery query(SFMUsageAtPositionRequest request) {
        SFMSymbolServerSupervisor.UsageSubmission submission = supervisor.submit(request);
        return new ReferenceQuery(request, submission.result(), submission.cancellation());
    }

    public SFMSymbolServerSupervisor.InteractionMapSubmission queryInteractionMap(
            SFMJavaInteractionMap.Request request
    ) {
        return supervisor.submit(request);
    }

    public CompletableFuture<SFMSymbolServerProtocol.ServerHello> start(Duration timeout) {
        return supervisor.start(timeout);
    }

    public Optional<SFMSymbolServerProtocol.ServerHello> hello() {
        return supervisor.hello();
    }

    public CompletableFuture<Long> ping(Duration timeout) {
        return supervisor.ping(timeout);
    }

    public CompletableFuture<Long> updateWorkspaceGeneration(long generation, Duration timeout) {
        return supervisor.updateWorkspaceGeneration(generation, timeout);
    }

    public SFMSymbolServerSupervisor.Telemetry telemetry() {
        return supervisor.telemetry();
    }

    public CompletableFuture<Void> termination() {
        return supervisor.termination();
    }

    @Override
    public void close() {
        supervisor.close();
    }
}
