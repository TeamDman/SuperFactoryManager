package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.program.linting.FlowProgramLinter;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.ResourcesProgramLinter;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;

public class SFMProgramLinters {
    public static final ResourceKey<Registry<IProgramLinter>> REGISTRY_ID
            = SFMResourceLocation.createSFMRegistryKey("program_linters");

    private static final SFMDeferredRegister<IProgramLinter> REGISTERER =
            new SFMDeferredRegisterBuilder<IProgramLinter>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<IProgramLinter, FlowProgramLinter> FLOW
            = REGISTERER.register(
            "flow",
            FlowProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, ResourcesProgramLinter> RESOURCE
            = REGISTERER.register(
            "resources",
            ResourcesProgramLinter::new
    );

    static {
        if (SFMModCompat.isMekanismLoaded()) {
//            SFMMekanismCompat.registerProgramLinters(REGISTERER);
        }
    }

    public static SFMRegistryWrapper<IProgramLinter> registry() {

        return REGISTERER.registry();
    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
