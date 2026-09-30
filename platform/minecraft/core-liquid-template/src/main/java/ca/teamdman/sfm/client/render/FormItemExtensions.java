package ca.teamdman.sfm.client.render;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
{% when '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
{% when '26.1.2' %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
{% endcase %}

public class FormItemExtensions implements IClientItemExtensions {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final BlockEntityWithoutLevelRenderer RENDERER = new FormItemRenderer();
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return RENDERER;
{% when '26.1.2' %}
    @MCVersionDependentBehaviour // 1.21 this replaces FormItem#initializeClient
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerSpecialRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                SFMResourceLocation.fromSFMPath("form"),
                FormItemRenderer.Unbaked.MAP_CODEC
        );
{% endcase %}
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
{% when '1.21', '1.21.1' %}

    @MCVersionDependentBehaviour // 1.21 this replaces FormItem#initializeClient
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new FormItemExtensions(), SFMItems.FORM.get());
    }
{% endcase %}
}
