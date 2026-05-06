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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PrintingPressBlockEntityRenderer implements BlockEntityRenderer<PrintingPressBlockEntity, PrintingPressRenderState> {
    private final ItemModelResolver itemModelResolver;

    public PrintingPressBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    @Override
    public PrintingPressRenderState createRenderState() {
        return new PrintingPressRenderState();
    }

    @Override
    public void submit(PrintingPressRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1, 0.6);
        rotate(poseStack);

        for (var stack : new ItemStackRenderState[]{state.form, state.paper, state.dye}) {
            if (!stack.isEmpty()) {
                stack.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
        Level level = blockEntity.getLevel();
        int seed = (int) blockEntity.getBlockPos().asLong();

        this.itemModelResolver.updateForTopItem(
                renderState.paper,
                blockEntity.getPaper(),
                ItemDisplayContext.GROUND,
                level,
                null,
                seed
        );
        this.itemModelResolver.updateForTopItem(
                renderState.dye,
                blockEntity.getInk(),
                ItemDisplayContext.GROUND,
                level,
                null,
                seed + 1
        );
        this.itemModelResolver.updateForTopItem(
                renderState.form,
                blockEntity.getForm(),
                ItemDisplayContext.GROUND,
                level,
                null,
                seed + 2
        );
    }

    @MCVersionDependentBehaviour
    private static void rotate(PoseStack poseStack) {
        var depthAxis = Axis.XP;
        poseStack.mulPose(depthAxis.rotationDegrees(-90));
    }
}
