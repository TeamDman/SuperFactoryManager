package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PrintingPressBlockEntityRenderer implements BlockEntityRenderer<PrintingPressBlockEntity, PrintingPressRenderState> {
    private final ItemModelResolver itemModelResolver;

    private final ItemStackRenderState scratchPaper = new ItemStackRenderState();
    private final ItemStackRenderState scratchDye   = new ItemStackRenderState();
    private final ItemStackRenderState scratchForm  = new ItemStackRenderState();

    public PrintingPressBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    @Override
    public PrintingPressRenderState createRenderState() {
        return new PrintingPressRenderState();
    }

    @Override
    public void submit(PrintingPressRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        int seed = (int) state.blockPos.asLong();

        poseStack.pushPose();
        poseStack.translate(0.5, 1, 0.6);
        rotate(poseStack);

        ItemStack[] stacks   = {state.form,  state.paper,  state.dye};
        ItemStackRenderState[] scratch = {scratchForm, scratchPaper, scratchDye};

        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                this.itemModelResolver.updateForTopItem(scratch[i], stacks[i], ItemDisplayContext.GROUND, null, null, seed + i);
                scratch[i].submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.translate(0.01, 0.01, 0.03);
            }
        }

        poseStack.popPose();
    }

    @Override
    public void extractRenderState(
            PrintingPressBlockEntity blockEntity,
            PrintingPressRenderState renderState,
            float partialTick,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);

        renderState.paper = blockEntity.getPaper().copy();
        renderState.dye   = blockEntity.getInk().copy();
        renderState.form  = blockEntity.getForm().copy();
    }

    @MCVersionDependentBehaviour
    private static void rotate(PoseStack poseStack) {
        var depthAxis = Axis.XP;
        poseStack.mulPose(depthAxis.rotationDegrees(-90));
    }
}
