package ca.teamdman.sfm.common.item;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.math.vector.Vector3f;

public class CraftingContractItemStackTileEntityRenderer extends
	ItemStackTileEntityRenderer {

	private static final ItemStack MISSING_OUTPUT_STACK = new ItemStack(Blocks.BARRIER);

	// prevent recursion
	public static boolean debounce = false;

	@Override
	public void renderByItem(
		ItemStack stack,
		TransformType p_239207_2_,
		MatrixStack matrixStack,
		IRenderTypeBuffer buffer,
		int combinedLight,
		int combinedOverlay
	) {
		debounce = true;
		try {
			if (!(stack.getItem() instanceof CraftingContractItem)) return;
			if (Minecraft.getInstance().level == null) return;

			ItemStack result = CraftingContractItem.getRecipe(
				stack,
				Minecraft.getInstance().level
			)
				.map(IRecipe::getResultItem)
				.orElse(MISSING_OUTPUT_STACK);

			ItemStack primary = stack;
			ItemStack secondary = result;
			if (Screen.hasShiftDown()) {
				primary = result;
				secondary = stack;
			}
			matrixStack.pushPose();
			matrixStack.translate(0.5,0.5,0);
			matrixStack.mulPose((new Vector3f(0,1,0)).rotationDegrees(180));
			Minecraft.getInstance().getItemRenderer().renderStatic(
				primary,
				TransformType.FIXED,
				combinedLight,
				combinedOverlay,
				matrixStack,
				buffer
			);
			matrixStack.translate(-0.3,-0.3,0.1);
			matrixStack.scale(0.7f,0.7f,1);
			Minecraft.getInstance().getItemRenderer().renderStatic(
				secondary,
				TransformType.FIXED,
				combinedLight,
				combinedOverlay,
				matrixStack,
				buffer
			);
			matrixStack.popPose();
		} finally {
			debounce = false;
		}
	}
}
