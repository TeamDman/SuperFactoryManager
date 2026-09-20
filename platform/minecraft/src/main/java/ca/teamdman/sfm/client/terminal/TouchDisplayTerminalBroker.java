package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterFrame;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterInbox;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-thread, local-session owner. The service's destructive frame handoff
 * is consumed once and fanned out to exact, independently revocable mounts.
 * Nothing here opens a screen or interprets packet items as permission.
 */
public final class TouchDisplayTerminalBroker implements AutoCloseable {
    public static final ResourceLocation SESSION = new ResourceLocation("sfm", "terminal/session");
    public static final ResourceLocation READ = new ResourceLocation("sfm", "terminal/raster/read");
    public static final ResourceLocation INPUT = new ResourceLocation("sfm", "terminal/input");
    public static final int MAX_SESSIONS = 4;
    public static final int MAX_MOUNTS = 16;

    @FunctionalInterface
    public interface Permissions {
        /** Must include current source/bindings, world, execution, and local/server policy. */
        boolean allowed(ClientProgramIdentity owner, ResourceLocation capability);
    }

    public interface Transport extends AutoCloseable {
        void start();
        void pump();
        boolean active();
        boolean ready();
        Optional<TouchDisplayRasterFrame> latestFrame();
        boolean touch(long id, double u, double v);
        @Override void close();
    }

    public record Scope(ClientProgramIdentity identity, TouchDisplayRasterInbox.Display display, long generation) {
        public Scope {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(display);
            if (generation < 1 || !display.world().equals(identity.world().worldId())
                    || !display.dimension().equals(identity.dimension().toString())) {
                throw new IllegalArgumentException("Terminal mount must match its exact world and generation");
            }
        }
    }

    public interface RasterTarget extends AutoCloseable {
        Scope scope();
        /** Checks the actual block entity, fresh presentation identity and opaque raster lease. */
        boolean current();
        TouchDisplayRasterInbox.OfferResult offer(TouchDisplayRasterFrame frame);
        @Override void close();
    }

    public static final class Session {
        private final ClientProgramIdentity owner;
        private final Transport transport;
        private final List<Mount> mounts = new ArrayList<>();
        private @Nullable TouchDisplayRasterFrame latest;
        private @Nullable InputLease input;
        private long nextId = 1;
        private boolean closed;
        private @Nullable String failure;

        private Session(ClientProgramIdentity owner, Transport transport) {
            this.owner = owner;
            this.transport = transport;
        }

        public ClientProgramIdentity owner() { return owner; }
        public boolean closed() { return closed; }
        /** Stable bounded diagnostic only; never terminal content or exception text. */
        public Optional<String> failure() { return Optional.ofNullable(failure); }
    }

    public static final class Mount {
        private final Session session;
        private final RasterTarget target;
        private final Scope scope;
        private long delivered = -1;
        private boolean closed;

        private Mount(Session session, RasterTarget target) {
            this.session = session;
            this.target = target;
            this.scope = target.scope();
        }

        public Scope scope() { return scope; }
        public boolean closed() { return closed; }
    }

    /** Opaque single-input-writer lease; no deserializer or packet path can create one. */
    public static final class InputLease {
        private final Mount mount;
        private InputLease(Mount mount) { this.mount = mount; }
    }

    private final Permissions permissions;
    private final Thread ownerThread = Thread.currentThread();
    private final List<Session> sessions = new ArrayList<>();
    private boolean closed;

    public TouchDisplayTerminalBroker(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions);
    }

    /** Explicit local creation only. The factory is not evaluated without permission. */
    public Optional<Session> create(ClientProgramIdentity owner, Supplier<? extends Transport> factory) {
        checkThread();
        if (closed || sessions.size() >= MAX_SESSIONS || !allowed(owner, SESSION)) return Optional.empty();
        Transport transport = Objects.requireNonNull(factory.get());
        Session session = new Session(owner, transport);
        sessions.add(session);
        try {
            transport.start();
            if (!transport.active()) {
                close(session);
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (RuntimeException failure) {
            try { close(session); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public List<Session> sessions() {
        checkThread();
        return List.copyOf(sessions);
    }

    /** On rejection ownership remains with the caller; successful mounting transfers it. */
    public Optional<Mount> mount(Session session, RasterTarget target) {
        checkThread();
        if (!valid(session) || mountCount() >= MAX_MOUNTS || !target.current()
                || !session.owner.equals(target.scope().identity()) || !allowed(session.owner, READ)) {
            return Optional.empty();
        }
        for (Session existing : sessions) {
            for (Mount mount : existing.mounts) {
                if (mount.scope.display().equals(target.scope().display())) return Optional.empty();
            }
        }
        Mount mount = new Mount(session, target);
        session.mounts.add(mount);
        deliver(mount);
        return Optional.of(mount);
    }

    /** A separate explicit input gesture must call this; mounting does not grant input. */
    public Optional<InputLease> acquireInput(Mount mount) {
        checkThread();
        if (!valid(mount) || !allowed(mount.scope.identity(), INPUT)) return Optional.empty();
        if (mount.session.input != null) {
            return mount.session.input.mount == mount ? Optional.of(mount.session.input) : Optional.empty();
        }
        InputLease lease = new InputLease(mount);
        mount.session.input = lease;
        return Optional.of(lease);
    }

    public boolean touch(InputLease lease, double u, double v) {
        checkThread();
        if (lease == null || !valid(lease.mount) || lease.mount.session.input != lease) return false;
        Session session = lease.mount.session;
        if (!allowed(session.owner, INPUT)) {
            close(session); // also cancels any queued input; never silently reacquire a lease
            return false;
        }
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1
                || session.nextId > TouchDisplayTerminalWorkerProtocol.MAX_ID) return false;
        if (!session.transport.ready()) return false;
        if (!session.transport.touch(session.nextId, u == 0 ? 0.0 : u, v == 0 ? 0.0 : v)) return false;
        session.nextId++;
        return true;
    }

    /** Call from the client tick/render coordinator; all transport methods must be nonblocking. */
    public void pump() {
        checkThread();
        for (Session session : List.copyOf(sessions)) {
            try {
                if (!valid(session) || session.input != null && !allowed(session.owner, INPUT)) {
                    close(session);
                    continue;
                }
                for (Mount mount : List.copyOf(session.mounts)) {
                    if (!valid(mount)) unmount(mount);
                }
                if (session.closed) continue;
                session.transport.pump();
                if (!session.transport.active()) {
                    close(session);
                    continue;
                }
                session.transport.latestFrame().ifPresent(frame -> {
                    if (!frame.full()) throw new IllegalArgumentException("Terminal broker requires composed full frames");
                    if (session.latest == null || frame.sequence() > session.latest.sequence()) session.latest = frame;
                });
                for (Mount mount : List.copyOf(session.mounts)) deliver(mount);
            } catch (RuntimeException failure) {
                if (session.failure == null) session.failure = "terminal_pump_failed";
                try { close(session); }
                catch (RuntimeException cleanup) {
                    // close() already attempted every resource and recorded a
                    // stable code. One broken session cannot halt other viewers.
                    session.failure = "terminal_cleanup_failed";
                }
            }
        }
    }

    public void unmount(Mount mount) {
        checkThread();
        if (mount == null || mount.closed || !sessions.contains(mount.session)) return;
        if (mount.session.input != null && mount.session.input.mount == mount) {
            close(mount.session); // input cannot outlive the surface which authorised it
        } else {
            release(mount);
        }
    }

    public void close(Session session) {
        checkThread();
        if (session == null || session.closed || !sessions.remove(session)) return;
        session.closed = true;
        session.input = null;
        session.latest = null;
        RuntimeException failure = null;
        for (Mount mount : List.copyOf(session.mounts)) {
            try { release(mount); }
            catch (RuntimeException cleanup) { failure = combine(failure, cleanup); }
        }
        try { session.transport.close(); }
        catch (RuntimeException cleanup) { failure = combine(failure, cleanup); }
        if (failure != null) {
            session.failure = "terminal_cleanup_failed";
            throw failure;
        }
    }

    @Override
    public void close() {
        checkThread();
        closed = true;
        RuntimeException failure = null;
        for (Session session : List.copyOf(sessions)) {
            try { close(session); }
            catch (RuntimeException cleanup) { failure = combine(failure, cleanup); }
        }
        if (failure != null) throw failure;
    }

    private boolean valid(Session session) {
        return session != null && !closed && !session.closed && sessions.contains(session)
                && allowed(session.owner, SESSION) && session.transport.active();
    }

    private boolean valid(Mount mount) {
        return mount != null && !mount.closed && valid(mount.session) && mount.session.mounts.contains(mount)
                && mount.scope.equals(mount.target.scope()) && mount.target.current() && allowed(mount.scope.identity(), READ);
    }

    private boolean allowed(ClientProgramIdentity identity, ResourceLocation capability) {
        return identity.requestedCapabilities().contains(capability) && permissions.allowed(identity, capability);
    }

    private void deliver(Mount mount) {
        TouchDisplayRasterFrame frame = mount.session.latest;
        if (frame != null && frame.sequence() > mount.delivered && valid(mount)) {
            if (mount.target.offer(frame).accepted()) mount.delivered = frame.sequence();
        }
    }

    private void release(Mount mount) {
        if (mount.closed) return;
        mount.closed = true;
        mount.session.mounts.remove(mount);
        mount.target.close();
    }

    private int mountCount() {
        return sessions.stream().mapToInt(session -> session.mounts.size()).sum();
    }

    private static RuntimeException combine(@Nullable RuntimeException previous, RuntimeException next) {
        if (previous == null) return next;
        if (previous != next) previous.addSuppressed(next);
        return previous;
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Terminal broker requires its client thread");
    }
}
