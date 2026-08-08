package ca.teamdman.sfm;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.registry.SFMMenuScreens;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituationRegistrations;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.registry.SFMTextEditorActions;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.action.SFMCommandPaletteActions;
import ca.teamdman.sfm.client.action.SFMDeveloperActions;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.client.screen.text_editor.SFMDocumentActionTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceScreenTypes;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.event_bus.SFMAutomaticEventSubscriber;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void registerComputerCraftTurtleUpgrades() {

        if (ca.teamdman.sfm.common.compat.SFMModCompat.isComputerCraftLoaded()) {
            ca.teamdman.sfm.common.compat.computercraft.SFMComputerCraftTurtleUpgrades.register(SFMEventBus.MOD_BUS);
        }
    }

    public SFM() {

        var bus = FMLJavaModLoadingContext
                .get()
                .getModEventBus();
        SFMEventBus.MOD_BUS = bus;

        SFMBlocks.register(bus);

        SFMItems.register(bus);

        registerComputerCraftTurtleUpgrades();

        SFMResourceTypes.register(bus);

        SFMProgramLinters.register(bus);

        SFMBlockEntities.register(bus);

        SFMGlobalBlockCapabilityProviders.register(bus);

        SFMTextEditors.register(bus);

        SFMTextEditorActions.register(bus);

        SFMClientActions.register(bus);

        SFMKeyboardUsageSituations.register(bus);

        SFMClientScreenTypes.register(bus);

        SFMWorkspaceScreenTypes.register(bus);

        SFMDocumentActionTarget.Actions.register(bus);

        SFMCommandPaletteActions.register(bus);

        SFMKeyboardUsageSituationRegistrations.register(bus);

        SFMDeveloperActions.register(bus);

        SFMMenus.register(bus);

        SFMRecipeTypes.register(bus);

        SFMRecipeSerializers.register(bus);

        SFMConfig.register(ModLoadingContext.get());

        bus.addListener((FMLClientSetupEvent e) -> {
            SFMMenuScreens.register();
            SFMCommandHistoryService.initializeDefault();
            SFMClientActions.commandTree();
        });

        bus.addListener((FMLCommonSetupEvent e) -> SFMPackets.register());

        SFMAutomaticEventSubscriber.attachEventBusSubscribers();
    }

}
