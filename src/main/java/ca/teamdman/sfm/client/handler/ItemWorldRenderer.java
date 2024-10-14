package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMDataComponents;
import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = SFM.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {
    private static final int BUFFER_SIZE = 256;
    private static final RenderType RENDER_TYPE = RenderType.create(
            "sfm_overlay",
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

    private static final ColorRGBA capabilityColor = rgbaToColorRGBA(100, 0, 255, 100);
    private static final ColorRGBA cableColor = rgbaToColorRGBA(100, 255, 0, 100);
    @Nullable
    private static VertexBuffer[] capabilityVBO = new VertexBuffer[6];
    @Nullable
    private static VertexBuffer[] cableVBO = new VertexBuffer[6];

    @SubscribeEvent
    public static void renderOverlays(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();


        // Check for Network Tool
        // Handle before Label Gun as it also handles highlighting capabilities
        ItemStack networkTool = getHeldItemOfType(player, NetworkToolItem.class);
        if (networkTool != null) {
            handleNetworkTool(event, poseStack, camera, bufferSource, networkTool);
            return;
        }

        // Check for Label Gun
        ItemStack labelGun = getHeldItemOfType(player, LabelGunItem.class);
        if (labelGun != null) {
            handleLabelGun(event, poseStack, camera, bufferSource, labelGun);
            return;
        }
    }

    private static void handleLabelGun(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            Camera camera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack labelGun
    ) {
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(labelGun);
        HashMultimap<BlockPos, String> labelsByPosition = HashMultimap.create();
        labelPositionHolder.forEach((label, pos1) -> labelsByPosition.put(pos1, label));

        RenderSystem.disableDepthTest();
//        RenderSystem.disableCull();

        // Draw labels
        poseStack.pushPose();
        poseStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        for (var entry : labelsByPosition.asMap().entrySet()) {
            BlockPos pos = entry.getKey();
            Collection<String> labels = entry.getValue();
            drawLabelsForPos(poseStack, camera, pos, bufferSource, labels);
        }
        poseStack.popPose();

        // Draw boxes

        poseStack.pushPose();
        poseStack.mulPose(camera.rotation().invert());
        poseStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        RENDER_TYPE.setupRenderState();

        Set<BlockPos> labelKeySet = labelsByPosition.keySet();

        drawBoxes(event, poseStack, labelKeySet, capabilityVBO, capabilityColor);

        poseStack.popPose();
        RENDER_TYPE.clearRenderState();

        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
//        RenderSystem.enableCull();
    }

    private static void handleNetworkTool(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            Camera camera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack networkTool
    ) {
        Set<BlockPos> cablePositions = networkTool.getOrDefault(SFMDataComponents.CABLE_POSITIONS, new HashSet<>());
        Set<BlockPos> capabilityPositions = networkTool.getOrDefault(SFMDataComponents.CAPABILITY_POSITIONS, new HashSet<>());


        RenderSystem.disableDepthTest();
//        RenderSystem.disableCull();

        poseStack.pushPose();

        poseStack.mulPose(camera.rotation().invert());
        poseStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        RENDER_TYPE.setupRenderState();

        // Draw Cables
        drawBoxes(event, poseStack, cablePositions, cableVBO, cableColor);

        // Draw Capabilities
        drawBoxes(event, poseStack, capabilityPositions, capabilityVBO, capabilityColor);

        poseStack.popPose();
        RENDER_TYPE.clearRenderState();

        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
//        RenderSystem.enableCull();
    }

    private static MeshData createShape(Direction direction, ColorRGBA color) {
        ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(2 * 4 * 4);
        BufferBuilder builder = new BufferBuilder(
                byteBufferBuilder,
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );
        color = scaleColor(color, 1 - ((double) direction.ordinal() / 10));

        switch (direction) {
            case DOWN:
                builder.addVertex(0F, 0F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 0F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 0F, 1F).setColor(color.rgba());
                builder.addVertex(0F, 0F, 1F).setColor(color.rgba());
                break;
            case UP:
                builder.addVertex(0F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 0F).setColor(color.rgba());
                builder.addVertex(0F, 1F, 0F).setColor(color.rgba());
                break;
            case NORTH:
                builder.addVertex(0F, 0F, 0F).setColor(color.rgba());
                builder.addVertex(0F, 1F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 0F, 0F).setColor(color.rgba());
                break;
            case SOUTH:
                builder.addVertex(1F, 0F, 1F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(0F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(0F, 0F, 1F).setColor(color.rgba());
                break;
            case WEST:
                builder.addVertex(0F, 0F, 1F).setColor(color.rgba());
                builder.addVertex(0F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(0F, 1F, 0F).setColor(color.rgba());
                builder.addVertex(0F, 0F, 0F).setColor(color.rgba());
                break;
            case EAST:
                builder.addVertex(1F, 0F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 0F).setColor(color.rgba());
                builder.addVertex(1F, 1F, 1F).setColor(color.rgba());
                builder.addVertex(1F, 0F, 1F).setColor(color.rgba());
                break;
        }

        return builder.buildOrThrow();
    }

    private static ColorRGBA rgbaToColorRGBA(int red, int green, int blue, int alpha) {
        return new ColorRGBA((red << 24) | (green << 16) | (blue << 8) | alpha);
    }

    private static ColorRGBA scaleColor(ColorRGBA originalColor, double scale) {
        int color = originalColor.rgba();

        int red = (color >> 24) & 0xFF;
        int green = (color >> 16) & 0xFF;
        int blue = (color >> 8) & 0xFF;
        int alpha = color & 0xFF;

        // Apply the scale to RGB components
        red = Math.min(255, Math.max(0, (int) (red * scale)));
        green = Math.min(255, Math.max(0, (int) (green * scale)));
        blue = Math.min(255, Math.max(0, (int) (blue * scale)));

        // Return the adjusted color as a packed int (RGBA)
        return new ColorRGBA((red << 24) | (green << 16) | (blue << 8) | alpha);
    }

    @Nullable
    private static ItemStack getHeldItemOfType(LocalPlayer player, Class<?> itemClass) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (itemClass.isInstance(mainHandItem.getItem())) {
            return mainHandItem;
        }

        ItemStack offhandItem = player.getOffhandItem();
        if (itemClass.isInstance(offhandItem.getItem())) {
            return offhandItem;
        }

        return null; // Neither hand holds the item
    }

    private static void drawLabelsForPos(
            PoseStack poseStack,
            Camera camera,
            BlockPos pos,
            MultiBufferSource mbs,
            Collection<String> labels
    ) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
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

    private static void drawBoxes(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            Set<BlockPos> pPos,
            VertexBuffer[] faces,
            ColorRGBA color
    ) {
        for (Direction direction : Direction.values()) {
            int ordinal = direction.ordinal();

            VertexBuffer VBO;
            if (faces[ordinal] == null) {
                faces[ordinal] = new VertexBuffer(VertexBuffer.Usage.STATIC);
                VBO = faces[ordinal];
                VBO.bind();
                VBO.upload(createShape(direction, color));
            } else {
                VBO = faces[ordinal];
                VBO.bind();
            }

            Set<BlockPos> faceNotTouchingOtherOverlay = pPos.stream()
                    .filter(pos -> !pPos.contains(pos.relative(direction)))
                    .collect(Collectors.toSet());

            for (BlockPos blockPos : faceNotTouchingOtherOverlay) {
                poseStack.pushPose();
                poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

                //noinspection DataFlowIssue
                VBO.drawWithShader(
                        poseStack.last().pose(),
                        event.getProjectionMatrix(),
                        GameRenderer.getPositionColorShader()
                );
                poseStack.popPose();
            }
            VertexBuffer.unbind();
        }
    }
}
