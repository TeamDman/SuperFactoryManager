package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterFrame;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterInbox;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterRuntime;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Exact live block/identity adapter; hidden faces suspend rendering, not authority checks. */
public final class TouchDisplayTerminalRasterTarget implements TouchDisplayTerminalBroker.RasterTarget {
    private static final Map<TouchDisplayRasterInbox.Display, TouchDisplayTerminalRasterTarget> OWNED = new HashMap<>();
    private final TouchDisplayBlockEntity display;
    private final TouchDisplayRasterRuntime.Stream stream;
    private final TouchDisplayTerminalBroker.Scope scope;
    private boolean closed;

    private TouchDisplayTerminalRasterTarget(TouchDisplayBlockEntity display, ClientProgramIdentity identity,
                                            TouchDisplayRasterRuntime.Stream stream) {
        this.display = display;
        this.stream = stream;
        var pos = display.getBlockPos();
        scope = new TouchDisplayTerminalBroker.Scope(identity,
                new TouchDisplayRasterInbox.Display(identity.world().worldId(), identity.dimension().toString(),
                        pos.getX(), pos.getY(), pos.getZ()), stream.generation());
    }

    public static Optional<TouchDisplayTerminalRasterTarget> open(TouchDisplayBlockEntity display, ClientProgramIdentity identity) {
        if (!Minecraft.getInstance().isSameThread()) return Optional.empty();
        for (var previous : java.util.List.copyOf(OWNED.values())) if (!previous.current()) previous.close();
        var pos = display.getBlockPos();
        var address = new TouchDisplayRasterInbox.Display(identity.world().worldId(), identity.dimension().toString(),
                pos.getX(), pos.getY(), pos.getZ());
        if (OWNED.containsKey(address) || OWNED.size() >= TouchDisplayTerminalBroker.MAX_MOUNTS) return Optional.empty();
        return TouchDisplayRasterRuntime.open(display, identity).map(stream -> {
            var target = new TouchDisplayTerminalRasterTarget(display, identity, stream);
            OWNED.put(address, target);
            return target;
        });
    }

    public static TouchDisplayTerminalBroker.Permissions livePermissions() {
        return (identity, capability) -> Minecraft.getInstance().isSameThread()
                && identity.requestedCapabilities().contains(capability)
                && ClientManagerFrameRuntime.liveIdentityFor(identity).isPresent()
                && ClientManagerFrameRuntime.consent().execution(identity, ClientManagerFrameRuntime::policyBlockers).allowed()
                && ClientManagerFrameRuntime.consent().evaluate(identity, capability, ClientManagerFrameRuntime::policyBlockers).allowed();
    }

    @Override public TouchDisplayTerminalBroker.Scope scope() { return scope; }

    public Optional<ca.teamdman.sfm.client.raster.TouchDisplayRasterTextureCache.TextureInfo> textureInfo() {
        return current() ? TouchDisplayRasterRuntime.textureInfo(stream) : Optional.empty();
    }

    @Override public boolean current() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && !closed && stream.isCurrent() && minecraft.level != null
                && display.getLevel() == minecraft.level && !display.isRemoved()
                && minecraft.level.hasChunkAt(display.getBlockPos())
                && minecraft.level.getBlockEntity(display.getBlockPos()) == display
                && ClientManagerFrameRuntime.presentationIdentity(display).filter(scope.identity()::equals).isPresent();
    }

    @Override public TouchDisplayRasterInbox.OfferResult offer(TouchDisplayRasterFrame frame) {
        return current() ? stream.offer(frame) : TouchDisplayRasterInbox.OfferResult.REJECTED_LEASE;
    }

    @Override public void close() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Terminal raster target requires client thread");
        if (closed) return;
        closed = true;
        OWNED.remove(scope.display(), this);
        stream.close();
    }
}
