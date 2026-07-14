package ca.teamdman.sfm;

import ca.teamdman.sfm.client.registry.SFMMenuScreens;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.compat.computercraft.ComputerCraftIntegration;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.event_bus.SFMAutomaticEventSubscriber;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// Welcome to SFM's source code!
/// I hope you enjoy your visit :D
@Mod(SFM.MOD_ID)
public class SFM {
    public static final String MOD_ID = "sfm";

    public static final Logger LOGGER = LogManager.getLogger(SFM.MOD_ID);

    public static final String ISSUE_TRACKER_URL = "https://github.com/TeamDman/SuperFactoryManager/issues";

    @SFMLocalizationDatagen
    public static final LocalizationEntry MOD_NAME = new LocalizationEntry(
            "mod.name",
            "Super Factory Manager"
    );

    public SFM(IEventBus bus) {

        SFMEventBus.MOD_BUS = bus;

        SFMBlocks.register(bus);

        SFMItems.register(bus);

        SFMCreativeTabs.register(bus);

        SFMResourceTypes.register(bus);

        SFMProgramLinters.register(bus);

        SFMBlockEntities.register(bus);

        SFMGlobalBlockCapabilityProviders.register(bus);

        SFMTextEditors.register(bus);

        SFMTextEditorActions.register(bus);

        SFMMenus.register(bus);

        SFMRecipeTypes.register(bus);

        SFMRecipeSerializers.register(bus);

        SFMConfig.register(ModLoadingContext.get());

        bus.addListener((FMLClientSetupEvent e) -> SFMMenuScreens.register());

        bus.addListener((FMLCommonSetupEvent e) -> {
            if (SFMModCompat.isComputerCraftLoaded()) {
                e.enqueueWork(ComputerCraftIntegration::register);
            }
        });
        SFMAutomaticEventSubscriber.attachEventBusSubscribers();
    }

}
