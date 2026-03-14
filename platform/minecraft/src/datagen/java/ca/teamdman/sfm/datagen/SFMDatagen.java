package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.data.loading.DatagenModLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFMDatagen {
    @SubscribeEvent
    public static void onGather(GatherDataEvent event) {
        if (!DatagenModLoader.isRunningDataGen()) return;
        if (event.includeServer()) {
            event.getGenerator().addProvider(event.includeClient(), new SFMBlockStatesAndModelsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMItemModelsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMBlockTagsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMLootTablesDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMRecipesDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMLanguageProviderDatagen(event));
        }
    }
}
