package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
{% if features.structured_action_results %}
import ca.teamdman.sfm.client.action.SFMClientActionStructuredResult;
{% endif %}
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Client-owned execution and transport lifetime. No puppet or screen owns this runtime. */
public final class SFMClientActionRuntime implements AutoCloseable {
    public enum Transport { FILES, VOX }
    public record Session(Transport transport, String generation) { }
    public record ActionResult(int code, List<String> feedback, String schema, String json,
                               boolean screenPresent, String screenClass) { }

    private final Minecraft minecraft;
    private final String instanceId = UUID.randomUUID().toString();
    private final SFMActionArbiter arbiter = new SFMActionArbiter();
    private final SFMActionWorkTracker puppetWork = new SFMActionWorkTracker();
    private java.util.function.BooleanSupplier puppetQuiescent = () -> true;
    private final ExecutorService completions = Executors.newSingleThreadExecutor(task -> daemon(task, "sfm-action-completion"));
    private final ExecutorService filesIo = Executors.newSingleThreadExecutor(task -> daemon(task, "sfm-action-files"));
    private final SFMClientThreadGate gate;
    private final Set<CompletableFuture<?>> fileTasks = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> voxTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final ThreadLocal<Session> caller = new ThreadLocal<>();
    private record Admission(long puppetRevision, long sceneRevision) { }
    private final ThreadLocal<Admission> admission = new ThreadLocal<>();
    private final java.util.concurrent.atomic.AtomicLong sceneRevision = new java.util.concurrent.atomic.AtomicLong();
    private volatile Session filesSession;
    private volatile Session voxSession;
    private volatile SFMClientControlFileBridge files;
    private volatile SFMClientControlServer vox;
    private volatile boolean closed;
    private long nextFilePoll;

    SFMClientActionRuntime(Minecraft minecraft) {
        this.minecraft = minecraft;
        gate = new SFMClientThreadGate(minecraft::execute, completions, 32);
{% if features.client_log_read_actions %}
        SFMClientControlLogBuffer.install();
{% endif %}
    }

    String instanceId() { return instanceId; }
    public SFMActionWorkTracker puppetWork() { return puppetWork; }

    public synchronized Path enableFiles() throws IOException {
        requireOpen();
        if (files != null) return files.directory();
        Session session = new Session(Transport.FILES, UUID.randomUUID().toString());
        Path directory = minecraft.gameDirectory.toPath().resolve("sfm-control")
                .resolve(instanceId).resolve(session.generation());
        SFMClientControlFileBridge bridge = SFMClientControlFileBridge.openAsync(directory,
                command -> submit(session, () -> fileResult(execute(command))));
        filesSession = session;
        files = bridge;
        SFM.LOGGER.info("SFM_CLIENT_CONTROL_FILE_BRIDGE_READY directory={}", directory);
        return directory;
    }

    public synchronized void enableVox() throws IOException {
        requireOpen();
        if (vox != null) return;
        Session session = new Session(Transport.VOX, UUID.randomUUID().toString());
        voxSession = session;
        try {
            vox = SFMClientControlServer.start(minecraft, this, session);
        } catch (IOException | RuntimeException failure) {
            voxSession = null;
            throw failure;
        }
    }

    public synchronized void disable(Transport transport) {
        Session invoking = caller.get();
        if (invoking != null && invoking.transport() == transport) {
            throw new IllegalStateException("Disable this transport from the in-game palette or the other enabled transport; the current request remains acknowledged");
        }
        Session ending = transport == Transport.FILES ? filesSession : voxSession;
        if (ending == null) return;
        if (transport == Transport.FILES) {
            filesSession = null;
            SFMClientControlFileBridge bridge = files;
            files = null;
            // Disk cleanup must not block the Minecraft thread.
            filesIo.execute(() -> {
                bridge.close();
                if (bridge.publicationFailure() != null) {
                    SFM.LOGGER.error("SFM_ACTION_FILES_CLOSE_FAILED directory={}", bridge.directory(), bridge.publicationFailure());
                }
            });
        } else {
            voxSession = null;
            SFMClientControlServer stopping = vox;
            vox = null;
            stopping.close();
        }
        tasks(transport).forEach(task -> task.cancel(false));
        arbiter.release(ending.generation());
        SFMClientCaptureService.cancelOwner(ending.generation());
    }

    public synchronized JsonObject status() {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "sfm.action-services/1");
        result.addProperty("instance_id", instanceId);
        result.addProperty("process_id", ProcessHandle.current().pid());
        result.addProperty("files_enabled", filesSession != null);
        result.addProperty("vox_enabled", voxSession != null);
        result.addProperty("files_directory", files == null ? "" : files.directory().toString());
        result.addProperty("puppet_execution_id", arbiter.puppetId());
        result.addProperty("puppet_name", arbiter.puppetName());
        result.addProperty("puppet_pause_available", !arbiter.puppetId().isEmpty() && puppetQuiescent.getAsBoolean());
        result.addProperty("mutation_owner", arbiter.owner(System.nanoTime()));
        return result;
    }

    void tick() {
        if (closed) return;
        SFMClientCaptureService.tick();
        SFMClientControlServer activeVox = vox;
        if (activeVox != null) activeVox.observeClientTick();
        SFMClientControlFileBridge bridge = files;
        long now = System.nanoTime();
        if (bridge == null || now < nextFilePoll || !polling.compareAndSet(false, true)) return;
        nextFilePoll = now + 100_000_000L;
        try {
            filesIo.execute(() -> {
            try {
                bridge.poll();
                if (bridge.publicationFailure() != null) SFM.LOGGER.warn("SFM_ACTION_FILE_RECEIPT_PENDING directory={} failure={}",
                        bridge.directory(), bridge.publicationFailure().toString());
            } finally { polling.set(false); }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            polling.set(false);
            if (!closed) throw stopped;
        }
    }

    <T> CompletableFuture<T> submit(Session session, Supplier<T> action) {
        Admission accepted = new Admission(arbiter.revision(), sceneRevision.get());
        CompletableFuture<T> future = gate.submit(() -> {
            if (!current(session)) throw new IllegalStateException("cancelled: transport session is no longer active");
            caller.set(session);
            admission.set(accepted);
            try { return action.get(); } finally { caller.remove(); admission.remove(); }
        });
        Set<CompletableFuture<?>> pending = tasks(session.transport());
        pending.add(future);
        future.whenComplete((result, failure) -> pending.remove(future));
        // Disable can race worker admission before the future joins this transport's set.
        if (!current(session)) future.cancel(false);
        return future;
    }

    ActionResult execute(String command) {
        if (!command.startsWith("sfm action invoke sfm:control/")) requireMutation();
        var origin = minecraft.screen;
        List<String> feedback = new ArrayList<>();
{% if features.structured_action_results %}
        AtomicReference<SFMClientActionStructuredResult> structured = new AtomicReference<>();
{% endif %}
        int code;
        try {
            code = SFMClientActionExecutor.execute(command,
                    SFMClientActionContext.create(origin, () -> minecraft.screen == origin),
                    component -> { if (feedback.size() < 64) feedback.add(component.getString()); }
{% if features.structured_action_results %}
                    , value -> {
                        if (!structured.compareAndSet(null, value)) throw new IllegalStateException("A client action may publish at most one structured result");
                    }
{% endif %}
            );
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
        var screen = minecraft.screen;
        return new ActionResult(code, List.copyOf(feedback),
{% if features.structured_action_results %}
                structured.get() == null ? "" : structured.get().schemaId(),
                structured.get() == null ? "" : structured.get().json(),
{% else %}
                "", "",
{% endif %}
                screen != null, screen == null ? "" : screen.getClass().getName());
    }

    public void requireMutation() {
        requireCurrentTarget();
        if (!puppetWork.isIdle()) throw new IllegalStateException("busy: previously dispatched puppet mutations are still pending");
        arbiter.requireMutation(callerId(), null, System.nanoTime());
    }
    public void sceneChanging() { sceneRevision.incrementAndGet(); }
    public long sceneRevision() { return sceneRevision.get(); }
    public void requireCurrentTarget() {
        Admission accepted = admission.get();
        if (accepted == null) return; // The palette already executes on the client thread.
        arbiter.requireRevision(accepted.puppetRevision());
        if (accepted.sceneRevision() != sceneRevision.get()) {
            throw new IllegalStateException("stale_target: screen changed after admission");
        }
    }
    public String callerId() { Session session = caller.get(); return session == null ? "palette" : session.generation(); }
    public void pausePuppet(String expectedId, int seconds) { arbiter.pause(callerId(), expectedId, System.nanoTime(), seconds * 1_000_000_000L, puppetQuiescent.getAsBoolean()); }
    public void resumePuppet(String expectedId) { arbiter.resume(callerId(), expectedId, System.nanoTime()); }
    public void observePuppet(Object identity, String name, java.util.function.BooleanSupplier quiescent) {
        arbiter.observePuppet(identity, name);
        puppetQuiescent = quiescent;
    }
    public boolean mayTickPuppet() { return arbiter.mayTickPuppet(System.nanoTime()); }

    private boolean current(Session session) {
        return session.equals(session.transport() == Transport.FILES ? filesSession : voxSession);
    }
    private Set<CompletableFuture<?>> tasks(Transport transport) { return transport == Transport.FILES ? fileTasks : voxTasks; }
    private void requireOpen() { if (closed) throw new IllegalStateException("Action services are shutting down"); }

    private static JsonObject fileResult(ActionResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("result_code", result.code());
        JsonArray feedback = new JsonArray();
        result.feedback().forEach(feedback::add);
        json.add("feedback", feedback);
        if (!result.schema().isEmpty()) {
            json.addProperty("structured_result_schema", result.schema());
            json.add("structured_result", com.google.gson.JsonParser.parseString(result.json()));
        }
        json.addProperty("screen", result.screenClass());
        return json;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        disable(Transport.FILES);
        disable(Transport.VOX);
        gate.close();
        filesIo.shutdown();
        completions.shutdown();
        // close() is called by the JVM hook, not the palette. Daemon cleanup must
        // finish before that hook returns; palette disable remains asynchronous.
        try {
            if (!filesIo.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                SFM.LOGGER.error("SFM_ACTION_FILES_SHUTDOWN_TIMEOUT: ready marker/receipts may remain stale");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            SFM.LOGGER.error("SFM_ACTION_FILES_SHUTDOWN_INTERRUPTED", interrupted);
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
