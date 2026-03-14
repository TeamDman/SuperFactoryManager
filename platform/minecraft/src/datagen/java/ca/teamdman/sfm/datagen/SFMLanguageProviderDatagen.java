package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticLanguageDataGen;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SFMLanguageProviderDatagen extends MCVersionAgnosticLanguageDataGen {
    public SFMLanguageProviderDatagen(GatherDataEvent event) {
        super(event, SFM.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        Set<String> seen = new HashSet<>();
        for (var entry : LocalizationKeys.getEntries()) {
            add(entry.key().get(), entry.value().get());
            seen.add(entry.key().get());
        }
        List<String> unmapped = new ArrayList<>();
        SFMWellKnownRegistries.ITEMS
                .entries()
                .stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(SFM.MOD_ID))
                .filter(entry -> !seen.contains(entry.getValue().getDescriptionId()))
                .map(entry -> entry.getValue().toString())
                .forEach(unmapped::add);
        SFMWellKnownRegistries.BLOCKS
                .entries()
                .stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(SFM.MOD_ID))
                .filter(entry -> !seen.contains(entry.getValue().getDescriptionId()))
                .map(entry -> entry.getValue().toString())
                .forEach(unmapped::add);
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException("Unmapped entries: " + String.join(", ", unmapped));
        }
    }
}
