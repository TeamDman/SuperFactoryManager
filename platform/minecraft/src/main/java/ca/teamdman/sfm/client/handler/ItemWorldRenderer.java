package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.HelpsWithMinecraftVersionIndependence;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.common.util.SFMDist;
import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 * https://github.com/tasgon/observable/blob/c3c5a0d0385e0b2c758729bdd935f103122f0f85/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {
    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;

    private static final int capabilityColor = ARGB.color(100, 100, 0, 255);
    private static final int capabilityColorLimitedView = ARGB.color(100, 0, 100, 255);
    private static final int cableColor = ARGB.color(100, 100, 255, 0);
    private static final int noNetworkErrorColor = ARGB.color(200, 255, 50, 50);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private MappableRingBuffer vertexBuffer;

    // We subscribe only on the sub-event class instead of checking getStage():
    // RenderLevelStageEvent.AfterTranslucentParticles replaces Stage.AFTER_PARTICLES.
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void renderOverlays(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        PoseStack poseStack = event.getPoseStack();
        CameraRenderState camera =  event.getLevelRenderState().cameraRenderState;

        boolean rendered = false;
        ItemStack held;
        if ((held = getHeldItemOfType(player, NetworkToolItem.class)) != null) {
            handleNetworkTool(event, poseStack, camera, bufferSource, held);
            rendered = true;
        }
        if ((held = getHeldItemOfType(player, LabelGunItem.class)) != null) {
            handleLabelGun(event, poseStack, camera, bufferSource, held);
            rendered = true;
        }
    }

    // Thanks @tigres810
    public static @Nullable BlockPos lookingAt() {
        HitResult rt = Minecraft.getInstance().hitResult;
        if (rt == null) return null;

        double x = (rt.getLocation().x);
        double y = (rt.getLocation().y);
        double z = (rt.getLocation().z);

        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        Vec3 lookAngle = player.getLookAngle();
        double xla = lookAngle.x;
        double yla = lookAngle.y;
        double zla = lookAngle.z;

        if ((x % 1 == 0) && (xla < 0)) x -= 0.01;
        if ((y % 1 == 0) && (yla < 0)) y -= 0.01;
        if ((z % 1 == 0) && (zla < 0)) z -= 0.01;

        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static @Nullable ItemStack getHeldItemOfType(LocalPlayer player, Class<?> itemClass) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (itemClass.isInstance(mainHandItem.getItem())) return mainHandItem;
        ItemStack offhandItem = player.getOffhandItem();
        if (itemClass.isInstance(offhandItem.getItem())) return offhandItem;
        return null;
    }

    private static void handleLabelGun(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            CameraRenderState camera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack labelGun
    ) {
        LabelGunItem.LabelGunViewMode viewMode = LabelGunItem.getViewMode(labelGun);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(labelGun);
        HashMultimap<BlockPos, String> labelsByPosition = HashMultimap.create();
        String activeLabel = LabelGunItem.getActiveLabel(labelGun);
        BlockPos lookingAtPos = ItemWorldRenderer.lookingAt();

        switch (viewMode) {
            case SHOW_ALL -> //noinspection RedundantLabeledSwitchRuleCodeBlock
            {
                labelPositionHolder.forEach((label, pos) -> labelsByPosition.put(pos, label));
            }
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK -> {
                if (!activeLabel.isEmpty()) {
                    labelPositionHolder.forEach((label, pos) -> {
                        if (label.equals(activeLabel)) labelsByPosition.put(pos, label);
                    });
                }
                if (lookingAtPos != null) {
                    for (String lbl : labelPositionHolder.getLabels(lookingAtPos)) {
                        labelsByPosition.put(lookingAtPos, lbl);
                    }
                }
            }
            case SHOW_ONLY_TARGETED_BLOCK -> {
                if (lookingAtPos != null) {
                    for (String lbl : labelPositionHolder.getLabels(lookingAtPos)) {
                        labelsByPosition.put(lookingAtPos, lbl);
                    }
                }
            }
        }

        // Depth test is now controlled by the pipeline, not by RenderSystem calls.
        // The OVERLAY_PIPELINE has depth-test disabled, matching the original intent.

        // Draw labels
        poseStack.pushPose();
        // Camera.getPosition() → Camera.position() since 1.21.2
        poseStack.translate(-camera.position().x, -camera.position().y, -camera.position().z);
        for (Map.Entry<BlockPos, Collection<String>> entry : labelsByPosition.asMap().entrySet()) {
            BlockPos pos = entry.getKey();
            Collection<String> labels = entry.getValue();
            drawLabelsForPos(poseStack, camera, pos, bufferSource, labels);
        }
        poseStack.popPose();

        // Draw boxes — depth/blend state managed by the pipeline inside drawVbo.
        BlockPosSet labelledPositions = new BlockPosSet(labelsByPosition.keySet());
        drawVbo(
                VBOKind.LABEL_GUN_CAPABILITIES,
                poseStack,
                labelledPositions,
                viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor,
                event
        );

        bufferSource.endBatch();
    }

    private static void handleNetworkTool(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            CameraRenderState ignoredCamera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack networkTool
    ) {
        if (!NetworkToolItem.getOverlayEnabled(networkTool)) return;
        BlockPosSet cablePositions = NetworkToolItem.getCablePositions(networkTool);
        BlockPosSet capabilityPositions = NetworkToolItem.getCapabilityProviderPositions(networkTool);

        // Depth/blend state handled by pipeline inside drawVbo.
        var selectedPos = NetworkToolItem.getSelectedNetworkBlockPos(networkTool);
        if (cablePositions.isEmpty() && selectedPos != null) {
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, BlockPosSet.of(selectedPos), noNetworkErrorColor, event);
        } else {
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, cablePositions, cableColor, event);
            drawVbo(VBOKind.NETWORK_TOOL_CAPABILITIES, poseStack, capabilityPositions, capabilityColor, event);
        }

        bufferSource.endBatch();
    }

    private static void drawVbo(
            VBOKind vboKind,
            BlockPosSet positions,
            int color,
            RenderLevelStageEvent event
    ) {
        if (positions.isEmpty()) return;

        int r = ARGB.red(color),
                g = ARGB.green(color),
                b = ARGB.blue(color),
                a = ARGB.alpha(color);

        PoseStack geomStack = new PoseStack();
        for (BlockPos blockPos : positions.blockPosIterator()) {
            geomStack.pushPose();
            geomStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            Matrix4f matrix = geomStack.last().pose();
            for (Direction face : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                if (!positions.contains(blockPos.relative(face))) {
                    writeFaceVertices(bufferBuilder, matrix, face, r, g, b, a);
                }
            }
            geomStack.popPose();
        }
    }

    private static void drawLabelsForPos(
            PoseStack poseStack,
            CameraRenderState camera,
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
        for (String label : labels) {
            SFMFontUtils.drawInBatch(
                    label,
                    font,
                    -font.width(label) / 2f,
                    0,
                    false,
                    true,
                    poseStack.last().pose(),
                    mbs
            );
            poseStack.translate(0, font.lineHeight + 0.1, 0);
        }
        poseStack.popPose();
    }

    @HelpsWithMinecraftVersionIndependence
    private static void writeVertex(
            VertexConsumer builder,
            Matrix4f matrix4f,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b,
            int a
    ) {
        builder.addVertex(matrix4f, x, y, z).setColor(r, g, b, a);
    }

    private static void writeFaceVertices(
            VertexConsumer builder,
            Matrix4f matrix4f,
            Direction direction,
            int r,
            int g,
            int b,
            int a
    ) {
        double scale = 1 - ((double) direction.ordinal() / 25d);
        r = (int) (r * scale);
        g = (int) (g * scale);
        b = (int) (b * scale);
        a = (int) (a * scale);
        switch (direction) {
            case DOWN:
                writeVertex(builder, matrix4f, 0F, 0F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 0F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 0F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 0F, 1F, r, g, b, a);
                break;
            case UP:
                writeVertex(builder, matrix4f, 0F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 1F, 0F, r, g, b, a);
                break;
            case NORTH:
                writeVertex(builder, matrix4f, 0F, 0F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 1F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 0F, 0F, r, g, b, a);
                break;
            case SOUTH:
                writeVertex(builder, matrix4f, 1F, 0F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 0F, 1F, r, g, b, a);
                break;
            case WEST:
                writeVertex(builder, matrix4f, 0F, 0F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 1F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 0F, 0F, 0F, r, g, b, a);
                break;
            case EAST:
                writeVertex(builder, matrix4f, 1F, 0F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 0F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 1F, 1F, r, g, b, a);
                writeVertex(builder, matrix4f, 1F, 0F, 1F, r, g, b, a);
                break;
        }
    }

    private enum VBOKind {
        LABEL_GUN_CAPABILITIES,
        NETWORK_TOOL_CAPABILITIES,
        NETWORK_TOOL_CABLES
    }

    private static class VBOCache {
        // One MappableRingBuffer per VBOKind — sized on first use, resized if needed
        private final EnumMap<VBOKind, MappableRingBuffer> buffers = new EnumMap<>(VBOKind.class);

        /** Returns the ring buffer for this kind, (re)creating it if the required byte size grew. */
        public MappableRingBuffer getRingBuffer(VBOKind kind, int requiredBytes) {
            MappableRingBuffer existing = buffers.get(kind);
            if (existing != null && existing.size() >= requiredBytes) {
                return existing;
            }
            if (existing != null) existing.close();
            MappableRingBuffer fresh = new MappableRingBuffer(
                    () -> "sfm vbo " + kind.name(),
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    requiredBytes
            );
            buffers.put(kind, fresh);
            return fresh;
        }

        public void clear() {
            buffers.values().forEach(MappableRingBuffer::close);
            buffers.clear();
        }
    }
}