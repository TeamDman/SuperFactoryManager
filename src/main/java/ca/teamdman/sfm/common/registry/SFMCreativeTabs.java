package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@MCVersionDependentBehaviour
@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFMCreativeTabs {
    @SuppressWarnings("NotNullFieldNotInitialized")
    public static CreativeModeTab TAB;

    @SubscribeEvent
    public static void onRegister(CreativeModeTabEvent.Register event) {
        TAB = event.registerCreativeModeTab(
                SFMResourceLocation.fromSFMPath("main"),
                builder ->
                        // Set name of tab to display
                        builder.title(LocalizationKeys.CREATIVE_TAB.getComponent())
                                // Set icon of creative tab
                                .icon(() -> new ItemStack(SFMBlocks.MANAGER_BLOCK.get()))
                                // Add default items to tab
                                .displayItems((params, output) -> output.acceptAll(SFMItems.REGISTERER.getOurEntries()
                                                                                           .stream()
                                                                                           .map(SFMRegistryObject::get)
                                                                                           .map(ItemStack::new)
                                                                                           .toList()))
        );
    }
}
