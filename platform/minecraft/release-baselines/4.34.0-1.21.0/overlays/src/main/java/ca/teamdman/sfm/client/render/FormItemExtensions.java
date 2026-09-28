package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public class FormItemExtensions implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer RENDERER = new FormItemRenderer();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return RENDERER;
    }

    @MCVersionDependentBehaviour // 1.21 this replaces FormItem#initializeClient
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new FormItemExtensions(), SFMItems.FORM.get());
    }
}
