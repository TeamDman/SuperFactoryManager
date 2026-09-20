package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.action.SFMClientProgramReadAction;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.*;

/** Minecraft lifetime adapter; hidden displays do not prevent revocation or world cleanup. */
public final class ClientProgramReadRuntime {
    private static ClientProgramReadService<BlockState> service;
    private static ClientLevel activeWorld;
    private static long renderEpoch;
    private ClientProgramReadRuntime() {}

    public static SFMValue block(ClientProgramIdentity identity, BlockPos position, boolean loadedScope) {
        return service().block(identity, position, loadedScope, renderEpoch);
    }
    public static SFMValue latest(ClientProgramIdentity identity, ResourceLocation channel) {
        return service().latest(identity, channel);
    }
    public static Optional<ClientProgramReadService.Observation> observation(ClientProgramIdentity identity) {
        requireClientThread();
        return service == null ? Optional.empty() : service.observation(identity);
    }
    private static ClientProgramReadService<BlockState> service() {
        requireClientThread();
        observeWorld();
        if (service == null) service = new ClientProgramReadService<>(ClientProgramConsentRuntime.gate(),
                ClientManagerFrameRuntime::policyBlockers, new Environment());
        return service;
    }
    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) renderEpoch++;
    }
    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeWorld();
        if (service != null) service.maintain();
    }
    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == activeWorld) { if (service != null) service.close(); activeWorld = null; }
    }
    private static void observeWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != activeWorld) { if (service != null) service.close(); activeWorld = current; }
    }
    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Client reads require the client thread");
    }

    private static final class Environment implements ClientProgramReadService.Environment<BlockState> {
        @Override public Optional<ClientProgramReadService.Context<BlockState>> open(ClientProgramIdentity identity) {
            Minecraft minecraft = Minecraft.getInstance();
            if (ClientManagerFrameRuntime.liveIdentityFor(identity).isEmpty() || minecraft.level == null || minecraft.player == null) {
                return Optional.empty();
            }
            ClientManagerBlockEntity manager = (ClientManagerBlockEntity) minecraft.level.getBlockEntity(identity.managerPosition());
            if (manager == null) return Optional.empty();
            var built = new ProgramBuilder(manager.storedSource()).forExecutionSide(ProgramExecutionSide.CLIENT).build();
            if (!built.isBuildSuccessful() || built.program() == null) return Optional.empty();
            var manifest = ClientProgramActionManifest.compile(built.program(), SFMClientActions::programmaticBinding);
            Set<BlockPos> bound = new HashSet<>();
            Set<BlockPos> loaded = new HashSet<>();
            Set<ResourceLocation> channels = new HashSet<>();
            for (var scope : manifest.scopes().getOrDefault(SFMClientProgramReadAction.BLOCK, Set.of())) {
                if (scope.permission().equals(ClientProgramBlockReadSurface.READ_BOUND)) bound.add(SFMClientProgramReadAction.position(scope.subject()));
                else if (scope.permission().equals(ClientProgramBlockReadSurface.READ_LOADED)) loaded.add(SFMClientProgramReadAction.position(scope.subject()));
            }
            for (var scope : manifest.scopes().getOrDefault(SFMClientProgramReadAction.INBOX, Set.of())) {
                if (scope.permission().equals(ClientProgramInboxReadSurface.READ)) {
                    channels.add(new ResourceLocation(((SFMValue.StringValue) scope.subject()).value()));
                }
            }
            return Optional.of(new ClientProgramReadService.Context<>(new MinecraftClientBlockStateSource(minecraft.level),
                    minecraft.player.getUUID(), bound, loaded, channels, ClientProgramInboxReadSurface.minecraftInbox(identity)));
        }
        @Override public boolean isCurrent(ClientProgramIdentity identity, ClientProgramReadService.Context<BlockState> context) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level == context.source().worldIdentity() && minecraft.player != null
                    && minecraft.player.getUUID().equals(context.recipient())
                    && ClientManagerFrameRuntime.liveIdentityFor(identity).isPresent();
        }
    }
}
