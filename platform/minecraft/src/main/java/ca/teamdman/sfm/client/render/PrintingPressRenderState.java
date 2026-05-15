package ca.teamdman.sfm.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class PrintingPressRenderState extends BlockEntityRenderState {
    public ItemStack paper = ItemStack.EMPTY;
    public ItemStack dye   = ItemStack.EMPTY;
    public ItemStack form  = ItemStack.EMPTY;
}
