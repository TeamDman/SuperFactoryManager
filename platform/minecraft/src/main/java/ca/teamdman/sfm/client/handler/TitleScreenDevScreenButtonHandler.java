package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreenChooserScreen;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

public class TitleScreenDevScreenButtonHandler {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!SFMEnvironmentUtils.isInIDE()) return;
        if (!(event.getScreen() instanceof TitleScreen titleScreen)) return;

        event.addListener(new SFMButtonBuilder()
                .setPosition(4, 4)
                .setSize(70, 20)
                .setText(Component.literal("SFM Dev"))
                .setOnPress(button -> Minecraft.getInstance().setScreen(
                        new SFMTitleScreenDevScreenChooserScreen(titleScreen)
                ))
                .build());
    }
}
