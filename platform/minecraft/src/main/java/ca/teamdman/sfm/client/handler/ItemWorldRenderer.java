package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.render.HighlightRenderList;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.HelpsWithMinecraftVersionIndependence;
import com.bbscn.Tools;
import com.google.common.collect.HashMultimap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Side.CLIENT)
/*
 * This class uses code from tasgon's "observable" mod, also using MPLv2
 * https://github.com/tasgon/observable/blob/master/common/src/main/kotlin/observable/client/Overlay.kt
 * https://github.com/tasgon/observable/blob/c3c5a0d0385e0b2c758729bdd935f103122f0f85/common/src/main/kotlin/observable/client/Overlay.kt
 */
public class ItemWorldRenderer {

    private static final int capabilityColor = Tools.toARGB(64, 100, 0, 255);
    private static final int capabilityColorLimitedView = Tools.toARGB(64, 100, 255, 255);
    private static final int cableColor = Tools.toARGB(64, 100, 255, 0);
    private static final int noNetworkErrorColor = Tools.toARGB(200, 255, 50, 50);
    private static final HighlightRenderListCache renderCache = new HighlightRenderListCache();

    @SubscribeEvent
    public static void renderOverlays(RenderWorldLastEvent event) {

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;


        ItemStack held;
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
            renderCache.clear();
        }
    }

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
        BlockPosSet cablePositions = NetworkToolItem.getCablePositions(networkTool);
        BlockPosSet capabilityPositions = NetworkToolItem.getCapabilityProviderPositions(networkTool);

        var selectedPos = NetworkToolItem.getSelectedNetworkBlockPos(networkTool);
        if (cablePositions.isEmpty() && selectedPos != null) {
            drawHighlights(
                    VBOKind.NETWORK_TOOL_CABLES,
                    Stream.of(selectedPos).collect(BlockPosSet.collector()),
                    noNetworkErrorColor,
                    player,
                    1
            );
        } else {
            drawHighlights(VBOKind.NETWORK_TOOL_CABLES, cablePositions, cableColor, player, 1);
            drawHighlights(VBOKind.NETWORK_TOOL_CAPABILITIES, capabilityPositions, capabilityColor, player, 0.9F);
        }
    }


    private static void drawHighlights(
                                       VBOKind vboKind,
                                       BlockPosSet positions,
                                       int color,
                                       EntityPlayerSP player,
                                       float highlightFraction
    ) {
        var colorRGB = new Color(color, true);

        HighlightRenderList list = renderCache.getList(
                vboKind,
                positions,
                player,
                colorRGB.getRed(),
                colorRGB.getGreen(),
                colorRGB.getBlue(),
                colorRGB.getAlpha()
        ,
                highlightFraction);

        if (list != null) {
            var renderManager = Minecraft.getMinecraft().getRenderManager();
            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    -renderManager.viewerPosX,
                    -renderManager.viewerPosY,
                    -renderManager.viewerPosZ
            );
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableCull();
            GlStateManager.disableDepth();


            list.render();

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


        drawHighlights(
                VBOKind.LABEL_GUN_CAPABILITIES,
                labelsByPosition.keySet().stream().collect(BlockPosSet.collector()),
                viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor,
                player,
                0.9F
        );

        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        var renderManager = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.translate(
                -renderManager.viewerPosX,
                -renderManager.viewerPosY,
                -renderManager.viewerPosZ
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
            BlockPos pos,
            float x,
            float y,
            float z,
            int r,
            int g,
            int b,
            int a
    ) {
//        Vector4f vec = org.lwjgl.util.vector.Matrix4f.transform(matrix4f, new Vector4f(x, y, z, 1.0F), null);
        builder.pos(pos.getX(), pos.getY(), pos.getZ()).color(r, g, b, a).endVertex();
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
            BlockPos matrix4f,
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


    private static class HighlightRenderListCache {
        private final EnumMap<VBOKind, HighlightRenderList> cache = new EnumMap<>(VBOKind.class);
        private int lastCachedTick = -1;

        public @Nullable HighlightRenderList getList(
                VBOKind kind,
                BlockPosSet positions,
                EntityPlayerSP player,
                int r,
                int g,
                int b,
                int a
        ,
                                                     float highlightFraction) {
            if (positions.isEmpty()) {
                return null;
            }
            @Nullable HighlightRenderList entry = cache.get(kind);

            boolean shouldRebuild = entry == null;

            if (entry != null
                    && player.ticksExisted != lastCachedTick
                    && !entry.positions.equals(positions)) {
                lastCachedTick = player.ticksExisted;
                shouldRebuild = true;
            }

            if (shouldRebuild) {
                // Dispose of the old VBO if it exists
                if (entry != null) {
                    entry.destroy();
                }

                entry = new HighlightRenderList(new BlockPosSet(positions), r, g, b, a, highlightFraction);
                cache.put(kind, entry);
            }
            return entry;
        }

        public void clear() {
            // Dispose of all cached VBOs
            for (var entry : cache.values()) {
                entry.destroy();
            }
            cache.clear();
        }
    }


}
