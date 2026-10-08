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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import com.mojang.blaze3d.platform.GlStateManager;
{% when '26.1.2' %}
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
{% endcase %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.math.Matrix4f;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import com.mojang.math.Axis;
{% endcase %}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.renderer.GameRenderer;
{% when '26.1.2' %}
import net.minecraft.client.renderer.MappableRingBuffer;
{% endcase %}
import net.minecraft.client.renderer.MultiBufferSource;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.util.FastColor;
{% when '26.1.2' %}
import net.minecraft.util.ARGB;
{% endcase %}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.RenderLevelStageEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
{% endcase %}
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import org.joml.Matrix4f;
{% when '1.21', '1.21.1' %}
import org.joml.Matrix4f;
import org.joml.Quaternionf;
{% when '26.1.2' %}
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
{% endcase %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
{% when '26.1.2' %}
import java.util.*;
import static ca.teamdman.sfm.client.handler.NetworkPipeline.NETWORK_PIPELINE;
{% endcase %}

/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
 * https://github.com/tasgon/observable/blob/c3c5a0d0385e0b2c758729bdd935f103122f0f85/common/src/main/kotlin/observable/client/Overlay.kt
{% when '26.1.2' %}
{% endcase %}
 */
public class ItemWorldRenderer {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static final int BUFFER_SIZE = 256;
    @SuppressWarnings("deprecation")
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
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static final int capabilityColor = FastColor.ARGB32.color(100, 100, 0, 255);
    private static final int capabilityColorLimitedView = FastColor.ARGB32.color(100, 0, 100, 255);
    private static final int cableColor = FastColor.ARGB32.color(100, 100, 255, 0);
    private static final int noNetworkErrorColor = FastColor.ARGB32.color(200, 255, 50, 50);
{% when '26.1.2' %}
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
{% endcase %}
    private static final VBOCache vboCache = new VBOCache();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    // ByteBufferBuilder allocator shared for building mesh data each frame.
    // RenderType.SMALL_BUFFER_SIZE is a convenient constant (~256 KB).
    private static final ByteBufferBuilder MESH_ALLOCATOR =
            new ByteBufferBuilder(net.minecraft.client.renderer.rendertype.RenderType.SMALL_BUFFER_SIZE);

    // Subscribe to the typed sub-event class instead of checking getStage().
    // AfterTranslucentParticles replaces the old Stage.AFTER_PARTICLES.
{% endcase %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static void renderOverlays(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
{% when '26.1.2' %}
    public static void renderOverlays(RenderLevelStageEvent.AfterTranslucentParticles event) {
{% endcase %}
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

        // PoseStack is now obtained directly from the event.
{% endcase %}
        PoseStack poseStack = event.getPoseStack();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        // Camera is no longer on the event; fetch from gameRenderer.
{% endcase %}
        Camera camera = minecraft.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        ItemStack held;
        boolean rendered = false;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Can render both if in main hand and off-hand
{% when '26.1.2' %}
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    // https://discord.com/channels/313125603924639766/983834532904042537/1009267533527928864
{% when '26.1.2' %}
{% endcase %}
    public static @Nullable BlockPos lookingAt() {
        HitResult rt = Minecraft.getInstance().hitResult;
        if (rt == null) return null;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        double x = (rt.getLocation().x);
        double y = (rt.getLocation().y);
        double z = (rt.getLocation().z);
{% when '26.1.2' %}
        double x = rt.getLocation().x;
        double y = rt.getLocation().y;
        double z = rt.getLocation().z;
{% endcase %}

        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        Vec3 lookAngle = player.getLookAngle();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        double xla = lookAngle.x;
        double yla = lookAngle.y;
        double zla = lookAngle.z;
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if ((x % 1 == 0) && (xla < 0)) x -= 0.01;
        if ((y % 1 == 0) && (yla < 0)) y -= 0.01;
        if ((z % 1 == 0) && (zla < 0)) z -= 0.01;
{% when '26.1.2' %}
        if ((x % 1 == 0) && (lookAngle.x < 0)) x -= 0.01;
        if ((y % 1 == 0) && (lookAngle.y < 0)) y -= 0.01;
        if ((z % 1 == 0) && (lookAngle.z < 0)) z -= 0.01;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // @MCVersionDependentBehaviour, the double constructor doesn't exist in 1.19.4
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
{% when '26.1.2' %}
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
{% endcase %}
    }

    private static @Nullable ItemStack getHeldItemOfType(
            LocalPlayer player,
            Class<?> itemClass
    ) {
        ItemStack mainHandItem = player.getMainHandItem();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (itemClass.isInstance(mainHandItem.getItem())) {
            return mainHandItem;
        }

{% when '26.1.2' %}
        if (itemClass.isInstance(mainHandItem.getItem())) return mainHandItem;
{% endcase %}
        ItemStack offhandItem = player.getOffhandItem();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (itemClass.isInstance(offhandItem.getItem())) {
            return offhandItem;
        }

        return null; // Neither hand holds the item
{% when '26.1.2' %}
        if (itemClass.isInstance(offhandItem.getItem())) return offhandItem;
        return null;
{% endcase %}
    }

    private static void handleLabelGun(
            RenderLevelStageEvent event,
            PoseStack poseStack,
            Camera camera,
            MultiBufferSource.BufferSource bufferSource,
            ItemStack labelGun
    ) {
        LabelGunItem.LabelGunViewMode viewMode = LabelGunItem.getViewMode(labelGun);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        // Gather all label -> positions from the gun:
{% when '26.1.2' %}
{% endcase %}
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(labelGun);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        // We'll build up a map of pos -> labels that we want to render
        // depending on the chosen mode.
{% when '26.1.2' %}
{% endcase %}
        HashMultimap<BlockPos, String> labelsByPosition = HashMultimap.create();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        // Some "helper" variables:
{% when '26.1.2' %}
{% endcase %}
        String activeLabel = LabelGunItem.getActiveLabel(labelGun);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        BlockPos lookingAtPos = ItemWorldRenderer.lookingAt();  // null if none
{% when '26.1.2' %}
        BlockPos lookingAtPos = ItemWorldRenderer.lookingAt();
{% endcase %}

        switch (viewMode) {
            case SHOW_ALL -> //noinspection RedundantLabeledSwitchRuleCodeBlock
            {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // Just add all labels
{% when '26.1.2' %}
{% endcase %}
                labelPositionHolder.forEach((label, pos) -> labelsByPosition.put(pos, label));
            }
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK -> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // 1) Show the active label for all positions
{% when '26.1.2' %}
{% endcase %}
                if (!activeLabel.isEmpty()) {
                    labelPositionHolder.forEach((label, pos) -> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        if (label.equals(activeLabel)) {
                            labelsByPosition.put(pos, label);
                        }
{% when '26.1.2' %}
                        if (label.equals(activeLabel)) labelsByPosition.put(pos, label);
{% endcase %}
                    });
                }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // 2) Also show *any* labels for the block the player is looking at
{% when '26.1.2' %}
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RenderSystem.disableDepthTest();


{% when '26.1.2' %}
{% endcase %}
        // Draw labels
        poseStack.pushPose();
        for (Map.Entry<BlockPos, Collection<String>> entry : labelsByPosition.asMap().entrySet()) {
            BlockPos pos = entry.getKey();
            Collection<String> labels = entry.getValue();
            drawLabelsForPos(poseStack, camera, pos, bufferSource, labels);
        }
        poseStack.popPose();

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Draw boxes
        RENDER_TYPE.setupRenderState();
{% when '26.1.2' %}
        // Draw boxes — blend/depth managed by OVERLAY_PIPELINE inside drawVbo.
{% endcase %}
        BlockPosSet labelledPositions = new BlockPosSet(labelsByPosition.keySet());
        drawVbo(
                VBOKind.LABEL_GUN_CAPABILITIES,
                poseStack,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                camera,
{% endcase %}
                labelledPositions,
                viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor,
                event
        );
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RENDER_TYPE.clearRenderState();
{% when '26.1.2' %}
{% endcase %}

        bufferSource.endBatch();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RenderSystem.enableDepthTest();
{% when '26.1.2' %}
{% endcase %}
    }

    private static void handleNetworkTool(
            RenderLevelStageEvent event,
            PoseStack poseStack,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            Camera ignoredCamera,
{% when '26.1.2' %}
            Camera camera,
{% endcase %}
            MultiBufferSource.BufferSource bufferSource,
            ItemStack networkTool
    ) {
        if (!NetworkToolItem.getOverlayEnabled(networkTool)) return;
        BlockPosSet cablePositions = NetworkToolItem.getCablePositions(networkTool);
        BlockPosSet capabilityPositions = NetworkToolItem.getCapabilityProviderPositions(networkTool);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RenderSystem.disableDepthTest();

        RENDER_TYPE.setupRenderState();

{% when '26.1.2' %}
{% endcase %}
        var selectedPos = NetworkToolItem.getSelectedNetworkBlockPos(networkTool);
        if (cablePositions.isEmpty() && selectedPos != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            drawVbo(
                    VBOKind.NETWORK_TOOL_CABLES,
                    poseStack,
                    BlockPosSet.of(selectedPos),
                    noNetworkErrorColor,
                    event
            );
{% when '26.1.2' %}
            drawVbo(
                    VBOKind.NETWORK_TOOL_CABLES,
                    poseStack,
                    camera,
                    BlockPosSet.of(selectedPos),
                    noNetworkErrorColor,
                    event
            );
{% endcase %}
        } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, cablePositions, cableColor, event);
            drawVbo(VBOKind.NETWORK_TOOL_CAPABILITIES, poseStack, capabilityPositions, capabilityColor, event);
{% when '26.1.2' %}
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, poseStack, camera, cablePositions, cableColor, event);
            drawVbo(VBOKind.NETWORK_TOOL_CAPABILITIES, poseStack, camera, capabilityPositions, capabilityColor, event);
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RENDER_TYPE.clearRenderState();

{% when '26.1.2' %}
{% endcase %}

        bufferSource.endBatch();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        RenderSystem.enableDepthTest();
{% when '26.1.2' %}
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
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
{% endcase %}
    private static void drawVbo(
            VBOKind vboKind,
            PoseStack poseStack,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
            Camera camera,
{% endcase %}
            BlockPosSet positions,
            int color,
            RenderLevelStageEvent event
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
        if (positions.isEmpty()) return;

//        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

{% endcase %}
        VBOCache.VBOEntry entry = vboCache.getVBO(
                vboKind,
                positions,
                event,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color),
                FastColor.ARGB32.alpha(color)
{% when '26.1.2' %}
                ARGB.red(color),
                ARGB.green(color),
                ARGB.blue(color),
                ARGB.alpha(color)
{% endcase %}
        );
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        if (entry != null) {
            poseStack.pushPose();
            // we need to pass in a new destination quaternion to avoid undesired camera mutation
//            poseStack.mulPose(event.getCamera().rotation().invert(new Quaternionf()));
            poseStack.translate(
                    entry.origin.getX() - event.getCamera().getPosition().x,
                    entry.origin.getY() - event.getCamera().getPosition().y,
                    entry.origin.getZ() - event.getCamera().getPosition().z
            );
{% when '1.21', '1.21.1' %}
        if (entry != null) {
            poseStack.pushPose();
            // we need to pass in a new destination quaternion to avoid undesired camera mutation
            poseStack.mulPose(event.getCamera().rotation().invert(new Quaternionf()));
            poseStack.translate(
                    entry.origin.getX() - event.getCamera().getPosition().x,
                    entry.origin.getY() - event.getCamera().getPosition().y,
                    entry.origin.getZ() - event.getCamera().getPosition().z
            );
{% when '26.1.2' %}
        if (entry == null) return;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            // Draw the VBO
            entry.vbo.bind();
            assert GameRenderer.getPositionColorShader() != null;
            entry.vbo.drawWithShader(
                    poseStack.last().pose(),
                    event.getProjectionMatrix(),
                    GameRenderer.getPositionColorShader()
            );
            VertexBuffer.unbind();
{% when '26.1.2' %}
        GpuBuffer gpuBuffer = entry.ringBuffer.currentBuffer();
        if (gpuBuffer == null) return;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            poseStack.popPose();
{% when '26.1.2' %}
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
                .translate(
                        (float) (entry.origin.getX() - camera.position().x),
                        (float) (entry.origin.getY() - camera.position().y),
                        (float) (entry.origin.getZ() - camera.position().z)
                );

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
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

//        poseStack.popPose();
{% endcase %}
    }

    private static void drawLabelsForPos(
            PoseStack poseStack,
            Camera camera,
            BlockPos pos,
            MultiBufferSource mbs,
            Collection<String> labels
    ) {
        poseStack.pushPose();
        poseStack.translate(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                pos.getX() + 0.5 - camera.getPosition().x,
                pos.getY() + 0.5 - camera.getPosition().y,
                pos.getZ() + 0.5 - camera.getPosition().z
{% when '26.1.2' %}
                pos.getX() + 0.5 - camera.position().x,
                pos.getY() + 0.5 - camera.position().y,
                pos.getZ() + 0.5 - camera.position().z
{% endcase %}
        );
        poseStack.mulPose(camera.rotation());
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
//        poseStack.mulPose(Axis.YP.rotationDegrees(180));
{% when '1.21', '1.21.1', '26.1.2' %}
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        builder.vertex(matrix4f, x, y, z).color(r, g, b, a).endVertex();
{% when '1.21', '1.21.1', '26.1.2' %}
        builder.addVertex(matrix4f, x, y, z).setColor(r, g, b, a);
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    // Enum to represent different kinds of VBOs
{% when '26.1.2' %}
{% endcase %}
    private enum VBOKind {
        LABEL_GUN_CAPABILITIES,
        NETWORK_TOOL_CAPABILITIES,
        NETWORK_TOOL_CABLES
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    // VBOCache class to handle caching of VBOs
{% when '26.1.2' %}
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
{% endcase %}
    private static class VBOCache {
        private final EnumMap<VBOKind, VBOEntry> cache = new EnumMap<>(VBOKind.class);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        private int lastChangeCheck = -1;
{% when '26.1.2' %}
        private int lastChangeCheckTick = -1;
{% endcase %}

        public @Nullable VBOEntry getVBO(
                VBOKind kind,
                BlockPosSet positions,
                RenderLevelStageEvent event,
                int r,
                int g,
                int b,
                int a
        ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            if (positions.isEmpty()) {
                return null;
            }
{% when '26.1.2' %}
            if (positions.isEmpty()) return null;

{% endcase %}
            @Nullable VBOEntry entry = cache.get(kind);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
            boolean shouldRebuild = (entry == null);
{% endcase %}
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            boolean shouldRebuild = entry == null;

            // only compare the entries every second since it's mildly expensive
{% when '26.1.2' %}
            // Throttle expensive equality checks to once per render tick.
            int currentTick = event.getLevelRenderer().getTicks();
{% endcase %}
            if (entry != null
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    && event.getRenderTick() != lastChangeCheck
                    && !entry.positions.equals(positions)) {
                lastChangeCheck = event.getRenderTick();
{% when '26.1.2' %}
                    && currentTick != lastChangeCheckTick
                    && !entry.positions.equals(positions)) {
                lastChangeCheckTick = currentTick;
{% endcase %}
                shouldRebuild = true;
            }

            if (shouldRebuild) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // Dispose of the old VBO if it exists
{% when '26.1.2' %}
{% endcase %}
                if (entry != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    entry.vbo.close();
{% when '26.1.2' %}
                    entry.ringBuffer.close();
{% endcase %}
                }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // Create a new VBO
{% when '26.1.2' %}
{% endcase %}
                BlockPos origin = getOrigin(positions);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                VertexBuffer vbo = createVBO(positions, origin, r, g, b, a);

                // Cache the new VBO
                entry = new VBOEntry(
                        new BlockPosSet(positions), // create immutable copy just in case
                        origin,
                        vbo
                );
{% when '26.1.2' %}
                MappableRingBuffer ringBuffer = createRingBuffer(positions, origin, r, g, b, a);
                entry = new VBOEntry(
                        new BlockPosSet(positions),
                        origin,
                        ringBuffer
                );
{% endcase %}
                cache.put(kind, entry);
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
            // Rotate so the next upload slot is ready while the GPU finishes the current one.
            // This causes flickering?
//            entry.ringBuffer.rotate();

{% endcase %}
            return entry;
        }

        public void clear() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            // Dispose of all cached VBOs
{% when '26.1.2' %}
{% endcase %}
            for (VBOEntry entry : cache.values()) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                entry.vbo.close();
{% when '26.1.2' %}
                entry.ringBuffer.close();
{% endcase %}
            }
            cache.clear();
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
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
{% endcase %}
        @HelpsWithMinecraftVersionIndependence
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        private BufferBuilder createBufferBuilder(int numPositions) {
            BufferBuilder bufferBuilder = new BufferBuilder(RENDER_TYPE.bufferSize() * numPositions);
            bufferBuilder.begin(RENDER_TYPE.mode(), RENDER_TYPE.format());
            return bufferBuilder;
        }

        private VertexBuffer createVBO(
{% when '1.21', '1.21.1' %}
        private BufferBuilder createBufferBuilder(int numPositions) {
//            BufferBuilder bufferBuilder = new BufferBuilder(RENDER_TYPE.bufferSize() * numPositions);
//            bufferBuilder.begin(RENDER_TYPE.mode(), RENDER_TYPE.format());
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(RENDER_TYPE.mode(), RENDER_TYPE.format());
            return bufferBuilder;
        }

        private VertexBuffer createVBO(
{% when '26.1.2' %}
        private MappableRingBuffer createRingBuffer(
{% endcase %}
                BlockPosSet positions,
                BlockPos origin,
                int r,
                int g,
                int b,
                int a
        ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            // Build the mesh data
{% when '26.1.2' %}
            // Build the mesh on the CPU.
{% endcase %}
            PoseStack poseStack = new PoseStack();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            // Do not undo camera transform; create vertices in world space
{% when '26.1.2' %}
            BufferBuilder bufferBuilder = new BufferBuilder(MESH_ALLOCATOR, VERTEX_MODE, VERTEX_FORMAT);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            BufferBuilder bufferBuilder = createBufferBuilder(positions.size());

            // Push vertices
{% when '26.1.2' %}
{% endcase %}
            for (BlockPos blockPos : positions.blockPosIterator()) {
                poseStack.pushPose();
                poseStack.translate(
                        blockPos.getX() - origin.getX(),
                        blockPos.getY() - origin.getY(),
                        blockPos.getZ() - origin.getZ()
                );
                Matrix4f matrix4f = poseStack.last().pose();
                for (Direction face : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    if (!positions.contains(blockPos.relative(face))) {
                        writeFaceVertices(bufferBuilder, matrix4f, face, r, g, b, a);
                    }
                }
                poseStack.popPose();
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
            BufferBuilder.RenderedBuffer meshData = bufferBuilder.end();
            VertexBuffer vbo = new VertexBuffer();
            vbo.bind();
            vbo.upload(meshData);
            VertexBuffer.unbind();
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            BufferBuilder.RenderedBuffer meshData = bufferBuilder.end();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(meshData);
            VertexBuffer.unbind();
{% when '1.21', '1.21.1' %}
            MeshData meshData = bufferBuilder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(meshData);
            VertexBuffer.unbind();
{% when '26.1.2' %}
            MeshData meshData = bufferBuilder.buildOrThrow();
            MeshData.DrawState drawState = meshData.drawState();
            MappableRingBuffer ringBuffer = getMappableRingBuffer(positions, drawState);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            return vbo;
{% when '26.1.2' %}
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
{% endcase %}
        }

        private BlockPos getOrigin(BlockPosSet positions) {
            var iterator = positions.blockPosIterator();
            if (!iterator.hasNext()) {
                return BlockPos.ZERO;
            }
            return iterator.next().immutable();
        }

        private record VBOEntry(
                BlockPosSet positions,
                BlockPos origin,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                VertexBuffer vbo
        ) {
        }
{% when '26.1.2' %}
                MappableRingBuffer ringBuffer
        ) {
        }
{% endcase %}
    }
}
