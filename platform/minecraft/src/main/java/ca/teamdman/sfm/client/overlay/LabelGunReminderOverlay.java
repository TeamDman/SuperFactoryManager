package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;

public class LabelGunReminderOverlay {


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

        LabelGunItem.LabelGunViewMode viewMode = getViewMode(player);
        if (viewMode == null) return;
        var msg = switch(viewMode) {
            case SHOW_ALL -> null;
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK -> LocalizationKeys.LABEL_GUN_VIEW_MODE_SHOW_ONLY_ACTIVE_AND_TARGETED;
            case SHOW_ONLY_TARGETED_BLOCK -> LocalizationKeys.LABEL_GUN_VIEW_MODE_SHOW_ONLY_TARGETED;
        };
        if (msg == null) return;
        FontRenderer font = minecraft.fontRenderer;
        var reminder = msg.getComponent(
                new TextComponentString(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY
                        .getDisplayName()).setStyle(new Style().setColor(TextFormatting.YELLOW))
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
        )
        ;
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static @Nullable LabelGunItem.LabelGunViewMode getViewMode(EntityPlayerSP player) {
        if (player == null) return null;
        if (!SFMConfig.client.showLabelGunReminderOverlay) return null;
        ItemStack labelGun = SFMHandUtils.getItemInEitherHand(player, SFMItems.LABEL_GUN);
        if (labelGun.isEmpty()) return null;
        return LabelGunItem.getViewMode(labelGun);
    }
}
