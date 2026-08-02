package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayScreenType;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMWorkspaceScreenTypes {
    private static final SFMDeferredRegister<SFMClientScreenType> REGISTERER =
            SFMClientScreenTypes.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientScreenType, SFMTestScreenType> TEST_SCREEN = REGISTERER.register(
            "test_screen",
            SFMTestScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMSizeDisplayScreenType> SIZE_DISPLAY = REGISTERER.register(
            "size_display",
            SFMSizeDisplayScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalScreenType> TERMINAL = REGISTERER.register(
            "terminal",
            SFMTerminalScreenType::new
    );

    private SFMWorkspaceScreenTypes() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
