package ca.teamdman.sfm.client.render;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class FormItemExtensions implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer RENDERER = new FormItemRenderer();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return RENDERER;
    }
}
