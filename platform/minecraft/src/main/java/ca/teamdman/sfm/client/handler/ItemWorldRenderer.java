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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import static ca.teamdman.sfm.client.handler.NetworkPipeline.NETWORK_PIPELINE;

/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {

    // -------------------------------------------------------------------------
    // MIGRATION NOTES (1.21.1 -> 26.1)
    // -------------------------------------------------------------------------
    // 1. RenderPipeline replaces RenderType.CompositeState / shader JSON.
    //    We use RenderPipelines.DEBUG_FILLED_SNIPPET as the base snippet (provides
    //    POSITION_COLOR format + QUADS mode + translucent blending).
    //    Depth test is kept (LEQUAL) so boxes respect the world geometry.
    //    To render *through* walls, use .withDepthStencilState(Optional.empty()).
    //
    // 2. RenderSystem.enableBlend / enableDepthTest / etc. are gone.
    //    All blend and depth state is declared in the RenderPipeline.
    //
    // 3. RenderLevelStageEvent lost getStage() / Stage enum.
    //    Subscribe to the typed sub-event class directly:
    //      RenderLevelStageEvent.AfterTranslucentParticles
    //    The event now exposes getPoseStack() and getModelViewMatrix() directly.
    //    getCamera() is gone from the event; use minecraft.gameRenderer.getMainCamera().
    //
    // 4. VertexBuffer is gone; replaced by GpuBuffer.
    //    For *cached* buffers, use MappableRingBuffer (see VBOCache).
    //    Upload via CommandEncoder#mapBuffer, draw via RenderPass.
    //    Always call RenderSystem.getDynamicUniforms().writeTransform(...) and
    //    RenderSystem.bindDefaultUniforms(renderPass) so the shader gets
    //    ModelViewMat / ProjMat / ColorModulator populated automatically.
    //
    // 5. Tesselator.getInstance().begin(...) is gone.
    //    Use: new BufferBuilder(allocator, mode, format)
    //    with a ByteBufferBuilder allocator.
    //
    // 6. Camera.getPosition() → Camera.position() (since 1.21.2).
    //
    // 7. levelRenderer.ticks field → levelRenderer.getTicks() method.
    // -------------------------------------------------------------------------
    private static final VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_COLOR;
    private static final VertexFormat.Mode VERTEX_MODE = VertexFormat.Mode.QUADS;

    // Uniform defaults for the draw call — identity / no offset / no texture warp
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static final int capabilityColor = ARGB.color(100, 100, 0, 255);
    private static final int capabilityColorLimitedView = ARGB.color(100, 0, 100, 255);
    private static final int cableColor = ARGB.color(100, 100, 255, 0);
    private static final int noNetworkErrorColor = ARGB.color(200, 255, 50, 50);

    private static final VBOCache vboCache = new VBOCache();

    // ByteBufferBuilder allocator shared for building mesh data each frame.
    // RenderType.SMALL_BUFFER_SIZE is a convenient constant (~256 KB).
    private static final ByteBufferBuilder MESH_ALLOCATOR =
            new ByteBufferBuilder(net.minecraft.client.renderer.rendertype.RenderType.SMALL_BUFFER_SIZE);

    // Subscribe to the typed sub-event class instead of checking getStage().
    // AfterTranslucentParticles replaces the old Stage.AFTER_PARTICLES.
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void renderOverlays(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        // PoseStack is now obtained directly from the event.
        PoseStack poseStack = event.getPoseStack();
        // Camera is no longer on the event; fetch from gameRenderer.
        Camera camera = minecraft.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        ItemStack held;
        boolean rendered = false;
        if ((held = getHeldItemOfType(player, NetworkToolItem.class)) != null) {
            handleNetworkTool(event, poseStack, camera, bufferSource, held);
            rendered = true;
        }
        if ((held = getHeldItemOfType(player, LabelGunItem.class)) != null) {
            handleLabelGun(event, poseStack, camera, bufferSource, held);
            rendered = true;
        }
        if (!rendered) {
            vboCache.clear();
        }
    }

    // Thanks @tigres810
    public static @Nullable BlockPos lookingAt() {
        HitResult rt = Minecraft.getInstance().hitResult;
        if (rt == null) return null;

        double x = rt.getLocation().x;
        double y = rt.getLocation().y;
        double z = rt.getLocation().z;

        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        Vec3 lookAngle = player.getLookAngle();

        if ((x % 1 == 0) && (lookAngle.x < 0)) x -= 0.01;
        if ((y % 1 == 0) && (lookAngle.y < 0)) y -= 0.01;
        if ((z % 1 == 0) && (lookAngle.z < 0)) z -= 0.01;

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
            Camera camera,
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

        // Draw boxes — blend/depth managed by OVERLAY_PIPELINE inside drawVbo.
        BlockPosSet labelledPositions = new BlockPosSet(labelsByPosition.keySet());
        drawVbo(
                VBOKind.LABEL_GUN_CAPABILITIES,
                poseStack,
                camera,
                labelledPositions,
                viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor
        );

        bufferSource.endBatch();
    }

    private static void handleNetworkTool(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            Camera camera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack networkTool
    ) {
        if (!NetworkToolItem.getOverlayEnabled(networkTool)) return;
        BlockPosSet cablePositions = NetworkToolItem.getCablePositions(networkTool);
        BlockPosSet capabilityPositions = NetworkToolItem.getCapabilityProviderPositions(networkTool);

        var selectedPos = NetworkToolItem.getSelectedNetworkBlockPos(networkTool);
        if (cablePositions.isEmpty() && selectedPos != null) {
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, camera, BlockPosSet.of(selectedPos), noNetworkErrorColor);
        } else {
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, camera, cablePositions, cableColor);
            drawVbo(VBOKind.NETWORK_TOOL_CAPABILITIES, poseStack, camera, capabilityPositions, capabilityColor);
        }

        bufferSource.endBatch();
    }

    /**
     * Draws a cached GpuBuffer of coloured block-face quads into the world.
     * <p>
     * In 26.1 the draw path is:
     *   1. Obtain a sequential index buffer from RenderSystem.
     *   2. Open a RenderPass on the main render target.
     *   3. Set the pipeline, bind default uniforms (fills ModelViewMat / ProjMat
     *      automatically from RenderSystem state), upload DynamicTransforms.
     *   4. Bind vertex + index buffers and call drawIndexed.
     */
    private static void drawVbo(
            VBOKind vboKind,
            PoseStack poseStack,
            Camera camera,
            BlockPosSet positions,
            int color
    ) {
        if (positions.isEmpty()) return;

//        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        GpuBuffer gpuBuffer = vboCache.getVBO(
                vboKind,
                positions,
                ARGB.red(color),
                ARGB.green(color),
                ARGB.blue(color),
                ARGB.alpha(color)
        );
        if (gpuBuffer == null) return;

        int vertexCount = positions.size() * 6 /*faces*/ * 4 /*verts per face*/;
        if (vertexCount <= 0) return;

        // Build the sequential index buffer for QUADS.
        RenderSystem.AutoStorageIndexBuffer indexBuffer =
                RenderSystem.getSequentialBuffer(VERTEX_MODE);

        // Apply camera translation into poseStack so geometry is in world space.
//        poseStack.pushPose();
//        poseStack.translate(-camera.position().x, -camera.position().y, -camera.position().z);
//        poseStack.mulPose(camera.rotation().invert(new Quaternionf()));

        Matrix4f viewMatrix = new Matrix4f()
                .rotate(camera.rotation().invert(new Quaternionf()))
                .translate((float)-camera.position().x, (float)-camera.position().y, (float)-camera.position().z);

        // Write the model-view transform + color modulator into the dynamic-uniforms
        // ring buffer.  This mirrors the Fabric example exactly and is mandatory —
        // without it the pipeline's ModelViewMat uniform is unset.
        GpuBufferSlice dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(
                        viewMatrix,
                        COLOR_MODULATOR,
                        MODEL_OFFSET,
                        TEXTURE_MATRIX
                );

        Minecraft minecraft = Minecraft.getInstance();
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "sfm overlay boxes",
                        minecraft.getMainRenderTarget().getColorTextureView(),
                        OptionalInt.empty(),
                        minecraft.getMainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            renderPass.setPipeline(NETWORK_PIPELINE);
            // bindDefaultUniforms populates ProjectionMatrix, ModelViewMatrix, etc.
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, gpuBuffer);
            renderPass.setIndexBuffer(indexBuffer.getBuffer(vertexCount), indexBuffer.type());
            renderPass.drawIndexed(0, 0, vertexCount / 4 * 6, 1);
        }

//        poseStack.popPose();
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

    /**
     * Caches GPU vertex buffers per VBOKind using {@link MappableRingBuffer}.
     *
     * <p>Migration from 1.21.1:
     * <ul>
     *   <li>{@code VertexBuffer} → {@link MappableRingBuffer} (a ring of {@link GpuBuffer}s
     *       that rotates each frame to avoid GPU/CPU sync stalls).</li>
     *   <li>Upload uses {@code CommandEncoder#mapBuffer} + {@code MemoryUtil#memCopy},
     *       matching the Fabric reference example.</li>
     *   <li>{@code levelRenderer.ticks} field → {@code getLevelRenderer().getTicks()} method.</li>
     * </ul>
     */
    private static class VBOCache {

        private final EnumMap<VBOKind, VBOEntry> cache = new EnumMap<>(VBOKind.class);
        private int lastChangeCheckTick = -1;

        public @Nullable GpuBuffer getVBO(
                VBOKind kind,
                BlockPosSet positions,
                int r,
                int g,
                int b,
                int a
        ) {
            if (positions.isEmpty()) return null;

            @Nullable VBOEntry entry = cache.get(kind);
            boolean shouldRebuild = (entry == null);

            // Throttle expensive equality checks to once per render tick.
            int currentTick = Minecraft.getInstance().levelRenderer.getTicks();
            if (entry != null
                    && currentTick != lastChangeCheckTick
                    && !entry.positions.equals(positions)) {
                lastChangeCheckTick = currentTick;
                shouldRebuild = true;
            }

            if (shouldRebuild) {
                if (entry != null) {
                    entry.ringBuffer.close();
                }
                MappableRingBuffer ringBuffer = createRingBuffer(positions, r, g, b, a);
                entry = new VBOEntry(new BlockPosSet(positions), ringBuffer);
                cache.put(kind, entry);
            }

            // Rotate so the next upload slot is ready while the GPU finishes the current one.
            // This causes flickering?
//            entry.ringBuffer.rotate();

            return entry.ringBuffer.currentBuffer();
        }

        public void clear() {
            for (VBOEntry entry : cache.values()) {
                entry.ringBuffer.close();
            }
            cache.clear();
        }

        /**
         * Builds the mesh and uploads it into a new {@link MappableRingBuffer}.
         *
         * <p>In 1.21.1 this used:
         * <pre>
         *   BufferBuilder bb = Tesselator.getInstance().begin(mode, format);
         *   // ... write vertices ...
         *   MeshData mesh = bb.buildOrThrow();
         *   VertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer());
         * </pre>
         *
         * <p>In 26.1:
         * <ul>
         *   <li>{@code Tesselator.begin} is gone; use
         *       {@code new BufferBuilder(allocator, mode, format)} with an
         *       explicit {@link ByteBufferBuilder} allocator.</li>
         *   <li>{@code uploadImmediateVertexBuffer} returns a transient buffer
         *       not suitable for caching; use {@link MappableRingBuffer} instead.</li>
         * </ul>
         */
        @HelpsWithMinecraftVersionIndependence
        private MappableRingBuffer createRingBuffer(
                BlockPosSet positions,
                int r,
                int g,
                int b,
                int a
        ) {
            // Build the mesh on the CPU.
            PoseStack poseStack = new PoseStack();
            BufferBuilder bufferBuilder = new BufferBuilder(MESH_ALLOCATOR, VERTEX_MODE, VERTEX_FORMAT);

            for (BlockPos blockPos : positions.blockPosIterator()) {
                poseStack.pushPose();
                poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                Matrix4f matrix4f = poseStack.last().pose();
                for (Direction face : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    if (!positions.contains(blockPos.relative(face))) {
                        writeFaceVertices(bufferBuilder, matrix4f, face, r, g, b, a);
                    }
                }
                poseStack.popPose();
            }

            MeshData meshData = bufferBuilder.buildOrThrow();
            MeshData.DrawState drawState = meshData.drawState();
            MappableRingBuffer ringBuffer = getMappableRingBuffer(positions, drawState);

            // Copy vertex data into the current ring-buffer slot.
            com.mojang.blaze3d.systems.CommandEncoder encoder =
                    RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                    ringBuffer.currentBuffer().slice(0, meshData.vertexBuffer().remaining()),
                    false,
                    true)) {
                MemoryUtil.memCopy(meshData.vertexBuffer(), mapped.data());
            }
            meshData.close();

            return ringBuffer;
        }

        private static MappableRingBuffer getMappableRingBuffer(BlockPosSet positions, MeshData.DrawState drawState) {
            VertexFormat format = drawState.format();

            int vertexBufferSize = drawState.vertexCount() * format.getVertexSize();

            // MappableRingBuffer: a small ring of GpuBuffers that can be mapped
            // by the CPU while the GPU uses the previous buffer, avoiding stalls.
            return new MappableRingBuffer(
                    () -> "sfm overlay vbo " + positions.hashCode(),
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    vertexBufferSize
            );
        }

        private record VBOEntry(
                BlockPosSet positions,
                MappableRingBuffer ringBuffer
        ) {}
    }
}