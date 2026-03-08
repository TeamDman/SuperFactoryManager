package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class NetworkToolReminderOverlay implements IGuiOverlay {
    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_REMINDER_OVERLAY = new LocalizationEntry(
            () -> "sfm.network_tool.reminder_overlay",
            () -> "Toggle network tool overlay with %s"
    );

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void render(
            ForgeGui gui,
            GuiGraphics guiGraphics,
            float partialTick,
            int screenWidth,
            int screenHeight
    ) {

        Minecraft minecraft = gui.getMinecraft();
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
        int x = screenWidth / 2 - reminderWidth / 2;
        int y = 30;
        SFMFontUtils.draw(
                guiGraphics,
                font,
                reminder,
                x,
                y,
                FastColor.ARGB32.color(255, 172, 208, 255),
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
