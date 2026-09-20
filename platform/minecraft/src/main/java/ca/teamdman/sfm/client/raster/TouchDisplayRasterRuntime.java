package ca.teamdman.sfm.client.raster;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client lifecycle and presentation authority for transient live rasters. */
public final class TouchDisplayRasterRuntime {
    private static final TouchDisplayRasterInbox INBOX = new TouchDisplayRasterInbox();
    private static final TouchDisplayRasterTextureCache<MinecraftRasterTextureAccess.Texture> TEXTURES =
            new TouchDisplayRasterTextureCache<>(INBOX, new MinecraftRasterTextureAccess());
    private static final Map<BlockPos, Binding> BINDINGS = new HashMap<>(); // client thread only
    private static @Nullable Level world;
    private static long renderEpoch;

    private TouchDisplayRasterRuntime() {}

    /** An authorised producer can offer from a receiver thread; it cannot choose another display. */
    public static final class Stream implements AutoCloseable {
        private final TouchDisplayRasterInbox.WriterLease lease;
        private final ClientProgramIdentity identity;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Stream(TouchDisplayRasterInbox.WriterLease lease, ClientProgramIdentity identity) {
            this.lease = lease;
            this.identity = identity;
        }

        public long generation() { return lease.generation(); }
        public boolean isCurrent() { return !closed.get() && INBOX.isCurrent(lease); }
        public TouchDisplayRasterInbox.OfferResult offer(TouchDisplayRasterFrame frame) {
            if (closed.get()) return TouchDisplayRasterInbox.OfferResult.REJECTED_LEASE;
            return INBOX.offer(lease, frame);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            INBOX.release(lease);
            Minecraft.getInstance().execute(() -> removeBinding(this));
        }
    }

    private static final class Binding {
        private final TouchDisplayBlockEntity display;
        private final Stream stream;
        private long lastEpoch = Long.MIN_VALUE;

        private Binding(TouchDisplayBlockEntity display, Stream stream) {
            this.display = display;
            this.stream = stream;
        }
    }

    /** Bind only the exact single approved writer resolved by the client frame scheduler. */
    public static Optional<Stream> open(TouchDisplayBlockEntity display, ClientProgramIdentity identity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return Optional.empty();
        observeWorld(minecraft.level);
        if (!validDisplay(display) || !authorised(display, identity)) return Optional.empty();
        Binding previous = BINDINGS.get(display.getBlockPos());
        if (previous != null) {
            if (previous.display == display && previous.stream.identity.equals(identity) && previous.stream.isCurrent()) {
                return Optional.of(previous.stream);
            }
            closeBinding(previous);
        }
        var pos = display.getBlockPos();
        var address = new TouchDisplayRasterInbox.Display(identity.world().worldId(), identity.dimension().toString(),
                pos.getX(), pos.getY(), pos.getZ());
        return INBOX.acquire(address, identity.sourceSha256() + ":" + identity.bindingSha256()).map(lease -> {
            Stream stream = new Stream(lease, identity);
            BINDINGS.put(pos.immutable(), new Binding(display, stream));
            return stream;
        });
    }

    /** Called by the BER after the client scheduler has resolved its current writer. */
    public static Optional<ResourceLocation> textureFor(TouchDisplayBlockEntity display) {
        return textureForSelectedFrame(display, renderEpoch, ClientManagerFrameRuntime.renderEligible(display));
    }

    /** Ambient-test scheduler seam; this does not move the camera or prove visibility. */
    public static Optional<ResourceLocation> textureForSelectedFrame(TouchDisplayBlockEntity display, long epoch) {
        return textureForSelectedFrame(display, epoch, true);
    }

    /** Visibility suspends uploads, never authority checks or the current writer lease. */
    public static Optional<ResourceLocation> textureForSelectedFrame(TouchDisplayBlockEntity display, long epoch, boolean eligible) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return Optional.empty();
        observeWorld(minecraft.level);
        Binding binding = BINDINGS.get(display.getBlockPos());
        if (binding == null) return Optional.empty();
        if (binding.display != display || !validDisplay(display) || !authorised(display, binding.stream.identity)
                || !binding.stream.isCurrent()) {
            closeBinding(binding);
            return Optional.empty();
        }
        Optional<MinecraftRasterTextureAccess.Texture> texture;
        if (!eligible || binding.lastEpoch == epoch) {
            texture = TEXTURES.currentTexture(binding.stream.lease);
        } else {
            binding.lastEpoch = epoch;
            texture = TEXTURES.textureFor(binding.stream.lease);
        }
        return texture.map(MinecraftRasterTextureAccess.Texture::location);
    }

    public static Optional<TouchDisplayRasterTextureCache.TextureInfo> textureInfo(Stream stream) {
        if (!Minecraft.getInstance().isSameThread() || !stream.isCurrent()) return Optional.empty();
        return TEXTURES.info(stream.lease);
    }

    public record Observation(TouchDisplayRasterInbox.Snapshot cpu, TouchDisplayRasterTextureCache.Snapshot gpu) {}

    public static Observation observation() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Raster observation requires client thread");
        return new Observation(INBOX.snapshot(), TEXTURES.snapshot());
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) renderEpoch++;
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeWorld(Minecraft.getInstance().level);
        for (Binding binding : List.copyOf(BINDINGS.values())) {
            if (!validDisplay(binding.display) || !authorised(binding.display, binding.stream.identity)
                    || !binding.stream.isCurrent()) closeBinding(binding);
        }
        TEXTURES.maintain();
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() != world) return;
        var chunk = event.getChunk().getPos();
        Minecraft.getInstance().execute(() -> {
            if (event.getLevel() != world) return;
            for (Binding binding : List.copyOf(BINDINGS.values())) {
                BlockPos display = binding.display.getBlockPos();
                BlockPos manager = binding.stream.identity.managerPosition();
                if ((display.getX() >> 4) == chunk.x && (display.getZ() >> 4) == chunk.z
                    || (manager.getX() >> 4) == chunk.x && (manager.getZ() >> 4) == chunk.z) closeBinding(binding);
            }
        });
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() != world) return;
        Minecraft.getInstance().execute(() -> {
            if (event.getLevel() == world) observeWorld(null);
        });
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ignored ->
                Minecraft.getInstance().execute(TEXTURES::invalidateTextures));
    }

    private static boolean validDisplay(TouchDisplayBlockEntity display) {
        return world != null && display.getLevel() == world && !display.isRemoved()
                && world.hasChunkAt(display.getBlockPos()) && world.getBlockEntity(display.getBlockPos()) == display;
    }

    private static boolean authorised(TouchDisplayBlockEntity display, ClientProgramIdentity identity) {
        return ClientManagerFrameRuntime.presentationIdentity(display).filter(identity::equals).isPresent();
    }

    private static void observeWorld(@Nullable Level next) {
        if (world == next) return;
        for (Binding binding : List.copyOf(BINDINGS.values())) closeBinding(binding);
        INBOX.clear();
        TEXTURES.invalidateTextures();
        world = next;
    }

    private static void closeBinding(Binding binding) {
        binding.stream.closed.set(true);
        INBOX.release(binding.stream.lease);
        removeBinding(binding.stream);
    }

    private static void removeBinding(Stream stream) {
        BlockPos pos = new BlockPos(stream.lease.display().x(), stream.lease.display().y(), stream.lease.display().z());
        Binding binding = BINDINGS.get(pos);
        if (binding != null && binding.stream == stream) BINDINGS.remove(pos);
        TEXTURES.retire(stream.lease);
    }
}
