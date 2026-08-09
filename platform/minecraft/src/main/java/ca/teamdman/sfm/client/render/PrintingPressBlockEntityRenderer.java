package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;

public class PrintingPressBlockEntityRenderer implements BlockEntityRenderer<PrintingPressBlockEntity> {
    public PrintingPressBlockEntityRenderer(BlockEntityRendererProvider.Context ignoredPContext) {

    }

    @Override
    public void render(
            PrintingPressBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buf,
            int packedLight,
            int packedOverlay
    ) {
        var paper = blockEntity.getPaper();
        var dye = blockEntity.getInk();
        var form = blockEntity.getForm();
        poseStack.pushPose();
        poseStack.translate(0.5, 1, 0.6);
        rotate(poseStack);

        for (var stack : new ItemStack[]{form, paper, dye}) {
            if (!stack.isEmpty()) {
                renderItemStack(blockEntity, poseStack, buf, packedLight, packedOverlay, stack);
                poseStack.translate(0.01, 0.01, 0.03);
            }
        }
        poseStack.popPose();
    }

    @MCVersionDependentBehaviour
    private static void renderItemStack(
            PrintingPressBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource buf,
            int packedLight,
            int packedOverlay,
            ItemStack stack
    ) {
        Minecraft
                .getInstance()
                .getItemRenderer()
                .renderStatic(
                        stack,
                        ItemTransforms.TransformType.GROUND,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        buf,
                        (int) blockEntity.getBlockPos().asLong()
                );
    }

    @MCVersionDependentBehaviour
    private static void rotate(PoseStack poseStack) {
        var depthAxis = new Vector3f(1, 0, 0);
        poseStack.mulPose(depthAxis.rotationDegrees(-90));
    }
}
