package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightProvider;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Provider-neutral facade over the supervised local {@code syntax serve} worker. */
public final class SFMSyntaxServerHighlightProvider implements SFMSyntaxHighlightProvider {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "syntax_server");

    private final SFMSyntaxServerSupervisor supervisor;

    public SFMSyntaxServerHighlightProvider(SFMSyntaxServerSupervisor.Configuration configuration) {
        this(new SFMSyntaxServerSupervisor(configuration));
    }

    SFMSyntaxServerHighlightProvider(SFMSyntaxServerSupervisor supervisor) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean available() {
        return supervisor.lifecycle() != SFMSyntaxServerSupervisor.Lifecycle.STOPPING
                && supervisor.lifecycle() != SFMSyntaxServerSupervisor.Lifecycle.CLOSED;
    }

    @Override
    public Query query(SFMSyntaxHighlightRequest request) {
        SFMSyntaxServerSupervisor.Submission submission = supervisor.submit(request);
        return new Query(request, submission.result(), submission.cancellation());
    }

    public CompletableFuture<SFMSyntaxServerProtocol.ServerHello> start(Duration timeout) {
        return supervisor.start(timeout);
    }

    /** Explicit user/programmatic retry that bypasses the automatic crash backoff once. */
    public CompletableFuture<SFMSyntaxServerProtocol.ServerHello> retry(Duration timeout) {
        return supervisor.retry(timeout);
    }

    public Optional<SFMSyntaxServerProtocol.ServerHello> hello() {
        return supervisor.hello();
    }

    public SFMSyntaxServerSupervisor.Telemetry telemetry() {
        return supervisor.telemetry();
    }

    public SFMSyntaxServerSupervisor supervisor() {
        return supervisor;
    }

    public CompletableFuture<Void> termination() {
        return supervisor.termination();
    }

    @Override
    public void close() {
        supervisor.close();
    }
}
