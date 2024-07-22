package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@EventBusSubscriber(modid = SFM.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {
    private static final int BUFFER_SIZE = 256;
    @SuppressWarnings("deprecation")
    private static final RenderType RENDER_TYPE = RenderType.create(
            "sfmlabels",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState
                    .builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
                    .setTransparencyState(
                            new RenderStateShard.TransparencyStateShard(
                                    "src_to_one",
                                    () -> {
                                        RenderSystem.enableBlend();
                                        RenderSystem.blendFunc(
                                                GlStateManager.SourceFactor.SRC_ALPHA,
                                                GlStateManager.DestFactor.ONE
                                        );
                                    },
                                    () -> {
                                        RenderSystem.disableBlend();
                                        RenderSystem.defaultBlendFunc();
                                    }
                            )
                    )
                    .createCompositeState(true)
    );
    @Nullable
    private static VertexBuffer capabilityProviderVBO;
    @Nullable
    private static VertexBuffer cableVBO;

    @SubscribeEvent
    public static void renderLabelHighlights(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        var labelGun = player.getMainHandItem();
        if (!(labelGun.getItem() instanceof LabelGunItem)) labelGun = player.getOffhandItem();
        if (labelGun.getItem() instanceof LabelGunItem) {
            var labels = LabelPositionHolder.from(labelGun);
            var labelPositions = HashMultimap.<BlockPos, String>create();
            labels.forEach((label, pos1) -> labelPositions.put(pos1, label));

            var poseStack = event.getPoseStack();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

            RenderSystem.disableDepthTest();

            poseStack.pushPose();
            poseStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

            { // draw labels
                for (var entry : labelPositions.asMap().entrySet()) {
                    drawLabel(poseStack, camera, entry.getKey(), bufferSource, entry.getValue());
                }
            }
            { // draw highlights
                RENDER_TYPE.setupRenderState();

                if (capabilityProviderVBO == null) {
                    capabilityProviderVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    capabilityProviderVBO.bind();
                    capabilityProviderVBO.upload(createCapabilityProviderVBO());
                } else {
                    capabilityProviderVBO.bind();
                }

                for (var pos : labelPositions.keySet()) {
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

                    //noinspection DataFlowIssue
                    capabilityProviderVBO.drawWithShader(
                            poseStack.last().pose(),
                            event.getProjectionMatrix(),
                            GameRenderer.getPositionColorShader()
                    );
                    poseStack.popPose();
                }

                VertexBuffer.unbind();
                RENDER_TYPE.clearRenderState();
            }
            bufferSource.endBatch();
            poseStack.popPose();
            RenderSystem.enableDepthTest();
        }

        var networkTool = player.getMainHandItem();
        if (!(networkTool.getItem() instanceof NetworkToolItem)) networkTool = player.getOffhandItem();
        if (networkTool.getItem() instanceof NetworkToolItem) {
            List<BlockPos> cablePositions = new ArrayList<>();
            List<BlockPos> capabilityProviderPositions = new ArrayList<>();
            // gather network positions from item
            networkTool.getOrCreateTag().getList("networks", Tag.TAG_COMPOUND).forEach(networkTag -> {
                ((CompoundTag) networkTag).getList("cable_positions", Tag.TAG_COMPOUND)
                        .stream()
                        .map(CompoundTag.class::cast)
                        .map(NbtUtils::readBlockPos)
                        .forEach(cablePositions::add);
                ((CompoundTag) networkTag)
                        .getList("capability_provider_positions", Tag.TAG_COMPOUND)
                        .stream()
                        .map(CompoundTag.class::cast)
                        .map(NbtUtils::readBlockPos)
                        .forEach(capabilityProviderPositions::add);
            });

            var poseStack = event.getPoseStack();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

            RenderSystem.disableDepthTest();

            poseStack.pushPose();
            poseStack.translate(
                    -camera.getPosition().x,
                    -camera.getPosition().y,
                    -camera.getPosition().z
            );

            { // draw highlights
                RENDER_TYPE.setupRenderState();

                if (capabilityProviderVBO == null) {
                    capabilityProviderVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    capabilityProviderVBO.bind();
                    capabilityProviderVBO.upload(createCapabilityProviderVBO());
                } else {
                    capabilityProviderVBO.bind();
                }

                for (var blockPos : capabilityProviderPositions) {
                    poseStack.pushPose();
                    poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

                    //noinspection DataFlowIssue
                    capabilityProviderVBO.drawWithShader(
                            poseStack.last().pose(),
                            event.getProjectionMatrix(),
                            GameRenderer.getPositionColorShader()
                    );
                    poseStack.popPose();
                }


                if (cableVBO == null) {
                    cableVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    cableVBO.bind();
                    cableVBO.upload(createCableVBO());
                } else {
                    cableVBO.bind();
                }

                for (var blockPos : cablePositions) {
                    poseStack.pushPose();
                    poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

                    //noinspection DataFlowIssue
                    cableVBO.drawWithShader(
                            poseStack.last().pose(),
                            event.getProjectionMatrix(),
                            GameRenderer.getPositionColorShader()
                    );
                    poseStack.popPose();
                }

                VertexBuffer.unbind();
                RENDER_TYPE.clearRenderState();
            }
            bufferSource.endBatch();
            poseStack.popPose();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawLabel(
            PoseStack poseStack,
            Camera camera,
            BlockPos pos,
            MultiBufferSource mbs,
            Collection<String> labels
    ) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);
        Font font = Minecraft.getInstance().font;
        poseStack.translate(0, labels.size() * (font.lineHeight + 0.1) / -2f, 0);
        for (var label : labels) {
            font.drawInBatch(
                    label,
                    -font.width(label) / 2f,
                    0,
                    -0x1,
                    false,
                    poseStack.last().pose(),
                    mbs,
                    Font.DisplayMode.SEE_THROUGH,
                    0,
                    0xF000F0
            );
            poseStack.translate(0, font.lineHeight + 0.1, 0);
        }
        poseStack.popPose();
    }

    public static MeshData createCapabilityProviderVBO() {
        return createShape(100, 0, 255, 100);
    }

    public static MeshData createCableVBO() {
        return createShape(100, 255, 0, 100);
    }

    public static MeshData createShape(int r, int g, int b, int a) {
        var builder = new BufferBuilder(4 * 6 * 8);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        builder.vertex(0F, 1F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 1F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 1F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 1F, 0F).color(r, g, b, a).endVertex();

        builder.vertex(0F, 1F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 1F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 0F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 0F).color(r, g, b, a).endVertex();

        builder.vertex(1F, 1F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 1F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 0F, 1F).color(r, g, b, a).endVertex();

        builder.vertex(0F, 1F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 1F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 1F).color(r, g, b, a).endVertex();

        builder.vertex(1F, 0F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 0F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 1F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 1F, 1F).color(r, g, b, a).endVertex();

        builder.vertex(1F, 0F, 0F).color(r, g, b, a).endVertex();
        builder.vertex(1F, 0F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 1F).color(r, g, b, a).endVertex();
        builder.vertex(0F, 0F, 0F).color(r, g, b, a).endVertex();

        return builder.end();
    }

}
