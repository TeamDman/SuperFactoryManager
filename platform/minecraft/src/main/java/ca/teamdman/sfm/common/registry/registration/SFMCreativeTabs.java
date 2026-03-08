package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CreativeModeTabEvent;

@MCVersionDependentBehaviour
public class SFMCreativeTabs {
    @SuppressWarnings("NotNullFieldNotInitialized")
    @SFMLocalizationDatagen
    public static final LocalizationEntry CREATIVE_TAB = new LocalizationEntry(
            "item_group.sfm",
            "Super Factory Manager"
    );

    public static CreativeModeTab MAIN;

    @SFMSubscribeEvent
    public static void onRegister(CreativeModeTabEvent.Register event) {
        MAIN = event.registerCreativeModeTab(
                SFMResourceLocation.fromSFMPath("main"),
                builder ->
                        // Set name of tab to display
                        builder.title(CREATIVE_TAB.getComponent())
                                // Set icon of creative tab
                                .icon(() -> new ItemStack(SFMBlocks.MANAGER.get()))
                                // Add default items to tab
                                .displayItems((params, output) -> output.acceptAll(SFMItems.REGISTERER.getOurEntries()
                                                                                           .stream()
                                                                                           .map(SFMRegistryObject::get)
                                                                                           .map(ItemStack::new)
                                                                                           .toList()))
        );
    }

}
