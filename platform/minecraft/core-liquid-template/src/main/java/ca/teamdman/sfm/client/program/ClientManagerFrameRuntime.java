package ca.teamdman.sfm.client.program;

{% if features.client_program_actions %}
import ca.teamdman.sfm.client.action.SFMClientActionAuthorizationService;
import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% endif %}
{% if features.multiplayer_packets %}
import ca.teamdman.sfm.client.net.SFMMultiplayerClientRuntime;
{% endif %}
{% if features.client_program_actions %}
import ca.teamdman.sfm.common.value.SFMValue;
{% endif %}
{% if features.touch_display %}
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
{% endif %}
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ClientManagerLoadedRegistry;
{% if features.touch_display %}
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
{% endif %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.Block;
{% if features.client_frame_render %}
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.RenderImageStatement;
{% endif %}
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
{% if features.touch_display %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% endif %}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
{% if features.touch_display %}
import net.minecraft.world.phys.Vec3;
{% endif %}
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

{% if features.touch_display %}
import java.util.ArrayList;
{% endif %}
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
{% if features.touch_display %}
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
{% endif %}

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

{% if features.client_program_actions %}
    private static final SFMClientProgramActionDispatcher ACTIONS = new SFMClientProgramActionDispatcher(
            SFMClientActions::programmaticBinding, SFMClientActionAuthorizationService.shared());
{% endif %}
    private static final WeakHashMap<ClientManagerBlockEntity, Compiled> COMPILED = new WeakHashMap<>();
    private static final WeakHashMap<ClientManagerBlockEntity, Optional<ClientProgramWorldIdentity>> COMPILED_WORLDS = new WeakHashMap<>();
    private static final WeakHashMap<ClientManagerBlockEntity, Integer> COMPILE_COUNTS = new WeakHashMap<>();
{% if features.touch_display %}
    private static final WeakHashMap<TouchDisplayBlockEntity, FrameState> FRAMES = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, Map<ExecutionKey, FrameState>> EXECUTIONS = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, Long> DISPLAY_EPOCHS = new WeakHashMap<>();
    private static final WeakHashMap<TouchDisplayBlockEntity, String> DIAGNOSTICS = new WeakHashMap<>();
    private static final ClientFrameWorkBudget WORK_BUDGET = new ClientFrameWorkBudget(MAX_DISPLAY_EVALUATIONS_PER_FRAME);
    private static final ClientFrameWorkBudget PROGRAM_WORK_BUDGET = new ClientFrameWorkBudget(MAX_DISPLAY_EVALUATIONS_PER_FRAME);
{% endif %}
    private static @Nullable Level activeWorld;
    private static long renderEpoch;
    private static boolean consentNoticeShown;

    private ClientManagerFrameRuntime() {}

    public static ClientProgramConsentGate consent() {
        return ClientProgramConsentRuntime.gate();
    }

    public static Optional<ClientProgramIdentity> identityFor(ClientManagerBlockEntity manager) {
        if (!runtimeAvailable()) return Optional.empty();
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

{% if features.touch_display %}
    public static Optional<String> diagnosticFor(TouchDisplayBlockEntity display) {
        return Optional.ofNullable(DIAGNOSTICS.get(display));
    }

{% endif %}
    public static int compileCount(ClientManagerBlockEntity manager) {
        return COMPILE_COUNTS.getOrDefault(manager, 0);
    }

{% if features.touch_display %}
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
        if (!runtimeAvailable()) {
            DIAGNOSTICS.put(display, "Client Manager requires a private world or negotiated remote session");
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
{% if features.client_program_actions %}
                ClientFrameEvaluator.Evaluation evaluated = ClientFrameEvaluator.evaluate(writer.trigger(), state.frameIndex,
                        (action, input) -> ACTIONS.invoke(action, input, writer.identity(), consent(),
                                ClientManagerFrameRuntime::policyBlockers,
                                (identity, scope) -> writer.manifest().permits(action, scope),
                                ClientManagerFrameRuntime::runtimeAvailable),
                        writer.renderAllowed() && writers.size() == 1);
                state.values = evaluated.values();
{% else %}
                Optional<ResourceLocation> evaluated = ClientFrameEvaluator.evaluate(writer.trigger(), state.frameIndex);
{% endif %}
                state.frameIndex++;
                state.evaluations++;
{% if features.client_program_actions %}
                if (evaluated.image().isPresent() && !Objects.equals(evaluated.image().get(), state.texture)) {
                    state.texture = evaluated.image().get();
                    state.changedFrames++;
                }
{% else %}
                if (writer.renderAllowed() && writers.size() == 1
                    && evaluated.isPresent() && !Objects.equals(evaluated.get(), state.texture)) {
                    state.texture = evaluated.get();
                    state.changedFrames++;
                }
{% endif %}
            } catch (RuntimeException failure) {
{% if features.client_program_actions %}
                state.values = Map.of();
{% endif %}
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
        if (world == null || display.getLevel() != world || display.isRemoved() || !runtimeAvailable()) return List.of();
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
{% if features.client_program_actions %}
                programs.add(new Writer(frame, compiled.identity(), compiled.manifest(), triggerIndex,
{% else %}
                programs.add(new Writer(frame, compiled.identity(), triggerIndex,
{% endif %}
                        rendering != null && rendering.allowed()));
            }
        }

        return programs;
    }

{% endif %}
    private static boolean requestsRender(Block block) {
{% if features.client_frame_render %}
        return block.statements().stream().anyMatch(statement -> statement instanceof RenderImageStatement
                || statement instanceof IfStatement branch
                   && (requestsRender(branch.trueBlock()) || requestsRender(branch.falseBlock())));
{% else %}
        return false;
{% endif %}
    }

{% if features.touch_display %}
    /** The sole live approved presentation writer; independent of transient render eligibility. */
    public static Optional<ClientProgramIdentity> presentationIdentity(TouchDisplayBlockEntity display) {
        if (!Minecraft.getInstance().isSameThread()) return Optional.empty();
        List<Writer> writers = candidates(display).stream().filter(Writer::renderAllowed).toList();
        return writers.size() == 1 ? liveIdentityFor(writers.get(0).identity()) : Optional.empty();
    }

{% if features.client_program_actions %}
    /** Bounded ephemeral result observation for ambient integration tests and diagnostics. */
    public static Optional<SFMValue> valueFor(TouchDisplayBlockEntity display, ClientProgramIdentity identity, String name) {
        Map<ExecutionKey, FrameState> states = EXECUTIONS.get(display);
        if (states == null || liveIdentityFor(identity).isEmpty()) return Optional.empty();
        return states.entrySet().stream().filter(entry -> entry.getKey().identity().equals(identity))
                .map(entry -> entry.getValue().values.get(ClientProgramActionManifest.key(name)))
                .filter(Objects::nonNull).findFirst();
    }

{% endif %}
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

{% endif %}
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

{% if features.touch_display %}
    private static void observeWorld(Level world) {
        if (activeWorld == world) return;
        clearWorld();
        activeWorld = world;
    }

{% endif %}
    private static void clearWorld() {
        if (activeWorld != null) ClientManagerLoadedRegistry.clear(activeWorld);
        COMPILED.clear();
        COMPILED_WORLDS.clear();
        COMPILE_COUNTS.clear();
{% if features.touch_display %}
        FRAMES.clear();
        EXECUTIONS.clear();
        DISPLAY_EPOCHS.clear();
        DIAGNOSTICS.clear();
        WORK_BUDGET.clear();
        PROGRAM_WORK_BUDGET.clear();
{% endif %}
        consentNoticeShown = false;
        activeWorld = null;
    }

    private static Compiled compiled(ClientManagerBlockEntity manager) {
        Compiled previous = COMPILED.get(manager);
        var world = worldIdentity(manager.worldId());
        if (previous != null && previous.snapshotRevision() == manager.clientSnapshotRevision()
                && world.equals(COMPILED_WORLDS.get(manager))) return previous;
        Compiled refreshed = compile(manager);
        COMPILED.put(manager, refreshed);
        COMPILED_WORLDS.put(manager, world);
        COMPILE_COUNTS.merge(manager, 1, Integer::sum);
        return refreshed;
    }

    private static Compiled compile(ClientManagerBlockEntity manager) {
        if (!runtimeAvailable()) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, LabelPositionHolder.empty(),
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, LabelPositionHolder.empty(),
{% endif %}
                    "Client Manager requires a private world or negotiated remote session");
        }
        LabelPositionHolder labels = manager.labels();
        String source = manager.storedSource();
        if (source.isBlank() || worldIdentity(manager.worldId()).isEmpty() || manager.getLevel() == null) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels, "No synced program/world identity");
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, labels, "No synced program/world identity");
{% endif %}
        }
        if (!ClientFrameSourceBudget.permits(source)) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, labels,
{% endif %}
                    "Client program exceeds its source/token/nesting budget");
        }
        var result = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        if (!result.isBuildSuccessful() || result.program() == null) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, labels,
{% endif %}
                    "Client program did not compile: " + result.metadata().errors());
        }
        Program program = result.program();
        if (program.triggers().isEmpty() || program.triggers().size() > MAX_FRAME_TRIGGERS
            || program.triggers().stream().anyMatch(t -> !(t instanceof FrameTrigger))) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, labels,
{% endif %}
                    "Client Manager currently supports only EVERY FRAME triggers");
        }
        try {
            String bindings = ClientManagerTargetBindings.canonical(program, labels);
{% if features.client_program_actions %}
            ClientProgramActionManifest manifest = ClientProgramActionManifest.compile(program, SFMClientActions::programmaticBinding);
{% endif %}
            ClientProgramIdentity identity = ClientProgramIdentity.fromStoredSourceAndBindings(
                    source, bindings, ProgramExecutionSide.CLIENT,
                    worldIdentity(manager.worldId()).orElseThrow(),
                    manager.getLevel().dimension().location(), manager.getBlockPos(),
                    ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
{% if features.client_program_actions %}
                    manifest.capabilities()
{% else %}
                    program.triggers().stream().anyMatch(trigger -> requestsRender(((FrameTrigger) trigger).block()))
                            ? Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramConsentGate.RENDER)
                            : Set.of(ClientProgramConsentGate.EXECUTE)
{% endif %}
            );
            ClientProgramConsentRuntime.observe(identity, source, bindings);
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), program, identity, manifest, labels, null);
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), program, identity, labels, null);
{% endif %}
        } catch (IllegalArgumentException invalidBindings) {
{% if features.client_program_actions %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, null, labels,
{% else %}
            return new Compiled(manager.clientSnapshotRevision(), null, null, labels,
{% endif %}
                    invalidBindings.getMessage());
        }
    }

    public static List<String> policyBlockers(
            ClientProgramIdentity identity, ResourceLocation capability
    ) {
        if (worldIdentity(identity.world().worldId()).filter(identity.world()::equals).isEmpty()) {
            return List.of("current_private_or_negotiated_world_required");
        }
        return List.of();
    }

    private static boolean privateWorldAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return false;
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isSingleplayer() && !server.isPublished();
    }

    private static boolean runtimeAvailable() {
{% if features.multiplayer_packets %}
        return privateWorldAvailable() || SFMMultiplayerClientRuntime.available();
{% else %}
        return privateWorldAvailable();
{% endif %}
    }

    private static Optional<ClientProgramWorldIdentity> worldIdentity(@Nullable java.util.UUID worldId) {
        if (worldId == null) return Optional.empty();
{% if features.multiplayer_packets %}
        return privateWorldAvailable() ? Optional.of(ClientProgramWorldIdentity.integrated(worldId))
                : SFMMultiplayerClientRuntime.worldIdentity(worldId);
{% else %}
        return privateWorldAvailable() ? Optional.of(ClientProgramWorldIdentity.integrated(worldId)) : Optional.empty();
{% endif %}
    }

{% if features.touch_display %}
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

{% endif %}
    private record Compiled(
            long snapshotRevision,
            @Nullable Program program,
            @Nullable ClientProgramIdentity identity,
{% if features.client_program_actions %}
            @Nullable ClientProgramActionManifest manifest,
{% endif %}
            LabelPositionHolder labels,
            @Nullable String diagnostic
    ) {}

{% if features.touch_display %}
{% if features.client_program_actions %}
    private record Writer(FrameTrigger trigger, ClientProgramIdentity identity, ClientProgramActionManifest manifest,
{% else %}
    private record Writer(FrameTrigger trigger, ClientProgramIdentity identity,
{% endif %}
                          int triggerIndex, boolean renderAllowed) {}
    private record ExecutionKey(ClientProgramIdentity identity, int triggerIndex) {}

    private static final class FrameState {
        final ClientProgramIdentity identity;
        long lastEpoch = Long.MIN_VALUE;
        long frameIndex;
        long evaluations;
        long changedFrames;
        @Nullable ResourceLocation texture;
{% if features.client_program_actions %}
        Map<String, SFMValue> values = Map.of();
{% endif %}

        FrameState(ClientProgramIdentity identity) {
            this.identity = identity;
        }
    }
{% endif %}
}
