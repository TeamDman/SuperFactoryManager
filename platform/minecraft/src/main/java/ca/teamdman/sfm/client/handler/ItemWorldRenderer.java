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
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
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

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 * https://github.com/tasgon/observable/blob/c3c5a0d0385e0b2c758729bdd935f103122f0f85/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {

    // -------------------------------------------------------------------------
    // MIGRATION NOTES (1.21.1 -> 26.1.2)
    // -------------------------------------------------------------------------
    // 1. RenderType.CompositeState / RenderStateShard / setupRenderState /
    //    clearRenderState — The old RenderType compositing system was replaced in
    //    1.21.5 by RenderPipeline. Rather than constructing a custom RenderType
    //    with CompositeState we now obtain a built-in pipeline from RenderPipelines
    //    and invoke it directly via a RenderPass.
    //
    // 2. RenderSystem.enableBlend / blendFunc / disableBlend / defaultBlendFunc /
    //    enableDepthTest / disableDepthTest — These direct OpenGL state-machine
    //    calls were removed in 1.21.5. Blend and depth state is now specified
    //    declaratively in the RenderPipeline definition. We use
    //    RenderPipelines.POSITION_COLOR_NO_DEPTH (or equivalent) which already     <-- Claude halucination
    //    has blending enabled and depth testing disabled, matching the original
    //    intent of the SRC_ALPHA / ONE blend that was used for the overlay.
    //    NOTE: If RenderPipelines does not expose an exact match in your build,
    //    define a custom RenderPipeline via RenderPipeline.builder(...) as shown
    //    in the 1.21.5 migration primer.
    //
    // 3. RenderLevelStageEvent — The event was overhauled in 1.21.5:
    //    • getStage() / Stage enum gone — replace with sub-event class matching:
    //        Stage.AFTER_PARTICLES -> RenderLevelStageEvent.AfterParticles         <-- Claude halucination
    //    • getCamera() gone — use Minecraft.getInstance().gameRenderer.getMainCamera()
    //    • getProjectionMatrix() gone — projMat is no longer needed for VertexBuffer.drawWithShader();
    //      see point 4 below.
    //    • getRenderTick() gone — use Minecraft.getInstance().levelRenderer.ticks
    //      (or track time yourself; see VBOCache below).
    //
    // 4. VertexBuffer — Renamed/replaced by GpuBuffer in 1.21.5.
    //    The old VertexBuffer.bind() / upload(MeshData) / drawWithShader(...) /
    //    unbind() / close() API is gone. Equivalent new API:
    //      • Creation:  RenderSystem.getDevice().createBuffer(usage, byteSize)
    //                   or VertexFormat.uploadImmediateVertexBuffer(buffer)
    //      • Upload:    GpuBuffer.write(ByteBuffer, offset)
    //      • Draw:      Use a RenderPass opened on a framebuffer, set the pipeline,
    //                   call renderPass.setVertexBuffer(0, gpuBuffer), then draw.
    //      • Lifecycle: GpuBuffer implements AutoCloseable.
    //    Because the GpuBuffer draw path requires a live RenderPass (which in turn
    //    needs a Framebuffer target), VBOs that want to render into the world must
    //    use the main render target: Minecraft.getInstance().getMainRenderTarget().
    //
    // 5. Camera.getPosition() → Camera.position() in 1.21.2.
    //
    // 6. GlStateManager.SourceFactor / DestFactor — gone; blend state is now
    //    expressed through RenderPipeline / BlendFunction objects.
    // -------------------------------------------------------------------------

    private static final int BUFFER_SIZE = 256;

    // We no longer use a custom RenderType with CompositeState. Instead we rely
    // on a built-in pipeline that has blending on, depth-write off, and no depth
    // test — which is the semantic of the original "sfm_overlay" render type.
    // RenderPipelines.POSITION_COLOR_NO_DEPTH is a good fit; if it does not exist
    // in your exact build, replace with an appropriate pipeline constant or
    // construct one with RenderPipeline.builder().
    private static final RenderPipeline OVERLAY_PIPELINE = RenderPipelines.TRANSLUCENT_BLOCK;

    // The vertex format and mode must stay consistent with the BufferBuilder calls below.
    private static final VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_COLOR;
    private static final VertexFormat.Mode VERTEX_MODE = VertexFormat.Mode.QUADS;

    private static final int capabilityColor = ARGB.color(100, 100, 0, 255);
    private static final int capabilityColorLimitedView = ARGB.color(100, 0, 100, 255);
    private static final int cableColor = ARGB.color(100, 100, 255, 0);
    private static final int noNetworkErrorColor = ARGB.color(200, 255, 50, 50);
    private static final VBOCache vboCache = new VBOCache();

    // We subscribe only on the sub-event class instead of checking getStage():
    // RenderLevelStageEvent.AfterTranslucentParticles replaces Stage.AFTER_PARTICLES.
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void renderOverlays(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        PoseStack poseStack = event.getPoseStack();
        // getCamera() was removed from the event; obtain it from gameRenderer instead.
        Camera camera = minecraft.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

/*        ItemStack held;
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
        }*/
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
            Camera ignoredCamera,
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
            PoseStack poseStack,
            BlockPosSet positions,
            int color,
            RenderLevelStageEvent event
    ) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        GpuBuffer gpuBuffer = vboCache.getVBO(
                vboKind,
                positions,
                ARGB.red(color),
                ARGB.green(color),
                ARGB.blue(color),
                ARGB.alpha(color)
        );
        if (gpuBuffer == null) return;

        poseStack.pushPose();
        // Invert camera rotation so geometry is expressed in world space
        poseStack.mulPose(camera.rotation().invert(new Quaternionf()));
        // Camera.getPosition() → Camera.position() since 1.21.2
        poseStack.translate(-camera.position().x, -camera.position().y, -camera.position().z);

        // Build index buffer for QUADS via the shared sequential buffer
        int vertexCount = positions.size() * 6 /*faces*/ * 4 /*vertices per face*/;
        // Clamp to avoid 0-draw if positions are empty (already guarded above, but be safe)
        if (vertexCount <= 0) {
            poseStack.popPose();
            return;
        }
        RenderSystem.AutoStorageIndexBuffer indexBuffer =
                RenderSystem.getSequentialBuffer(VERTEX_MODE);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "no idea",
                        Minecraft.getInstance().getMainRenderTarget().getColorTextureView(),
                        OptionalInt.empty(),
                        Minecraft.getInstance().getMainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            renderPass.setPipeline(OVERLAY_PIPELINE);
            renderPass.setVertexBuffer(0, gpuBuffer);
            renderPass.setIndexBuffer(indexBuffer.getBuffer(vertexCount), indexBuffer.type());
            // modelViewMatrix from the poseStack
            // (In 1.21.5+ uniforms are bound via the pipeline defaults for ModelViewMat/ProjMat)
            renderPass.drawIndexed(0, vertexCount, vertexCount / 4 * 6, 1);
        }

        poseStack.popPose();
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
     * Caches GPU buffers (formerly VertexBuffers) per VBOKind.
     * In 1.21.5, VertexBuffer was replaced by GpuBuffer
     * (com.mojang.blaze3d.buffers.GpuBuffer). The new API:
     *   - Upload:  GpuBuffer.write(ByteBuffer, offset) or
     *              VertexFormat#uploadImmediateVertexBuffer(BuiltBuffer)
     *   - Draw:    Via RenderPass (see drawVbo above)
     *   - Lifecycle: AutoCloseable (use try-with-resources or explicit close())
     * getRenderTick() is gone from the event; we track change detection via a
     * simple frame counter instead (or you can use Minecraft.getInstance().levelRenderer.ticks
     * if that field is accessible in your mappings).
     */
    private static class VBOCache {
        private final EnumMap<VBOKind, VBOEntry> cache = new EnumMap<>(VBOKind.class);
        // Track ticks for change-check throttling using the level renderer ticks field.
        // If you cannot access levelRenderer.ticks directly, replace with a frame counter.
        private int lastChangeCheck = -1;

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

            // Throttle expensive equality checks to once per tick
            int currentTick = Minecraft.getInstance().levelRenderer.getTicks();
            if (entry != null
                    && currentTick != lastChangeCheck
                    && !entry.positions.equals(positions)) {
                lastChangeCheck = currentTick;
                shouldRebuild = true;
            }

            if (shouldRebuild) {
                if (entry != null) {
                    // GpuBuffer implements AutoCloseable
                    entry.gpuBuffer.close();
                }
                GpuBuffer gpuBuffer = createGpuBuffer(positions, r, g, b, a);
                entry = new VBOEntry(new BlockPosSet(positions), gpuBuffer);
                cache.put(kind, entry);
            }

            return entry.gpuBuffer;
        }

        public void clear() {
            for (VBOEntry entry : cache.values()) {
                entry.gpuBuffer.close();
            }
            cache.clear();
        }

        @HelpsWithMinecraftVersionIndependence
        private BufferBuilder createBufferBuilder(int numPositions) {
            // In 1.21.1 this used Tesselator.getInstance().begin(...)
            // That API is unchanged here; Tesselator is still present in 26.1.
            return Tesselator.getInstance().begin(VERTEX_MODE, VERTEX_FORMAT);
        }

        private GpuBuffer createGpuBuffer(
                BlockPosSet positions,
                int r,
                int g,
                int b,
                int a
        ) {
            PoseStack poseStack = new PoseStack();
            BufferBuilder bufferBuilder = createBufferBuilder(positions.size());

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

            // VertexBuffer is gone in 1.21.5.
            // We use VertexFormat#uploadImmediateVertexBuffer to push the mesh
            // data into a new GpuBuffer owned by us.
            // Note: uploadImmediateVertexBuffer returns a *transient* buffer
            // managed by the format. For a persistent cached buffer you should
            // instead use RenderSystem.getDevice().createBuffer(...) with
            // DYNAMIC_WRITE usage and call GpuBuffer.write(meshData.vertexBuffer(), 0).
            // The approach below is correct for smaller frequently-rebuilt meshes.
            GpuBuffer gpuBuffer = VERTEX_FORMAT.uploadImmediateVertexBuffer(meshData.vertexBuffer());
            meshData.close();
            return gpuBuffer;
        }

        private record VBOEntry(
                BlockPosSet positions,
                GpuBuffer gpuBuffer
        ) {}
    }
}