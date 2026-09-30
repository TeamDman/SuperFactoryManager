package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticLanguageDataGen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
import net.minecraft.data.PackOutput;
{% endcase %}

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SFMLanguageProviderDatagen extends MCVersionAgnosticLanguageDataGen {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public SFMLanguageProviderDatagen(GatherDataEvent event) {
{% when '26.1.2' %}
    public SFMLanguageProviderDatagen(PackOutput output) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(event, SFM.MOD_ID, "en_us");
{% when '26.1.2' %}
        super(output, SFM.MOD_ID, "en_us");
{% endcase %}
    }

    public static List<LocalizationEntry> getEntries() {

        var rtn = new ArrayList<LocalizationEntry>();

        SFMAnnotationUtils.discoverAnnotations(SFMLocalizationDatagen.class)
                .forEach(annotationData -> {
                    // Load the class containing the field with the annotation
                    Class<?> parentClass = annotationData.tryLoadClass();

                    // Load the field
                    Field declaredField;
                    try {
                        declaredField = parentClass.getDeclaredField(annotationData.memberName());
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }

                    // Ensure the field is of the correct type
                    if (!declaredField.getType().equals(LocalizationEntry.class)) {
                        throw new RuntimeException("Field "
                                                   + declaredField.getName()
                                                   + " is not of type LocalizationEntry");
                    }

                    // Get the instance
                    LocalizationEntry entry;
                    try {
                        entry = (LocalizationEntry) declaredField.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    SFM.LOGGER.info(
                            "Found localization entry \"{}\" in {}#{}",
                            entry.key().get(),
                            parentClass.getName(),
                            declaredField.getName()
                    );

                    // Add to the results list
                    rtn.add(entry);
                });

        return rtn;
    }

    @Override
    protected void addTranslations() {

        Set<String> seen = new HashSet<>();
        for (var entry : getEntries()) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            add(entry.key().get(), entry.value().get());
{% when '26.1.2' %}
            this.add(entry.key().get(), entry.value().get());
{% endcase %}
            seen.add(entry.key().get());
        }
        List<String> unmapped = new ArrayList<>();
        SFMWellKnownRegistries.ITEMS
                .entries()
                .stream()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .filter(entry -> entry.getKey().location().getNamespace().equals(SFM.MOD_ID))
{% when '26.1.2' %}
                .filter(entry -> entry.getKey().identifier().getNamespace().equals(SFM.MOD_ID))
{% endcase %}
                .filter(entry -> !seen.contains(entry.getValue().getDescriptionId()))
                .map(entry -> entry.getValue().toString())
                .forEach(unmapped::add);
        SFMWellKnownRegistries.BLOCKS
                .entries()
                .stream()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .filter(entry -> entry.getKey().location().getNamespace().equals(SFM.MOD_ID))
{% when '26.1.2' %}
                .filter(entry -> entry.getKey().identifier().getNamespace().equals(SFM.MOD_ID))
{% endcase %}
                .filter(entry -> !seen.contains(entry.getValue().getDescriptionId()))
                .map(entry -> entry.getValue().toString())
                .forEach(unmapped::add);
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException("Unmapped entries: " + String.join(", ", unmapped));
        }
    }

}
