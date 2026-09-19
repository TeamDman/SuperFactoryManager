package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.action.SFMClientActionAuthorizationService;
import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ClientManagerLoadedRegistry;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.Block;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.RenderImageStatement;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/** Executes consented client visual programs only for BER-selected Touch Displays. */
public final class ClientManagerFrameRuntime {
    public static final int MAX_MANAGER_TO_DISPLAY_DISTANCE_SQUARED = 64 * 64;
    public static final int MAX_FRAME_TRIGGERS = 32;
    public static final int MAX_MANAGERS_PER_FRAME = 128;
    public static final int MAX_DISPLAY_EVALUATIONS_PER_FRAME = 128;

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONSENT_REQUIRED = new LocalizationEntry(
            "sfm.client_manager.consent_required",
            "Client Manager paused: approve its program before it can render"
    );

    private static final SFMClientProgramActionDispatcher ACTIONS = new SFMClientProgramActionDispatcher(
            SFMClientActions::programmaticBinding, SFMClientActionAuthorizationService.shared());
    private static final WeakHashMap<ClientManagerBlockEntity, Compiled> COMPILED = new WeakHashMap<>();
    private static final WeakHashMap<ClientManagerBlockEntity, Integer> COMPILE_COUNTS = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, FrameState> FRAMES = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, Map<ExecutionKey, FrameState>> EXECUTIONS = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, Long> DISPLAY_EPOCHS = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, String> DIAGNOSTICS = new WeakHashMap<>();
    private static final ClientFrameWorkBudget WORK_BUDGET = new ClientFrameWorkBudget(MAX_DISPLAY_EVALUATIONS_PER_FRAME);
    private static final ClientFrameWorkBudget PROGRAM_WORK_BUDGET = new ClientFrameWorkBudget(MAX_DISPLAY_EVALUATIONS_PER_FRAME);
    private static @Nullable Level activeWorld;
    private static long renderEpoch;
    private static boolean consentNoticeShown;

    private ClientManagerFrameRuntime() {}

    public static ClientProgramConsentGate consent() {
        return ClientProgramConsentRuntime.gate();
    }

    public static Optional<ClientProgramIdentity> identityFor(ClientManagerBlockEntity manager) {
        if (!privateWorldAvailable()) return Optional.empty();
        return Optional.ofNullable(compiled(manager).identity());
    }

    /** Resolves authority from the current loaded manager, never a stale saved identity. */
    public static Optional<ClientProgramIdentity> liveIdentityFor(ClientProgramIdentity expected) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return Optional.empty();
        Level world = minecraft.level;
        if (world == null || !world.dimension().location().equals(expected.dimension())
            || !world.hasChunkAt(expected.managerPosition())) return Optional.empty();
        if (!(world.getBlockEntity(expected.managerPosition()) instanceof ClientManagerBlockEntity manager)
            || manager.isRemoved()) return Optional.empty();
        return identityFor(manager).filter(expected::equals);
    }

    public static Optional<String> diagnosticFor(ClientManagerBlockEntity manager) {
        return Optional.ofNullable(compiled(manager).diagnostic());
    }

    public static Optional<String> diagnosticFor(TouchDisplayBlockEntity display) {
        return Optional.ofNullable(DIAGNOSTICS.get(display));
    }

    public static int compileCount(ClientManagerBlockEntity manager) {
        return COMPILE_COUNTS.getOrDefault(manager, 0);
    }

    /** Called only from the selected display's block-entity renderer. */
    public static Optional<ResourceLocation> textureFor(TouchDisplayBlockEntity display) {
        Minecraft minecraft = Minecraft.getInstance();
        return textureForSelectedFrame(display, renderEpoch, renderEligible(minecraft, display));
    }

    /**
     * Scheduler seam with eligibility supplied by the caller. Production calls
     * it from the BER; ambient tests can exercise execution without moving the
     * player's camera. Such tests do not prove actual visibility or pixels.
     */
    public static Optional<ResourceLocation> textureForSelectedFrame(
            TouchDisplayBlockEntity display, long epoch, boolean eligible
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Level world = minecraft.level;
        if (!minecraft.isSameThread() || world == null || display.getLevel() != world || display.isRemoved()) {
            return Optional.empty();
        }
        observeWorld(world);
        DIAGNOSTICS.remove(display);
        if (!privateWorldAvailable()) {
            DIAGNOSTICS.put(display, "Client Manager requires a private integrated world");
            FRAMES.remove(display);
            EXECUTIONS.remove(display);
            return Optional.empty();
        }
        if (!eligible) {
            // Visibility is a scheduling pause, not a loss of program authority
            // or a fresh animation. Retain values/frame counters and stream leases.
            return Optional.empty();
        }

        FrameState cached = FRAMES.get(display);
        // This is shared across all displays and uses the actual render event,
        // never an epoch supplied by the ambient scheduler test seam.
        if (!Objects.equals(DISPLAY_EPOCHS.get(display), epoch) && !WORK_BUDGET.tryAcquire(renderEpoch)) {
            DIAGNOSTICS.put(display, "Client Manager display work budget exhausted for this frame");
            return retainedTexture(display, cached);
        }
        DISPLAY_EPOCHS.put(display, epoch);

        List<Writer> programs = candidates(display);
        List<Writer> writers = programs.stream().filter(Writer::renderAllowed).toList();
        Map<ExecutionKey, FrameState> states = EXECUTIONS.computeIfAbsent(display, ignored -> new HashMap<>());
        Set<ExecutionKey> active = new HashSet<>();
        for (Writer writer : programs) {
            ExecutionKey key = new ExecutionKey(writer.identity(), writer.triggerIndex());
            active.add(key);
            FrameState state = states.computeIfAbsent(key, ignored -> new FrameState(writer.identity()));
            if (state.lastEpoch == epoch) continue;
            if (!PROGRAM_WORK_BUDGET.tryAcquire(renderEpoch)) {
                DIAGNOSTICS.put(display, "Client Manager program work budget exhausted for this frame");
                continue;
            }
            state.lastEpoch = epoch;
            try {
                ClientFrameEvaluator.Evaluation evaluated = ClientFrameEvaluator.evaluate(writer.trigger(), state.frameIndex,
                        (action, input) -> ACTIONS.invoke(action, input, writer.identity(), consent(),
                                ClientManagerFrameRuntime::policyBlockers,
                                (identity, scope) -> writer.manifest().permits(action, scope),
                                ClientManagerFrameRuntime::privateWorldAvailable),
                        writer.renderAllowed() && writers.size() == 1);
                state.values = evaluated.values();
                state.frameIndex++;
                state.evaluations++;
                if (evaluated.image().isPresent() && !Objects.equals(evaluated.image().get(), state.texture)) {
                    state.texture = evaluated.image().get();
                    state.changedFrames++;
                }
            } catch (RuntimeException failure) {
                state.values = Map.of();
                DIAGNOSTICS.put(display, "Client Manager frame evaluation failed");
            }
        }
        states.keySet().retainAll(active);
        if (writers.size() != 1) {
            if (writers.size() > 1) DIAGNOSTICS.put(display, "Multiple approved Client Manager triggers target this display");
            FRAMES.remove(display);
            return Optional.empty();
        }
        Writer writer = writers.get(0);
        FrameState state = states.get(new ExecutionKey(writer.identity(), writer.triggerIndex()));
        FRAMES.put(display, state);
        return retainedTexture(display, state);
    }

    private static List<Writer> candidates(TouchDisplayBlockEntity display) {
        Minecraft minecraft = Minecraft.getInstance();
        Level world = minecraft.level;
        if (world == null || display.getLevel() != world || display.isRemoved() || !privateWorldAvailable()) return List.of();
        List<Writer> programs = new ArrayList<>();
        Set<ClientManagerBlockEntity> managers = ClientManagerLoadedRegistry.snapshot(world);
        if (managers.size() > MAX_MANAGERS_PER_FRAME) {
            DIAGNOSTICS.put(display, "Loaded Client Manager count exceeds the per-frame budget");
            FRAMES.remove(display);
            return List.of();
        }
        for (ClientManagerBlockEntity manager : managers) {
            if (manager.isRemoved() || manager.getLevel() != world
                || !world.hasChunkAt(manager.getBlockPos())
                || world.getBlockEntity(manager.getBlockPos()) != manager
                || manager.getBlockPos().distSqr(display.getBlockPos())
                   > MAX_MANAGER_TO_DISPLAY_DISTANCE_SQUARED) continue;

            Compiled compiled = compiled(manager);
            if (compiled.program() == null || compiled.identity() == null) continue;
            int triggerIndex = -1;
            for (var trigger : compiled.program().triggers()) {
                triggerIndex++;
                FrameTrigger frame = (FrameTrigger) trigger;
                if (!ClientManagerTargetBindings.contains(frame, compiled.labels(), display.getBlockPos())) continue;

                ClientProgramConsentGate.Evaluation execution = consent().execution(compiled.identity(),
                        ClientManagerFrameRuntime::policyBlockers);
                boolean renderRequested = requestsRender(frame.block());
                ClientProgramConsentGate.Evaluation rendering = renderRequested
                        ? consent().evaluate(compiled.identity(), ClientProgramConsentGate.RENDER,
                                ClientManagerFrameRuntime::policyBlockers) : null;
                if (!execution.allowed() || rendering != null && !rendering.allowed()) {
                    if (execution.effective() == ClientProgramConsentGate.EffectiveState.AWAITING_CONSENT
                        || rendering != null && rendering.effective() == ClientProgramConsentGate.EffectiveState.AWAITING_CONSENT) {
                        showOneConsentNotice(minecraft);
                    }
                    if (!execution.allowed()) continue;
                }
                programs.add(new Writer(frame, compiled.identity(), compiled.manifest(), triggerIndex,
                        rendering != null && rendering.allowed()));
            }
        }

        return programs;
    }

    private static boolean requestsRender(Block block) {
        return block.statements().stream().anyMatch(statement -> statement instanceof RenderImageStatement
                || statement instanceof IfStatement branch
                   && (requestsRender(branch.trueBlock()) || requestsRender(branch.falseBlock())));
    }

    /** The sole live approved presentation writer; independent of transient render eligibility. */
    public static Optional<ClientProgramIdentity> presentationIdentity(TouchDisplayBlockEntity display) {
        if (!Minecraft.getInstance().isSameThread()) return Optional.empty();
        List<Writer> writers = candidates(display).stream().filter(Writer::renderAllowed).toList();
        return writers.size() == 1 ? liveIdentityFor(writers.get(0).identity()) : Optional.empty();
    }

    /** Bounded ephemeral result observation for ambient integration tests and diagnostics. */
    public static Optional<SFMValue> valueFor(TouchDisplayBlockEntity display, ClientProgramIdentity identity, String name) {
        Map<ExecutionKey, FrameState> states = EXECUTIONS.get(display);
        if (states == null || liveIdentityFor(identity).isEmpty()) return Optional.empty();
        return states.entrySet().stream().filter(entry -> entry.getKey().identity().equals(identity))
                .map(entry -> entry.getValue().values.get(ClientProgramActionManifest.key(name)))
                .filter(Objects::nonNull).findFirst();
    }

    public static FrameObservation observation(TouchDisplayBlockEntity display) {
        FrameState state = FRAMES.get(display);
        return state == null
                ? new FrameObservation(0, 0, 0, null, null)
                : new FrameObservation(state.frameIndex, state.evaluations, state.changedFrames,
                        state.identity.bindingSha256(), state.texture);
    }

    private static Optional<ResourceLocation> retainedTexture(TouchDisplayBlockEntity display, @Nullable FrameState state) {
        if (state == null) return Optional.empty();
        if (liveIdentityFor(state.identity).isEmpty()
            || presentationIdentity(display).filter(state.identity::equals).isEmpty()) {
            FRAMES.remove(display);
            return Optional.empty();
        }
        return Optional.ofNullable(state.texture);
    }

    public record FrameObservation(
            long nextFrameIndex,
            long evaluations,
            long changedFrames,
            @Nullable String bindingSha256,
            @Nullable ResourceLocation texture
    ) {}

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) renderEpoch++;
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level world) || !world.isClientSide()) return;
        Minecraft.getInstance().execute(() -> {
            // A loaded manager may never have had an eligible display. Release
            // that world's registry even when the render runtime never observed it.
            ClientManagerLoadedRegistry.clear(world);
            if (world == activeWorld) clearWorld();
        });
    }

    private static void observeWorld(Level world) {
        if (activeWorld == world) return;
        clearWorld();
        activeWorld = world;
    }

    private static void clearWorld() {
        if (activeWorld != null) ClientManagerLoadedRegistry.clear(activeWorld);
        COMPILED.clear();
        COMPILE_COUNTS.clear();
        FRAMES.clear();
        EXECUTIONS.clear();
        DISPLAY_EPOCHS.clear();
        DIAGNOSTICS.clear();
        WORK_BUDGET.clear();
        PROGRAM_WORK_BUDGET.clear();
        consentNoticeShown = false;
        activeWorld = null;
    }

    private static Compiled compiled(ClientManagerBlockEntity manager) {
        Compiled previous = COMPILED.get(manager);
        if (previous != null && previous.snapshotRevision() == manager.clientSnapshotRevision()) return previous;
        Compiled refreshed = compile(manager);
        COMPILED.put(manager, refreshed);
        COMPILE_COUNTS.merge(manager, 1, Integer::sum);
        return refreshed;
    }

    private static Compiled compile(ClientManagerBlockEntity manager) {
        if (!privateWorldAvailable()) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, LabelPositionHolder.empty(),
                    "Client Manager requires a private integrated world");
        }
        LabelPositionHolder labels = manager.labels();
        String source = manager.storedSource();
        if (source.isBlank() || manager.worldId() == null || manager.getLevel() == null) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels, "No synced program/world identity");
        }
        if (!ClientFrameSourceBudget.permits(source)) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
                    "Client program exceeds its source/token/nesting budget");
        }
        var result = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        if (!result.isBuildSuccessful() || result.program() == null) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
                    "Client program did not compile: " + result.metadata().errors());
        }
        Program program = result.program();
        if (program.triggers().isEmpty() || program.triggers().size() > MAX_FRAME_TRIGGERS
            || program.triggers().stream().anyMatch(t -> !(t instanceof FrameTrigger))) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
                    "Client Manager currently supports only EVERY FRAME triggers");
        }
        try {
            String bindings = ClientManagerTargetBindings.canonical(program, labels);
            ClientProgramActionManifest manifest = ClientProgramActionManifest.compile(program, SFMClientActions::programmaticBinding);
            ClientProgramIdentity identity = ClientProgramIdentity.fromStoredSourceAndBindings(
                    source, bindings, ProgramExecutionSide.CLIENT,
                    ClientProgramWorldIdentity.integrated(manager.worldId()),
                    manager.getLevel().dimension().location(), manager.getBlockPos(),
                    ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                    manifest.capabilities()
            );
            ClientProgramConsentRuntime.observe(identity, source, bindings);
            return new Compiled(manager.clientSnapshotRevision(), program, identity, manifest, labels, null);
        } catch (IllegalArgumentException invalidBindings) {
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
                    invalidBindings.getMessage());
        }
    }

    public static List<String> policyBlockers(
            ClientProgramIdentity identity, ResourceLocation capability
    ) {
        if (!privateWorldAvailable()
            || !identity.world().serverEndpoint().equals("integrated")) {
            return List.of("private_integrated_world_required");
        }
        return List.of();
    }

    private static boolean privateWorldAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return false;
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isSingleplayer() && !server.isPublished();
    }

    public static boolean renderEligible(TouchDisplayBlockEntity display) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && minecraft.level != null && display.getLevel() == minecraft.level
                && !display.isRemoved() && minecraft.level.hasChunkAt(display.getBlockPos())
                && minecraft.level.getBlockEntity(display.getBlockPos()) == display
                && renderEligible(minecraft, display);
    }

    private static boolean renderEligible(Minecraft minecraft, TouchDisplayBlockEntity display) {
        Direction face = display.getBlockState().getValue(TouchDisplayBlock.FACING);
        BlockPos pos = display.getBlockPos();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 outward = camera.subtract(Vec3.atCenterOf(pos));
        double front = outward.x * face.getStepX()
                       + outward.y * face.getStepY()
                       + outward.z * face.getStepZ();
        if (front <= 0.05) return false;
        double distance = Math.max(0.5, outward.length());
        // The BER call itself proves Minecraft selected the block entity for
        // rendering. This conservative pixel estimate skips tiny far surfaces;
        // it does not promise exact GPU depth occlusion behind walls.
        double projectedPixels = 0.875 * minecraft.getWindow().getHeight() / (1.4 * distance);
        return projectedPixels >= 4;
    }

    private static void showOneConsentNotice(Minecraft minecraft) {
        if (consentNoticeShown || minecraft.player == null) return;
        consentNoticeShown = true;
        minecraft.player.displayClientMessage(CONSENT_REQUIRED.getComponent(), true);
    }

    private record Compiled(
            long snapshotRevision,
            @Nullable Program program,
            @Nullable ClientProgramIdentity identity,
            @Nullable ClientProgramActionManifest manifest,
            LabelPositionHolder labels,
            @Nullable String diagnostic
    ) {}

    private record Writer(FrameTrigger trigger, ClientProgramIdentity identity, ClientProgramActionManifest manifest,
                          int triggerIndex, boolean renderAllowed) {}
    private record ExecutionKey(ClientProgramIdentity identity, int triggerIndex) {}

    private static final class FrameState {
        final ClientProgramIdentity identity;
        long lastEpoch = Long.MIN_VALUE;
        long frameIndex;
        long evaluations;
        long changedFrames;
        @Nullable ResourceLocation texture;
        Map<String, SFMValue> values = Map.of();

        FrameState(ClientProgramIdentity identity) {
            this.identity = identity;
        }
    }
}
