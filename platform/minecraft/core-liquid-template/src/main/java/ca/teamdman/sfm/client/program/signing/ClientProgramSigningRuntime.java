package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.ClientProgramSigningScreen;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.ClientManagerSigningResponses;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSignaturePacket;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSigningRequestPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit human authoring bridge. It never opens from proximity, program execution or a server packet. */
public final class ClientProgramSigningRuntime {
    private static final long CLOCK_ORIGIN = System.nanoTime();
    private static ClientPacketListener connection;
    private static UUID connectionId;
    private static ClientProgramSigningController active;
    private static FixtureKeys fixtureKeys;
    private record FixtureKeys(ClientProgramIdentity identity, Path directory) {}
    private ClientProgramSigningRuntime() {}

    /** Test-only isolation seam; ordinary actions and programs cannot install a key directory. */
    public static AutoCloseable installFixtureKeyDirectory(ClientProgramIdentity identity, Path directory) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Fixture keys require the client thread");
        if (SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.GAME_PUPPET) {
            throw new IllegalStateException("Fixture keys require an explicit game-puppet run");
        }
        observeConnection();
        if (fixtureKeys != null) throw new IllegalStateException("A fixture key directory is already installed");
        Path root = minecraft.gameDirectory.toPath().toAbsolutePath().normalize().resolve("sfm-puppet");
        Path selected = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        if (selected.equals(root) || !selected.startsWith(root)) {
            throw new IllegalArgumentException("Fixture keys must be below the game-puppet directory");
        }
        var installed = new FixtureKeys(Objects.requireNonNull(identity), selected);
        fixtureKeys = installed;
        return () -> {
            if (!minecraft.isSameThread()) throw new IllegalStateException("Fixture keys require the client thread");
            if (fixtureKeys == installed) fixtureKeys = null;
        };
    }

    public static String open(ClientProgramIdentity identity, String previousSource) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Signing review requires the client thread");
        observeConnection();
        if (!privateWorld() || connectionId == null || ClientManagerFrameRuntime.liveIdentityFor(identity).isEmpty()) {
            return "Signing requires the current loaded program in a private integrated world.";
        }
        var target = new ClientProgramSigningController.Target(connectionId, identity.world(), identity.dimension(), identity.managerPosition());
        if (current(target).isEmpty()) return "The manager has no valid signing revision to review.";
        closeActive();
        active = new ClientProgramSigningController(ClientProgramSigningRuntime::current,
                body -> ClientProgramSigningController.compileLocally(body, SFMClientActions::programmaticBinding),
                new ClientProgramSigningController.Transport() {
                    public void review(ServerboundClientManagerSigningRequestPacket packet) { SFMPackets.sendToServer(packet); }
                    public void submit(ServerboundClientManagerSignaturePacket packet) { SFMPackets.sendToServer(packet); }
                }, () -> (System.nanoTime() - CLOCK_ORIGIN) / 1_000_000L, UUID::randomUUID, ClientSigningUiWorker.executor(),
                minecraft::execute, ignored -> {});
        ClientManagerSigningResponses.setReceiver(response -> minecraft.execute(() -> {
            if (active != null) active.receive(response);
        }));
        SFMScreenChangeHelpers.setOrPushScreen(new ClientProgramSigningScreen(active, () -> current(target),
                previousSource, fixtureKeys != null && fixtureKeys.identity().equals(identity)
                ? fixtureKeys.directory() : FMLPaths.CONFIGDIR.get().resolve("sfm").resolve("signing-keys")));
        return "Review the server-acknowledged revision. Saving and signing are separate actions.";
    }

    private static Optional<ClientProgramSigningController.LiveRevision> current(ClientProgramSigningController.Target target) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || !privateWorld() || minecraft.getConnection() != connection
                || !target.connectionId().equals(connectionId) || minecraft.level == null
                || !minecraft.level.dimension().location().equals(target.dimension())
                || !minecraft.level.hasChunkAt(target.position())
                || !(minecraft.level.getBlockEntity(target.position()) instanceof ClientManagerBlockEntity manager)
                || manager.isRemoved() || manager.worldId() == null
                || !target.world().equals(ClientProgramWorldIdentity.integrated(manager.worldId()))) return Optional.empty();
        var snapshot = manager.signingSnapshot();
        return snapshot == null ? Optional.empty() : Optional.of(new ClientProgramSigningController.LiveRevision(
                target, snapshot.incarnation(), snapshot.revision(), snapshot.body()));
    }

    private static boolean privateWorld() {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        return minecraft.player != null && server != null && server.isSingleplayer() && !server.isPublished();
    }

    private static void observeConnection() {
        var now = Minecraft.getInstance().getConnection();
        if (now != connection) {
            closeActive();
            fixtureKeys = null;
            connection = now;
            connectionId = now == null ? null : UUID.randomUUID();
        }
    }

    private static void closeActive() {
        if (active != null) active.close();
        active = null;
        ClientManagerSigningResponses.setReceiver(ignored -> {});
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeConnection();
        if (active != null) active.tick();
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            closeActive();
            fixtureKeys = null;
        }
    }
}
