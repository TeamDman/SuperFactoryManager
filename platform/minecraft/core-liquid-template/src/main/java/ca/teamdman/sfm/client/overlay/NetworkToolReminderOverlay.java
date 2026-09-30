package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMHandUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.client.DeltaTracker;
{% endcase %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.client.gui.GuiGraphics;
{% when '1.21', '1.21.1' %}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.util.FastColor;
{% when '26.1.2' %}
import net.minecraft.util.ARGB;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.neoforged.neoforge.client.gui.overlay.ExtendedGui;
import net.neoforged.neoforge.client.gui.overlay.IGuiOverlay;
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.neoforged.neoforge.client.gui.GuiLayer;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
public class NetworkToolReminderOverlay implements IGuiOverlay {
{% when '1.21', '1.21.1' %}
public class NetworkToolReminderOverlay implements LayeredDraw.Layer {
{% when '26.1.2' %}
public class NetworkToolReminderOverlay implements GuiLayer {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_REMINDER_OVERLAY = new LocalizationEntry(
            () -> "sfm.network_tool.reminder_overlay",
            () -> "Toggle network tool overlay with %s"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @SuppressWarnings("DuplicatedCode")
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
    public void render(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
            ForgeGui gui,
            PoseStack poseStack,
            float partialTick,
            int screenWidth,
            int screenHeight
{% when '1.20', '1.20.1' %}
            ForgeGui gui,
            GuiGraphics guiGraphics,
            float partialTick,
            int screenWidth,
            int screenHeight
{% when '1.20.2', '1.20.3', '1.20.4' %}
            ExtendedGui gui,
            GuiGraphics guiGraphics,
            float partialTick,
            int screenWidth,
            int screenHeight
{% when '1.21', '1.21.1' %}
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker
{% when '26.1.2' %}
            GuiGraphicsExtractor guiGraphics,
            DeltaTracker deltaTracker
{% endcase %}
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        Minecraft minecraft = gui.getMinecraft();
{% when '1.21', '1.21.1', '26.1.2' %}
        Minecraft minecraft = Minecraft.getInstance();
{% endcase %}
        if (minecraft.options.hideGui) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!shouldRender(minecraft)) {
            return;
        }
        Font font = minecraft.font;
        var reminder = NETWORK_TOOL_REMINDER_OVERLAY.getComponent(
                SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY
                        .get()
                        .getTranslatedKeyMessage().plainCopy().withStyle(ChatFormatting.YELLOW)
        );
        int reminderWidth = font.width(reminder);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        int x = screenWidth / 2 - reminderWidth / 2;
{% when '1.21', '1.21.1', '26.1.2' %}
        int x = guiGraphics.guiWidth() / 2 - reminderWidth / 2;
{% endcase %}
        int y = 30;
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                guiGraphics,
{% endcase %}
                font,
                reminder,
                x,
                y,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                FastColor.ARGB32.color(255, 172, 208, 255),
{% when '26.1.2' %}
                ARGB.color(255, 172, 208, 255),
{% endcase %}
                true
        );
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean shouldRender(Minecraft minecraft) {

        LocalPlayer player = minecraft.player;
        if (player == null) return false;
        if (!SFMConfig.CLIENT_CONFIG.showNetworkToolReminderOverlay.get()) return false;
        ItemStack networkTool = SFMHandUtils.getItemInEitherHand(player, SFMItems.NETWORK_TOOL.get());
//        return !networkTool.isEmpty() && NetworkToolItem.getOverlayEnabled(networkTool);
        return !networkTool.isEmpty();
    }

}
