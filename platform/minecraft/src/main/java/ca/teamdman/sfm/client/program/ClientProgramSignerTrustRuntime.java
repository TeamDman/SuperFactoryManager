package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningSnapshot;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.Optional;
import java.util.WeakHashMap;

/** Only current client-compiled programs can use local signer rules; disk metadata alone is not authority. */
public final class ClientProgramSignerTrustRuntime {
    private ClientProgramSignerTrustRuntime() {}
    private static final ObservationCache<ClientManagerBlockEntity> OBSERVED = new ObservationCache<>();
    private static final class Holder {
        private static final ClientProgramSignerTrustService SERVICE = new ClientProgramSignerTrustService(
                FMLPaths.CONFIGDIR.get().resolve("sfm").resolve("client-program-signers.bin"),
                System::currentTimeMillis, ClientProgramSignerTrustRuntime::currentProgram);
    }
    public static ClientProgramSignerTrustService service() { return Holder.SERVICE; }

    private static Optional<ClientProgramSignerTrustService.ObservedProgram> currentProgram(ClientProgramIdentity expected) {
        if (ClientManagerFrameRuntime.liveIdentityFor(expected).isEmpty()) return Optional.empty();
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(expected.managerPosition()) instanceof ClientManagerBlockEntity manager)) {
            return Optional.empty();
        }
        var snapshot = manager.signingSnapshot();
        if (snapshot == null) { OBSERVED.remove(manager); return Optional.empty(); }
        return Optional.of(OBSERVED.resolve(manager, expected, snapshot));
    }

    @SFMSubscribeEvent(SFMDist.CLIENT)
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) { OBSERVED.clear(); service().clearTransient(); }
    }

    /** Snapshot identity changes on source, binding or signature-history edits; unchanged frames re-encode nothing. */
    static final class ObservationCache<K> {
        private record Cached(ClientProgramIdentity identity, ClientManagerSigningSnapshot snapshot,
                              ClientProgramSignerTrustService.ObservedProgram observed) {}
        private final WeakHashMap<K, Cached> entries = new WeakHashMap<>();
        synchronized ClientProgramSignerTrustService.ObservedProgram resolve(
                K manager, ClientProgramIdentity identity, ClientManagerSigningSnapshot snapshot) {
            var existing = entries.get(manager);
            if (existing != null && existing.identity().equals(identity) && existing.snapshot() == snapshot) {
                return existing.observed();
            }
            var observed = new ClientProgramSignerTrustService.ObservedProgram(snapshot.body().source(), snapshot.history());
            if (entries.size() >= ClientProgramConsentStore.MAX_PROGRAMS && !entries.containsKey(manager)) {
                entries.remove(entries.keySet().iterator().next());
            }
            entries.put(manager, new Cached(identity, snapshot, observed));
            return observed;
        }
        synchronized void remove(K manager) { entries.remove(manager); }
        synchronized void clear() { entries.clear(); }
    }
}
