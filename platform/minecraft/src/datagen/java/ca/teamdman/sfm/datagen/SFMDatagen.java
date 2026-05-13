package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import java.util.List;
import java.util.Set;

public class SFMDatagen {
    @SFMSubscribeEvent
    public static void onGather(GatherDataEvent.Client event) {
        if (!DatagenModLoader.isRunningDataGen()) return;

        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();

        generator.addProvider(true, new SFMBlockStatesAndModelsDatagen(packOutput));
//        generator.addProvider(true, new SFMItemModelsDatagen(packOutput));

        event.createProvider((output, lookupProvider) -> new SFMLootTablesDatagen(
                output,
                Set.of(),
                List.of(),
            lookupProvider
        ));

        event.createProvider(SFMBlockTagsDatagen::new);
        event.createProvider(SFMRecipesDatagen.Runner::new);
        event.createProvider(SFMLanguageProviderDatagen::new);
    }
}
