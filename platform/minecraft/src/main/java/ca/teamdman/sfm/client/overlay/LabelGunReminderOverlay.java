package ca.teamdman.sfm.client.overlay;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMHandUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.jetbrains.annotations.Nullable;

public class LabelGunReminderOverlay implements IGuiOverlay {


    @SuppressWarnings("DuplicatedCode")
    @Override
    public void render(
            ForgeGui gui,
            PoseStack poseStack,
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

        LabelGunItem.LabelGunViewMode viewMode = getViewMode(minecraft);
        if (viewMode == null) return;
        var msg = switch(viewMode) {
            case SHOW_ALL -> null;
            case SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK -> LocalizationKeys.LABEL_GUN_VIEW_MODE_SHOW_ONLY_ACTIVE_AND_TARGETED;
            case SHOW_ONLY_TARGETED_BLOCK -> LocalizationKeys.LABEL_GUN_VIEW_MODE_SHOW_ONLY_TARGETED;
        };
        if (msg == null) return;
        Font font = minecraft.font;
        var reminder = msg.getComponent(
                SFMKeyMappings.CYCLE_LABEL_VIEW_KEY
                        .get()
                        .getTranslatedKeyMessage().plainCopy().withStyle(ChatFormatting.YELLOW)
        );
        int reminderWidth = font.width(reminder);
        int x = screenWidth / 2 - reminderWidth / 2;
        int y = 20;
        SFMFontUtils.draw(
                poseStack,
                font,
                reminder,
                x,
                y,
                FastColor.ARGB32.color(255, 172, 208, 255),
                true
        );
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static @Nullable LabelGunItem.LabelGunViewMode getViewMode(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) return null;
        if (!SFMConfig.CLIENT_CONFIG.showLabelGunReminderOverlay.get()) return null;
        ItemStack labelGun = SFMHandUtils.getItemInEitherHand(player, SFMItems.LABEL_GUN_ITEM.get());
        if (labelGun.isEmpty()) return null;
        return LabelGunItem.getViewMode(labelGun);
    }
}
