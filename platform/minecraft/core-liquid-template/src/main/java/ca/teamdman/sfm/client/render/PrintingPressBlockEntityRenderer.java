package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
{% when '26.1.2' %}
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
{% endcase %}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.item.ItemDisplayContext;
{% when '26.1.2' %}
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class PrintingPressBlockEntityRenderer implements BlockEntityRenderer<PrintingPressBlockEntity> {
    public PrintingPressBlockEntityRenderer(BlockEntityRendererProvider.Context ignoredPContext) {
{% when '26.1.2' %}
public class PrintingPressBlockEntityRenderer implements BlockEntityRenderer<PrintingPressBlockEntity, PrintingPressRenderState> {
    private final ItemModelResolver itemModelResolver;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    private final ItemStackRenderState scratchPaper = new ItemStackRenderState();
    private final ItemStackRenderState scratchDye   = new ItemStackRenderState();
    private final ItemStackRenderState scratchForm  = new ItemStackRenderState();

    public PrintingPressBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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

{% when '26.1.2' %}
    public PrintingPressRenderState createRenderState() {
        return new PrintingPressRenderState();
    }

    @Override
    public void submit(
            PrintingPressRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        int seed = (int) state.blockPos.asLong();

{% endcase %}
        poseStack.pushPose();
        poseStack.translate(0.5, 1, 0.6);
        rotate(poseStack);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        for (var stack : new ItemStack[]{form, paper, dye}) {
            if (!stack.isEmpty()) {
                renderItemStack(blockEntity, poseStack, buf, packedLight, packedOverlay, stack);
{% when '26.1.2' %}
        ItemStack[] stacks   = {state.form,  state.paper,  state.dye};
        ItemStackRenderState[] scratch = {scratchForm, scratchPaper, scratchDye};

        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                this.itemModelResolver.updateForTopItem(scratch[i], stacks[i], ItemDisplayContext.GROUND, null, null, seed + i);
                scratch[i].submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
{% endcase %}
                poseStack.translate(0.01, 0.01, 0.03);
            }
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

{% endcase %}
        poseStack.popPose();
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @MCVersionDependentBehaviour
    private static void renderItemStack(
{% when '26.1.2' %}
    @Override
    public void extractRenderState(
{% endcase %}
            PrintingPressBlockEntity blockEntity,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            PoseStack poseStack,
            MultiBufferSource buf,
            int packedLight,
            int packedOverlay,
            ItemStack stack
{% when '26.1.2' %}
            PrintingPressRenderState renderState,
            float partialTick,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
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
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        Minecraft
                .getInstance()
                .getItemRenderer()
                .renderStatic(
                        stack,
                        ItemDisplayContext.GROUND,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        buf,
                        blockEntity.getLevel(),
                        (int) blockEntity.getBlockPos().asLong()
                );
{% when '26.1.2' %}
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);

        renderState.paper = blockEntity.getPaper().copy();
        renderState.dye   = blockEntity.getInk().copy();
        renderState.form  = blockEntity.getForm().copy();
{% endcase %}
    }

    @MCVersionDependentBehaviour
    private static void rotate(PoseStack poseStack) {
{% case minecraft_version %}
{% when '1.19.2' %}
        var depthAxis = new Vector3f(1, 0, 0);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        var depthAxis = Axis.XP;
{% endcase %}
        poseStack.mulPose(depthAxis.rotationDegrees(-90));
    }
}
