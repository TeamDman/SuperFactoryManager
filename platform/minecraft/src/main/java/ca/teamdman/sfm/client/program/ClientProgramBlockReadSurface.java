package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A bounded, consent-aware view of block states already replicated to this client.
 * A Client Manager owns one surface for its lifetime and calls {@link #beginFrame()}
 * before each frame. This never loads chunks or queries a server inventory.
 *
 * <p>The source samples a cheap revision on every read. Only a changed revision
 * causes the state to be projected into an SFM value again. For vanilla block
 * states, the immutable BlockState object is the content revision.</p>
 */
public final class ClientProgramBlockReadSurface<S> {
    public static final ResourceLocation READ_BOUND = new ResourceLocation("sfm", "world/block_state/read_bound");
    public static final ResourceLocation READ_LOADED = new ResourceLocation("sfm", "world/block_state/read_loaded");
    public static final int MAX_RADIUS = 64;
    public static final int MAX_BOUND_POSITIONS = 128;
    public static final int MAX_UNIQUE_READS_PER_FRAME = 128;
    public static final int MAX_CACHE_ENTRIES = 256;

    public enum Status {
        VALUE,
        UNKNOWN_UNLOADED,
        UNAVAILABLE_UNDECLARED,
        UNAVAILABLE_AWAITING_CONSENT,
        UNAVAILABLE_DENIED_BY_USER,
        UNAVAILABLE_BLOCKED_BY_POLICY,
        UNAVAILABLE_OUT_OF_SCOPE,
        UNAVAILABLE_WORLD_CHANGED,
        UNAVAILABLE_BUDGET,
        UNAVAILABLE_SOURCE
    }

    public record Result(Status status, Optional<SFMValue> value) {
        public Result {
            Objects.requireNonNull(status, "status");
            value = Objects.requireNonNull(value, "value");
            if ((status == Status.VALUE) != value.isPresent()) {
                throw new IllegalArgumentException("Only VALUE carries a block state");
            }
        }

        public static Result unavailable(Status status) {
            return new Result(status, Optional.empty());
        }

        public static Result present(SFMValue value) {
            return new Result(Status.VALUE, Optional.of(value));
        }
    }

    /** An empty sample means the chunk disappeared between the load check and read. */
    public interface Source<S> {
        /** A stable object for this loaded client world; compared by reference, not equals. */
        Object worldIdentity();

        ResourceLocation dimension();

        boolean isActive();

        boolean isLoaded(BlockPos position);

        Optional<Sample<S>> sample(BlockPos position);

        SFMValue project(S state);
    }

    public record Sample<S>(Object revision, S state) {
        public Sample {
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(state, "state");
        }
    }

    private record Cached(Object revision, SFMValue value) {}

    private final ClientProgramIdentity program;
    private final ClientProgramConsentGate consent;
    private final ClientProgramConsentGate.Policy policy;
    private final Source<S> source;
    private final Object worldIdentity;
    private final Set<BlockPos> boundPositions;
    private final int radius;
    private final Set<BlockPos> readThisFrame = new HashSet<>();
    private final Map<BlockPos, Cached> cached = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Cached> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public ClientProgramBlockReadSurface(
            ClientProgramIdentity program,
            ClientProgramConsentGate consent,
            ClientProgramConsentGate.Policy policy,
            Source<S> source,
            Set<BlockPos> boundPositions,
            int radius
    ) {
        this.program = Objects.requireNonNull(program, "program");
        if (program.hostSide() != ProgramExecutionSide.CLIENT) {
            throw new IllegalArgumentException("Only Client Managers may read client block states");
        }
        this.consent = Objects.requireNonNull(consent, "consent");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.source = Objects.requireNonNull(source, "source");
        this.worldIdentity = Objects.requireNonNull(source.worldIdentity(), "source world identity");
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Client block read radius must be within 0.." + MAX_RADIUS);
        }
        this.radius = radius;
        Objects.requireNonNull(boundPositions, "boundPositions");
        if (boundPositions.size() > MAX_BOUND_POSITIONS) {
            throw new IllegalArgumentException("Too many bound client block positions");
        }
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos position : boundPositions) {
            Objects.requireNonNull(position, "bound position");
            if (!inRadius(program.managerPosition(), position, radius)) {
                throw new IllegalArgumentException("Bound client block position exceeds the approved radius");
            }
            positions.add(position.immutable());
        }
        this.boundPositions = Set.copyOf(positions);
    }

    public void beginFrame() {
        readThisFrame.clear();
    }

    public void clear() {
        cached.clear();
        readThisFrame.clear();
    }

    public Result readBound(BlockPos position) {
        return read(position, READ_BOUND, true);
    }

    public Result readLoaded(BlockPos position) {
        return read(position, READ_LOADED, false);
    }

    public int cachedPositions() {
        return cached.size();
    }

    private Result read(BlockPos requested, ResourceLocation capability, boolean exactBound) {
        Objects.requireNonNull(requested, "position");
        BlockPos position = requested.immutable();
        if (!program.requestedCapabilities().contains(capability)) {
            return Result.unavailable(Status.UNAVAILABLE_UNDECLARED);
        }
        ClientProgramConsentGate.Evaluation execution = consent.execution(program, policy);
        if (!execution.allowed()) return Result.unavailable(deniedStatus(execution));
        ClientProgramConsentGate.Evaluation read = consent.evaluate(program, capability, policy);
        if (!read.allowed()) return Result.unavailable(deniedStatus(read));
        if (!inRadius(program.managerPosition(), position, radius)
            || (exactBound && !boundPositions.contains(position))) {
            return Result.unavailable(Status.UNAVAILABLE_OUT_OF_SCOPE);
        }
        if (source.worldIdentity() != worldIdentity || !source.isActive()
            || !source.dimension().equals(program.dimension())) {
            clear();
            return Result.unavailable(Status.UNAVAILABLE_WORLD_CHANGED);
        }
        if (!readThisFrame.contains(position)
            && readThisFrame.size() >= MAX_UNIQUE_READS_PER_FRAME) {
            return Result.unavailable(Status.UNAVAILABLE_BUDGET);
        }
        readThisFrame.add(position);
        if (!source.isLoaded(position)) {
            cached.remove(position);
            return Result.unavailable(Status.UNKNOWN_UNLOADED);
        }
        Optional<Sample<S>> observed = source.sample(position);
        if (observed.isEmpty()) {
            cached.remove(position);
            return Result.unavailable(Status.UNKNOWN_UNLOADED);
        }
        Sample<S> sample = observed.orElseThrow();
        Cached previous = cached.get(position);
        if (previous != null && previous.revision.equals(sample.revision())) {
            return Result.present(previous.value);
        }
        try {
            SFMValue value = Objects.requireNonNull(source.project(sample.state()), "projected block state");
            cached.put(position, new Cached(sample.revision(), value));
            return Result.present(value);
        } catch (IllegalArgumentException invalidProjection) {
            cached.remove(position);
            return Result.unavailable(Status.UNAVAILABLE_SOURCE);
        }
    }

    private static boolean inRadius(BlockPos origin, BlockPos target, int radius) {
        long dx = (long) target.getX() - origin.getX();
        long dy = (long) target.getY() - origin.getY();
        long dz = (long) target.getZ() - origin.getZ();
        if (Math.abs(dx) > radius || Math.abs(dy) > radius || Math.abs(dz) > radius) {
            return false;
        }
        long squared = dx * dx + dy * dy + dz * dz;
        return squared <= (long) radius * radius;
    }

    private static Status deniedStatus(ClientProgramConsentGate.Evaluation evaluation) {
        return switch (evaluation.effective()) {
            case ALLOWED -> throw new IllegalArgumentException("Allowed consent is not a denial");
            case AWAITING_CONSENT -> Status.UNAVAILABLE_AWAITING_CONSENT;
            case DENIED_BY_USER -> Status.UNAVAILABLE_DENIED_BY_USER;
            case BLOCKED_BY_POLICY -> Status.UNAVAILABLE_BLOCKED_BY_POLICY;
        };
    }
}
