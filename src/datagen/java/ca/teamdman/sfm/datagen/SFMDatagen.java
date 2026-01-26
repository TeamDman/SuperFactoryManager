package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.data.loading.DatagenModLoader;

public class SFMDatagen {
    @SFMSubscribeEvent
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
