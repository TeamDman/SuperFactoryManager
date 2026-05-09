package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SFMClientTextEditorConfig {
    public final ModConfigSpec.BooleanValue showLineNumbers;
    public final ModConfigSpec.EnumValue<SFMTextEditorIntellisenseLevel> intellisenseLevel;
    public final ModConfigSpec.ConfigValue<String> preferredEditor;

    SFMClientTextEditorConfig(ModConfigSpec.Builder builder) {

        showLineNumbers = builder.define("showLineNumbers", false);
        intellisenseLevel = builder.defineEnum("intellisenseLevel", SFMTextEditorIntellisenseLevel.OFF);
        preferredEditor = builder.define("preferredEditor", "sfm:v1");
    }


    public static @NotNull ISFMTextEditorRegistration getPreferredTextEditor() {

        @Nullable Identifier id = SFMResourceLocation.tryParse(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get());
        if (id == null) {
            // Clobber the invalid ID
            SFMTextEditors.V1.getId().ifPresent(defaultId -> {
                SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(defaultId.identifier().toString());
            });
            return SFMTextEditors.V1.get();
        } else {
            return SFMTextEditors.registry()
                    .get(id).map(Holder.Reference::value)
                    .orElse(SFMTextEditors.V1.get());
        }
    }

}