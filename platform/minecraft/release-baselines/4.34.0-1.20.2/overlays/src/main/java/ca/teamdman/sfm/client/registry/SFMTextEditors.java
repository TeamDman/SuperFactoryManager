package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV1Registration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV2Registration;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;

public class SFMTextEditors {
    public static final ResourceKey<Registry<ISFMTextEditorRegistration>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("text_editor");

    private static final SFMDeferredRegister<ISFMTextEditorRegistration> REGISTERER =
            new SFMDeferredRegisterBuilder<ISFMTextEditorRegistration>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMTextEditScreenV1Registration> V1 = REGISTERER.register(
            "v1",
            SFMTextEditScreenV1Registration::new
    );

    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMTextEditScreenV2Registration> V2 = REGISTERER.register(
            "v2",
            SFMTextEditScreenV2Registration::new
    );

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }

    @MCVersionDependentBehaviour
    public static SFMRegistryWrapper<ISFMTextEditorRegistration> registry() {
        return REGISTERER.registry();
    }
}
