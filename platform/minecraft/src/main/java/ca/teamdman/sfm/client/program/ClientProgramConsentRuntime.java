package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.SFM;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.Map;
import java.util.LinkedHashMap;

/** Client-owned persistent consent, loaded lazily without opening any UI. */
public final class ClientProgramConsentRuntime {
    private ClientProgramConsentRuntime() {}
    private static final class Holder {
        private static final ClientProgramConsentService SERVICE = new ClientProgramConsentService(
                FMLPaths.CONFIGDIR.get().resolve("sfm").resolve("client-program-consents.bin"),
                System::currentTimeMillis, ClientProgramSignerTrustRuntime.service());
    }

    public static ClientProgramConsentService service() { return Holder.SERVICE; }
    public static ClientProgramConsentGate gate() { return service().gate(); }

    public static ClientProgramConsentService.Result observe(
            ClientProgramIdentity identity, String source, String canonicalBindings
    ) {
        String sfmVersion = ModList.get().getModContainerById(SFM.MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse("unknown");
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("minecraft", SharedConstants.getCurrentVersion().getName());
        versions.put("sfm", sfmVersion);
        for (String loader : new String[]{"forge", "neoforge"}) {
            ModList.get().getModContainerById(loader).ifPresent(mod ->
                    versions.put(loader, mod.getModInfo().getVersion().toString()));
        }
        String build = SFM.class.getPackage().getImplementationVersion();
        versions.put("sfm_build", build == null ? "development-unversioned" : build);
        return service().observe(identity, source, canonicalBindings, versions);
    }
}
