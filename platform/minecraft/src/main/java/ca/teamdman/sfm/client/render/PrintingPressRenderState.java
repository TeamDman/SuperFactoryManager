package ca.teamdman.sfm.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class PrintingPressRenderState extends BlockEntityRenderState {
    public ItemStack paper = ItemStack.EMPTY;
    public ItemStack dye   = ItemStack.EMPTY;
    public ItemStack form  = ItemStack.EMPTY;
}
