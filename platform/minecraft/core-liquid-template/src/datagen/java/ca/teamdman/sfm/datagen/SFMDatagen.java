package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.data.loading.DatagenModLoader;
{% when '1.20.2', '1.21', '1.21.1' %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
{% when '1.20.3', '1.20.4' %}
import ca.teamdman.sfm.SFM;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
{% when '26.1.2' %}
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import java.util.List;
import java.util.Set;
{% endcase %}

public class SFMDatagen {
    @SFMSubscribeEvent
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static void onGather(GatherDataEvent event) {
{% when '26.1.2' %}
    public static void onGather(GatherDataEvent.Client event) {
{% endcase %}
        if (!DatagenModLoader.isRunningDataGen()) return;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (event.includeServer()) {
            event.getGenerator().addProvider(event.includeClient(), new SFMBlockStatesAndModelsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMItemModelsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMBlockTagsDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMLootTablesDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMRecipesDatagen(event));
            event.getGenerator().addProvider(event.includeClient(), new SFMLanguageProviderDatagen(event));
        }
{% when '26.1.2' %}

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
{% endcase %}
    }
}
