package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class NetworkToolReminderOverlay {
    @SuppressWarnings("DuplicatedCode")
    public void render(
            float partialTick,
            int screenWidth,
            int screenHeight
    ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.hideGUI) {
            return;
        }
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }

        if (!shouldRender(player)) return;


        FontRenderer font = minecraft.fontRenderer;
        var reminder = LocalizationKeys.NETWORK_TOOL_REMINDER_OVERLAY.getComponent(
                new TextComponentString(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY.getDisplayName())
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))
        );

        int reminderWidth = font.getStringWidth(reminder.getUnformattedText());
        int x = screenWidth / 2 - reminderWidth / 2;
        int y = 20;
        SFMFontUtils.draw(
                font,
                reminder,
                x,
                y,
                0xFFACD0FF,
                true
        );
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean shouldRender(EntityPlayerSP player) {
        if (!SFMConfig.client.showNetworkToolReminderOverlay) return false;
        ItemStack networkTool = SFMHandUtils.getItemInEitherHand(player, SFMItems.NETWORK_TOOL_ITEM);
//        return !networkTool.isEmpty() && NetworkToolItem.getOverlayEnabled(networkTool);
        return !networkTool.isEmpty();
    }
}
