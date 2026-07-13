package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SFMTitleScreenDevScreenChooserScreen extends Screen {
    private static final int PANEL = 0xD8202020;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFB0B0B0;

    private final TitleScreen titleScreen;

    public SFMTitleScreenDevScreenChooserScreen(TitleScreen titleScreen) {
        super(Component.literal("SFM Dev Screens"));
        this.titleScreen = titleScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(titleScreen);
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 180;
        int buttonHeight = 20;
        int spacing = 6;
        int x = this.width / 2 - buttonWidth / 2;
        int panelHeight = panelHeight();
        int y = this.height / 2 - panelHeight / 2 + 46;

        for (SFMTitleScreenDevScreen devScreen : SFMTitleScreenDevScreen.values()) {
            this.addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(x, y)
                    .setSize(buttonWidth, buttonHeight)
                    .setText(devScreen.displayName())
                    .setOnPress(button -> Minecraft.getInstance().setScreen(devScreen.create(titleScreen)))
                    .build());
            y += buttonHeight + spacing;
        }

        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(x, y)
                .setSize(buttonWidth, buttonHeight)
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> this.onClose())
                .build());
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(guiGraphics);
        int panelWidth = 220;
        int panelHeight = panelHeight();
        int left = this.width / 2 - panelWidth / 2;
        int top = this.height / 2 - panelHeight / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        guiGraphics.fill( left, top, right, bottom, PANEL);
        guiGraphics.fill( left, top, right, top + 1, BORDER);
        guiGraphics.fill( left, bottom - 1, right, bottom, BORDER);
        guiGraphics.fill( left, top, left + 1, bottom, BORDER);
        guiGraphics.fill( right - 1, top, right, bottom, BORDER);
        guiGraphics.drawCenteredString( this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, top + 12, TEXT);
        guiGraphics.drawCenteredString( this.font, "IDE-only launch tools", this.width / 2, top + 26, MUTED);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static int panelHeight() {
        return 76 + (SFMTitleScreenDevScreen.values().length + 1) * 26;
    }
}
