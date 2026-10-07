package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV1Registration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV2Registration;
{% endcase %}
{% if features.canvas_text_editor %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.text_editor.SFMTextEditorV3Registration;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.client.text_editor.SFMDrawCanvasTextEditorRegistration;
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV1Registration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenV2Registration;
{% endcase %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

public class SFMTextEditors {
    public static final ResourceKey<Registry<ISFMTextEditorRegistration>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("text_editor");

    private static final SFMDeferredRegister<ISFMTextEditorRegistration> REGISTERER =
            new SFMDeferredRegisterBuilder<ISFMTextEditorRegistration>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMTextEditScreenV1Registration> V1 = REGISTERER.register(
            "v1",
            SFMTextEditScreenV1Registration::new
    );

    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMTextEditScreenV2Registration> V2 = REGISTERER.register(
            "v2",
            SFMTextEditScreenV2Registration::new
    );

{% if features.canvas_text_editor %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMTextEditorV3Registration> V3 = REGISTERER.register(
            "text_editor_v3",
            SFMTextEditorV3Registration::new
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static final SFMRegistryObject<ISFMTextEditorRegistration, SFMDrawCanvasTextEditorRegistration> DRAW = REGISTERER.register(
            "draw",
            SFMDrawCanvasTextEditorRegistration::new
{% endcase %}
    );

{% endif %}
    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }

    @MCVersionDependentBehaviour
    public static SFMRegistryWrapper<ISFMTextEditorRegistration> registry() {
        return REGISTERER.registry();
    }
}
