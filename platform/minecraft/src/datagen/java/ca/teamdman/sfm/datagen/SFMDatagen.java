package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

public class SFMDatagen {
    @SFMSubscribeEvent
    public static void onGather(GatherDataEvent event) {
        if (!DatagenModLoader.isRunningDataGen()) return;

        event.getGenerator().addProvider(event.includeDev(), new SFMBlockStatesAndModelsDatagen(event));
        event.getGenerator().addProvider(event.includeDev(), new SFMItemModelsDatagen(event));
        event.getGenerator().addProvider(event.includeDev(), new SFMBlockTagsDatagen(event));
        event.getGenerator().addProvider(event.includeDev(), new SFMLootTablesDatagen(event));

        event.createProvider(SFMRecipesDatagen.Runner::new);

        event.getGenerator().addProvider(event.includeDev(), new SFMLanguageProviderDatagen(event));
    }
}
