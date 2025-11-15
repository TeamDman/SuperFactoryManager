package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.HelpsWithMinecraftVersionIndependence;
import ca.teamdman.sfm.common.util.SFMDirections;
import com.bbscn.Tools;
import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.HashMultimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.ReadableVector4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Callable;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 * https://github.com/tasgon/observable/blob/c3c5a0d0385e0b2c758729bdd935f103122f0f85/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {
    private static final int BUFFER_SIZE = 256;
    @SuppressWarnings("deprecation")
//    private static final RenderType RENDER_TYPE = RenderType.create(
//            "sfm_overlay",
//            DefaultVertexFormat.POSITION_COLOR,
//            VertexFormat.Mode.QUADS,
//            BUFFER_SIZE,
//            false,
//            false,
//            RenderType.CompositeState
//                    .builder()
//                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
//                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
//                    .setTransparencyState(
//                            new RenderStateShard.TransparencyStateShard(
//                                    "src_to_one",
//                                    () -> {
//                                        RenderSystem.enableBlend();
//                                        RenderSystem.blendFunc(
//                                                GlStateManager.SourceFactor.SRC_ALPHA,
//                                                GlStateManager.DestFactor.ONE
//                                        );
//                                    },
//                                    () -> {
//                                        RenderSystem.disableBlend();
//                                        RenderSystem.defaultBlendFunc();
//                                    }
//                            )
//                    )
//                    .createCompositeState(true)
//    );

//    private static final int capabilityColor = FastColor.ARGB32.color(100, 100, 0, 255);
//    private static final int capabilityColorLimitedView = FastColor.ARGB32.color(100, 0, 100, 255);
//    private static final int cableColor = FastColor.ARGB32.color(100, 100, 255, 0);
    private static final int capabilityColor = Tools.toARGB(40, 135, 206, 235);
    private static final int cableColor = Tools.toARGB(40, 135, 206, 235);
    private static final int capabilityColorLimitedView = Tools.toARGB(40, 100, 0, 100);
    private static final VBOCache vboCache = new VBOCache();

    @SubscribeEvent
    public static void renderOverlays(RenderWorldLastEvent event) {

        var partialTicks = event.getPartialTicks();

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;


        ItemStack held = getHeldItemOfType(player, LabelGunItem.class);
        boolean rendered = false;

        if ((held = getHeldItemOfType(player, NetworkToolItem.class)) != null) {
            handleNetworkTool(player, held, event.getPartialTicks());
            rendered = true;
        }
        if ((held = getHeldItemOfType(player, LabelGunItem.class)) != null) {
            handleLabelGun(player, held, event.getPartialTicks());
            rendered = true;
        }
        if (!rendered) {
            vboCache.clear();
        }
    }

    // Thanks @tigres810
    // https://discord.com/channels/313125603924639766/983834532904042537/1009267533527928864
//    public static @Nullable BlockPos lookingAt() {
//        HitResult rt = Minecraft.getInstance().hitResult;
//        if (rt == null) return null;
//
//        double x = (rt.getLocation().x);
//        double y = (rt.getLocation().y);
//        double z = (rt.getLocation().z);
//
//        LocalPlayer player = Minecraft.getInstance().player;
//        assert player != null;
//        Vec3 lookAngle = player.getLookAngle();
//        double xla = lookAngle.x;
//        double yla = lookAngle.y;
//        double zla = lookAngle.z;
//
//        if ((x % 1 == 0) && (xla < 0)) x -= 0.01;
//        if ((y % 1 == 0) && (yla < 0)) y -= 0.01;
//        if ((z % 1 == 0) && (zla < 0)) z -= 0.01;
//
//        // @MCVersionDependentBehaviour, the double constructor doesn't exist in 1.19.4
//        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
//    }

    private static BlockPos lookingAt() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null) {
            return mc.objectMouseOver.getBlockPos();
        }
        return null;
    }

    private static @Nullable ItemStack getHeldItemOfType(
            EntityPlayerSP player,
            Class<?> itemClass
    ) {
        ItemStack mainHandItem = player.getHeldItemMainhand();
        if (itemClass.isInstance(mainHandItem.getItem())) {
            return mainHandItem;
        }

        ItemStack offhandItem = player.getHeldItemOffhand();
        if (itemClass.isInstance(offhandItem.getItem())) {
            return offhandItem;
        }

        return null;
    }

    private static void handleNetworkTool(
            EntityPlayerSP player,
            ItemStack networkTool,
            float partialTicks
    ) {
        if (!NetworkToolItem.getOverlayEnabled(networkTool)) return;
        Set<BlockPos> cablePositions = NetworkToolItem.getCablePositions(networkTool);
        Set<BlockPos> capabilityPositions = NetworkToolItem.getCapabilityProviderPositions(networkTool);

        withGlContext(() -> {
            drawVbo(VBOKind.NETWORK_TOOL_CABLES, cablePositions, cableColor, player, partialTicks);
            drawVbo(VBOKind.NETWORK_TOOL_CAPABILITIES, capabilityPositions, capabilityColor, player, partialTicks);
        });

    }


    private static void drawVbo(
            VBOKind vboKind,
            Set<BlockPos> positions,
            int color,
            EntityPlayerSP player,
            float partialTicks
    ) {
        var colorRGB = Tools.intToColor(color);
        VertexBuffer vbo = vboCache.getVBO(
                vboKind,
                positions,
                player,
                colorRGB.getRed(),
                colorRGB.getGreen(),
                colorRGB.getBlue(),
                colorRGB.getAlpha()
        );
        if (vbo != null) {
            var renderManager = Minecraft.getMinecraft().getRenderManager();
            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    renderManager.viewerPosX,
                    renderManager.viewerPosY,
                    renderManager.viewerPosZ
            );


            vbo.bindBuffer();
            vbo.drawArrays(GL11.GL_QUADS);
            vbo.unbindBuffer();

            GlStateManager.popMatrix();
        }
    }

    private static void withGlContext(Runnable runnable) {

        try {
            GlStateManager.pushMatrix();
            GlStateManager.disableCull();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            runnable.run();
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    private static void handleLabelGun(EntityPlayerSP player, ItemStack labelGun, float partialTicks) {
        LabelGunItem.LabelGunViewMode viewMode = LabelGunItem.getViewMode(labelGun);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(labelGun);

        HashMultimap<BlockPos, String> labelsByPosition = HashMultimap.create();
        String activeLabel = LabelGunItem.getActiveLabel(labelGun);
        BlockPos lookingAtPos = lookingAt();

        switch (viewMode) {
            case SHOW_ALL -> //noinspection RedundantLabeledSwitchRuleCodeBlock
            {
                // Just add all labels
                labelPositionHolder.forEach((label, pos) -> labelsByPosition.put(pos, label));
            }
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK -> {
                // 1) Show the active label for all positions
                if (!activeLabel.isEmpty()) {
                    labelPositionHolder.forEach((label, pos) -> {
                        if (label.equals(activeLabel)) {
                            labelsByPosition.put(pos, label);
                        }
                    });
                }
                // 2) Also show *any* labels for the block the player is looking at
                if (lookingAtPos != null) {
                    labelsByPosition.putAll(lookingAtPos, labelPositionHolder.getLabels(lookingAtPos));
                }
            }
            case SHOW_ONLY_TARGETED_BLOCK -> {
                if (lookingAtPos != null) {
                    labelsByPosition.putAll(lookingAtPos, labelPositionHolder.getLabels(lookingAtPos));
                }
                break;
            }
        }


        withGlContext(() -> {
            drawVbo(VBOKind.LABEL_GUN_CAPABILITIES, labelsByPosition.keySet(), viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor, player, partialTicks);
        });

        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        var renderManager = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.translate(
                renderManager.viewerPosX,
                renderManager.viewerPosY,
                renderManager.viewerPosZ
        );
        for (Map.Entry<BlockPos, Collection<String>> entry : labelsByPosition.asMap().entrySet()) {
            drawLabel(entry.getKey(), entry.getValue(), player);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }


    private static void drawLabel(BlockPos pos, Collection<String> labels, EntityPlayer player) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (String label : labels) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-player.rotationYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(player.rotationPitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.025F, -0.025F, 0.025F);
            font.drawString(label, (int) (-font.getStringWidth(label) / 2f), 0, 0xFFFFFF);
            GlStateManager.popMatrix();
            y += 0.25;
        }
    }


    private enum VBOKind {
        LABEL_GUN_CAPABILITIES,
        NETWORK_TOOL_CAPABILITIES,
        NETWORK_TOOL_CABLES
    }


    @HelpsWithMinecraftVersionIndependence
    private static void writeVertex(
            BufferBuilder builder,
            Matrix4f matrix4f,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b,
            int a
    ) {
        Vector4f vec = org.lwjgl.util.vector.Matrix4f.transform(matrix4f, new Vector4f(x, y, z, 1.0F), null);
        builder.pos(vec.getX(), vec.getY(), vec.getZ()).color(r, g, b, a).endVertex();
//        for (int e = 0; e < builder.getVertexFormat().getElementCount(); e++) {
//            switch (builder.getVertexFormat().getElement(e).getUsage()) {
//                case POSITION:
//                    builder.put(e, vec.getX(), vec.getY(), vec.getZ(), 1f);
//                    break;
//                case COLOR:
//                    builder.put(e, r, g, b, a);
//                    break;
//                default:
//                    builder.put(e);
//                    break;
//            }
//        }
    }

    private static void writeFaceVertices(
            BufferBuilder builder,
            Matrix4f matrix4f,
            EnumFacing direction,
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


    private static class VBOCache {
        private final EnumMap<VBOKind, VBOEntry> cache = new EnumMap<>(VBOKind.class);
        private int lastCachedTick = -1;


        public @Nullable VertexBuffer getVBO(
                VBOKind kind,
                Set<BlockPos> positions,
                EntityPlayerSP player,
                int r,
                int g,
                int b,
                int a
        ) {
            if (positions.isEmpty()) {
                return null;
            }
            @Nullable VBOEntry entry = cache.get(kind);

            boolean shouldRebuild = entry == null;

            // only compare the entries every second since it's mildly expensive
            if (entry != null
                    && player.ticksExisted != lastCachedTick
                    && !entry.positions.equals(positions)) {
                lastCachedTick = player.ticksExisted;
                shouldRebuild = true;
            }

            if (shouldRebuild) {
                // Dispose of the old VBO if it exists
                if (entry != null) {
                    entry.vbo.deleteGlBuffers();
                }

                // Create a new VBO
                VertexBuffer vbo = createVBO(positions, r, g, b, a);

                // Cache the new VBO
                entry = new VBOEntry(new HashSet<>(positions), vbo);
                cache.put(kind, entry);
            }

            return entry.vbo;
        }

        public void clear() {
            // Dispose of all cached VBOs
            for (VBOEntry entry : cache.values()) {
                entry.vbo.deleteGlBuffers();
            }
            cache.clear();
        }

        @HelpsWithMinecraftVersionIndependence
        private BufferBuilder createBufferBuilder(int numPositions) {
            BufferBuilder bufferBuilder = new BufferBuilder(BUFFER_SIZE * numPositions);
            bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            return bufferBuilder;
        }

        private VertexBuffer createVBO(
                Set<BlockPos> positions,
                int r,
                int g,
                int b,
                int a
        ) {
            BufferBuilder bufferBuilder = createBufferBuilder(positions.size());

            // Push vertices
            for (BlockPos blockPos : positions) {
                Matrix4f matrix4f = new Matrix4f(new float[]{blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1});
                for (EnumFacing face : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                    if (!positions.contains(blockPos.offset(face))) {
                        writeFaceVertices(bufferBuilder, matrix4f, face, r, g, b, a);
                    }
                }
            }

            ByteBuffer meshData = bufferBuilder.getByteBuffer();
            VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
            vbo.bindBuffer();
            vbo.bufferData(meshData);
            vbo.unbindBuffer();

            return vbo;
        }


        @Desugar
        private record VBOEntry(
                Set<BlockPos> positions,
                VertexBuffer vbo
        ) {
        }
    }
}
