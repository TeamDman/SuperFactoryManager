package ca.teamdman.sfm.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;

public class PrintingPressRenderState extends BlockEntityRenderState {
    ItemStackRenderState paper;
    ItemStackRenderState dye;
    ItemStackRenderState form;
}
