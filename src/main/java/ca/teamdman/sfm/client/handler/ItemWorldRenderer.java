package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.bbscn.Tools;
import com.google.common.collect.HashMultimap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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

import java.util.Collection;
import java.util.Map;

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

    @SubscribeEvent
    public static void renderOverlays(RenderWorldLastEvent event) {

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        ItemStack held = getHeldItemOfType(player, LabelGunItem.class);
        if (held != null) {
            handleLabelGun(player, held, event.getPartialTicks());
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

        return null; // Neither hand holds the item
    }
    private static void handleLabelGun(EntityPlayer player, ItemStack labelGun, float partialTicks) {
        LabelGunItem.LabelGunViewMode viewMode = LabelGunItem.getViewMode(labelGun);
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(labelGun);

        HashMultimap<BlockPos, String> labelsByPosition = HashMultimap.create();
        String activeLabel = LabelGunItem.getActiveLabel(labelGun);
        BlockPos lookingAtPos = lookingAt();

        switch (viewMode) {
            case SHOW_ALL:
                labelPositionHolder.forEach((s, blockPos) -> {
                    labelsByPosition.put(blockPos,s);
                });
                break;
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK:
                if (!activeLabel.isEmpty()) {
                    labelPositionHolder.forEach((label, pos) -> {
                        if (label.equals(activeLabel)) {
                            labelsByPosition.put(pos, label);
                        }
                    });
                }
                if (lookingAtPos != null) {
                    labelsByPosition.putAll(lookingAtPos, labelPositionHolder.getLabels(lookingAtPos));
                }
                break;
            case SHOW_ONLY_TARGETED_BLOCK:
                if (lookingAtPos != null) {
                    labelsByPosition.putAll(lookingAtPos, labelPositionHolder.getLabels(lookingAtPos));
                }
                break;
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableCull(); // 禁用面剔除
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth(); // 避免遮挡

        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX)*partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY)*partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ)*partialTicks;
        GlStateManager.translate(-dx, -dy, -dz);

        for (Map.Entry<BlockPos, Collection<String>> entry : labelsByPosition.asMap().entrySet()) {
            drawBox(entry.getKey(), viewMode != LabelGunItem.LabelGunViewMode.SHOW_ALL ? capabilityColorLimitedView : capabilityColor);
        }

        // 恢复状态以渲染字体
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (Map.Entry<BlockPos, Collection<String>> entry : labelsByPosition.asMap().entrySet()) {
            drawLabel(entry.getKey(), entry.getValue(), player);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableCull(); // 重新启用面剔除
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
    private static void drawBox(BlockPos pos, int color) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();

        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = (color >> 24 & 0xFF) / 255.0F;

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (EnumFacing face : EnumFacing.values()) {
            if (true) { // 可加邻接判断优化
                addFace(buffer, pos, face, r, g, b, a);
            }
        }
        tess.draw();
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
    private static void addFace(BufferBuilder buffer, BlockPos pos, EnumFacing face, float r, float g, float b, float a) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        switch (face) {
            case UP:
                buffer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
                break;
            case DOWN:
                buffer.pos(x, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
                break;
            case NORTH:
                buffer.pos(x, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
                buffer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
                break;
            case SOUTH:
                buffer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
                break;
            case WEST:
                buffer.pos(x, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
                break;
            case EAST:
                buffer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
                buffer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
                break;
        }
    }


}
