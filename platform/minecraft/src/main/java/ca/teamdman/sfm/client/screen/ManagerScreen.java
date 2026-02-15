package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.diagnostics.SFMDiagnostics;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundManagerFixPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerProgramPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerRebuildPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerResetPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.common.util.TextFormattingColors;
import ca.teamdman.sfml.ast.Program;
import com.bbscn.Button;
import com.bbscn.Renderable;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Optional;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;


@SuppressWarnings({"FieldCanBeLocal", "unused", "NotNullFieldNotInitialized"})
@Optional.Interface(modid="jei",iface="mezz.jei.api.gui.IAdvancedGuiHandler")
public class ManagerScreen extends GuiContainerExtend implements IAdvancedGuiHandler<ManagerScreen> {
    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/manager.png"
    );
    private final float STATUS_DURATION = 40;
    private ITextComponent status = new TextComponentString("");
    private float statusCountdown = 0;
    private Button diagButton;
    private Button clipboardPasteButton;
    private Button clipboardCopyButton;
    private Button discordButton;
    private Button resetButton;
    private Button editButton;
    private Button examplesButton;
    private Button logsButton;
    private Button rebuildButton;
    private Button serverConfigButton;

    private final SFMButtonBuilder buttonBuilder = new SFMButtonBuilder();


    protected int titleLabelX;
    protected int titleLabelY;
    protected int inventoryLabelX;
    protected int inventoryLabelY;

    protected ManagerContainerMenu menu;

    public ManagerScreen(
            ManagerContainerMenu menu
    ) {
        super(menu);
        this.menu = menu;

        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = ySize - 96;
    }

    public List<Button> getButtonsForJEIExclusionZones() {
        return Arrays.asList(
                clipboardPasteButton,
                editButton,
                examplesButton,
                clipboardCopyButton,
                logsButton,
                rebuildButton,
                serverConfigButton
        );
    }

    public boolean isReadOnly() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null || player.isSpectator();
    }

    public void updateVisibilities() {
        boolean diskPresent = inventorySlots.getSlot(0).getHasStack();
        diagButton.visible = shouldShowDiagButton();
        clipboardCopyButton.visible = diskPresent;
        logsButton.visible = diskPresent;
        rebuildButton.visible = diskPresent && !isReadOnly();
        clipboardPasteButton.visible = diskPresent && !isReadOnly();
        resetButton.visible = diskPresent && !isReadOnly();
        editButton.visible = diskPresent && !isReadOnly();
    }

    public boolean keyPressed(
            int pKeyCode,
            int pScanCode,
            int pModifiers
    ) {
        if (GuiScreen.isKeyComboCtrlV(pKeyCode) && clipboardPasteButton.visible) {
            onClipboardPasteButtonClicked();
            return true;
        } else if (GuiScreen.isKeyComboCtrlC(pKeyCode) && clipboardCopyButton.visible) {
            onClipboardCopyButtonClicked();
            return true;
        } else if (pKeyCode == Keyboard.KEY_E
                && GuiScreen.isCtrlKeyDown()
                && GuiScreen.isShiftKeyDown()
                && examplesButton.visible) {
            onExamplesButtonClicked();
            return true;
        } else if (SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY.isActiveAndMatches(pKeyCode)
                && editButton.visible) {
            onEditButtonClicked();
            return true;
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    public TextFormatting getMillisecondColour(float ms) {
        if (ms <= 5) {
            return TextFormatting.GREEN;
        } else if (ms <= 15) {
            return TextFormatting.YELLOW;
        } else {
            return TextFormatting.RED;
        }
    }


    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        for (Renderable renderable : this.renderables) {
            renderable.render(mouseX, mouseY, partialTicks);
        }

        this.renderHoveredToolTip(mouseX, mouseY);
        updateVisibilities();
        statusCountdown -= partialTicks;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        super.drawGuiContainerForegroundLayer(mx, my);
        drawLabels(mx, my);
    }

    /**
     * Draws the background layer of this container (behind the items).
     */
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (!menu.logLevel.equals(Level.OFF.name())) {
            GlStateManager.color(0.2F, 0.8F, 1.0F, 1.0F);
        } else {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        this.mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE_LOCATION);

        // Top-left corner of the GUI
        int i = (this.width - this.xSize) / 2;
        int j = (this.height - this.ySize) / 2;

        this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);
    }


    @Override
    public void initGui() {
        super.initGui();
        int buttonWidth = 120;
        int buttonHeight = 16;
        clipboardPasteButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 16
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON)
                        .setOnPress(button -> this.onClipboardPasteButtonClicked())
                        .setTooltip(
                                this,
                                this.fontRenderer,
                                MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON_TOOLTIP
                        )
                        .build()
        );
        editButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 16 + 50
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_EDIT_BUTTON)
                        .setOnPress(button -> onEditButtonClicked())
                        .setTooltip(
                                this,
                                this.fontRenderer,
                                MANAGER_GUI_EDIT_BUTTON_TOOLTIP.getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY))
                        )
                        .build()
        );
        examplesButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 16 * 2 + 50
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_VIEW_EXAMPLES_BUTTON)
                        .setOnPress(button -> onExamplesButtonClicked())
                        .setTooltip(
                                this,
                                this.fontRenderer,
                                MANAGER_GUI_VIEW_EXAMPLES_BUTTON_TOOLTIP
                        )
                        .build()
        );
        discordButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 112
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_DISCORD_BUTTON)
                        .setOnPress(button -> this.onDiscordButtonClicked())
                        .build()
        );
        clipboardCopyButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 128
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_COPY_TO_CLIPBOARD_BUTTON)
                        .setOnPress(button -> this.onClipboardCopyButtonClicked())
                        .build()
        );
        logsButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 16 * 9
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_VIEW_LOGS_BUTTON)
                        .setOnPress(button -> onLogsButtonClicked())
                        .build()
        );
        rebuildButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 - buttonWidth,
                                (this.height - this.ySize) / 2 + 16 * 10
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_REBUILD_BUTTON)
                        .setOnPress(button -> this.onRebuildButtonClicked())
                        .build()
        );
        resetButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 + 120,
                                (this.height - this.ySize) / 2 + 10
                        )
                        .setSize(50, 12)
                        .setText(MANAGER_GUI_RESET_BUTTON)
                        .setOnPress(button -> onResetButtonClicked())
                        .setTooltip(this, this.fontRenderer, MANAGER_GUI_RESET_BUTTON_TOOLTIP)
                        .build()
        );
        diagButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.xSize) / 2 + 35,
                                (this.height - this.ySize) / 2 + 48
                        )
                        .setSize(12, 14)
                        .setText(new TextComponentString("!"))
                        .setOnPress(button -> onDiagButtonClicked())
                        .setTooltip(
                                this, this.fontRenderer, isReadOnly()
                                        ? MANAGER_GUI_WARNING_BUTTON_TOOLTIP_READ_ONLY
                                        : MANAGER_GUI_WARNING_BUTTON_TOOLTIP
                        )
                        .build()
        );
        updateVisibilities();
    }

    private void onDiagButtonClicked() {
        if (GuiScreen.isShiftKeyDown() && !isReadOnly()) {
            sendAttemptFix();
        } else {
            this.onSaveDiagnosticsToClipboard();
        }
    }

    private String getProgram() {
        return menu.program;
    }

    private void onEditButtonClicked() {
        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                getProgram(),
                LabelPositionHolder.from(menu.getDisk()),
                this::sendProgram
        ));
    }

    private void onExamplesButtonClicked() {
        SFMScreenChangeHelpers.showExampleListScreen(
                getProgram(),
                LabelPositionHolder.from(menu.getDisk()),
                this::sendProgram
        );
    }

    private void onLogsButtonClicked() {
        SFMScreenChangeHelpers.showLogsScreen(menu);
    }

    private void performReset() {
        SFMPackets.sendToServer(new ServerboundManagerResetPacket(
                menu.windowId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_RESET.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void onResetButtonClicked() {
        if (getProgram().trim().isEmpty() && LabelPositionHolder.from(menu.getDisk()).isEmpty()) {
            performReset();
            return;
        }
        SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                this::performReset,
                LocalizationKeys.MANAGER_RESET_CONFIRM_SCREEN_TITLE.getComponent(),
                LocalizationKeys.MANAGER_RESET_CONFIRM_SCREEN_MESSAGE.getComponent(),
                LocalizationKeys.MANAGER_RESET_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                LocalizationKeys.MANAGER_RESET_CONFIRM_SCREEN_NO_BUTTON.getComponent(),
                20
        ));
    }

    private void onRebuildButtonClicked() {
        SFMPackets.sendToServer(new ServerboundManagerRebuildPacket(
                menu.windowId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_REBUILD.getComponent();
        statusCountdown = STATUS_DURATION;
    }


    private void sendAttemptFix() {
        SFMPackets.sendToServer(new ServerboundManagerFixPacket(
                menu.windowId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_FIX.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void sendProgram(String program) {
        program = program.length() > Program.MAX_PROGRAM_LENGTH ? program.substring(0, Program.MAX_PROGRAM_LENGTH) : program;
        SFMPackets.sendToServer(new ServerboundManagerProgramPacket(
                menu.windowId,
                menu.MANAGER_POSITION,
                program
        ));
        menu.program = program;
        status = MANAGER_GUI_STATUS_LOADED_CLIPBOARD.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void onDiscordButtonClicked() {
        String discordUrl = "https://discord.gg/xjXYj9MmS4";
        SFMScreenChangeHelpers.setOrPushScreen(
                new GuiConfirmOpenLink(
                        (proceed, buttonId) -> {
                            if (proceed) {
//                                Util.getPlatform().openUri(discordUrl);
                            }
                            SFMScreenChangeHelpers.popScreen();
                        },
                        discordUrl,
                        0,
                        false
                )
        );
    }

    private void onClipboardCopyButtonClicked() {
        try {
            GuiScreen.setClipboardString(menu.program);
            status = MANAGER_GUI_STATUS_SAVED_CLIPBOARD.getComponent();
            statusCountdown = STATUS_DURATION;
        } catch (Throwable t) {
            SFM.LOGGER.error("failed to save clipboard", t);
        }
    }

    private boolean shouldShowDiagButton() {
        var disk = menu.getDisk();
        if (!(disk.getItem() instanceof DiskItem)) return false;
        var errors = DiskItem.getErrors(disk);
        var warnings = DiskItem.getWarnings(disk);
        return !errors.isEmpty() || !warnings.isEmpty();
    }

    private void onSaveDiagnosticsToClipboard() {
        try {
            var disk = menu.CONTAINER.getStackInSlot(0);
            if (!(disk.getItem() instanceof DiskItem)) return;
            String diagnosticInfo = SFMDiagnostics.getDiagnosticsSummary(disk);
            GuiScreen.setClipboardString(diagnosticInfo);
            status = MANAGER_GUI_STATUS_SAVED_CLIPBOARD.getComponent();
            statusCountdown = STATUS_DURATION;
        } catch (Throwable t) {
            SFM.LOGGER.error("failed saving clipboard", t);
        }
    }

    private void onClipboardPasteButtonClicked() {
        String clipboardContents;
        try {
            clipboardContents = GuiScreen.getClipboardString();
        } catch (Throwable t) {
            SFM.LOGGER.error("failed loading clipboard", t);
            return;
        }
        String existingProgram = getProgram();
        boolean shouldConfirm = !existingProgram.trim().isEmpty() && !existingProgram.equals(clipboardContents);
        if (!shouldConfirm) {
            sendProgram(clipboardContents);
            return;
        }
        SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                () -> sendProgram(clipboardContents),
                LocalizationKeys.MANAGER_PASTE_CONFIRM_SCREEN_TITLE.getComponent(),
                LocalizationKeys.MANAGER_PASTE_CONFIRM_SCREEN_MESSAGE.getComponent(),
                LocalizationKeys.MANAGER_PASTE_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                LocalizationKeys.MANAGER_PASTE_CONFIRM_SCREEN_NO_BUTTON.getComponent(),
                20
        ));
    }

    @MCVersionDependentBehaviour
    private void disableTexture() {
        GlStateManager.disableTexture2D();
    }

    protected void drawLabels(
            int mx,
            int my
    ) {
        this.fontRenderer.drawString(menu.CONTAINER.getDisplayName().getFormattedText(), this.titleLabelX, this.titleLabelY, 4210752);
        this.fontRenderer.drawString(menu.PLAYER_INVENTORY.getDisplayName().getUnformattedText(), this.inventoryLabelX, this.inventoryLabelY, 4210752);


        // draw state string
        var state = menu.state;
        SFMFontUtils.draw(
                this.fontRenderer,
                MANAGER_GUI_STATE.getComponent(state.LOC.getComponent().setStyle(new Style().setColor(state.COLOR))),
                titleLabelX,
                20,
                0,
                false
        );

        // draw log level
        if (!menu.logLevel.equals(Level.OFF.name())) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    titleLabelX,
                    this.fontRenderer.FONT_HEIGHT * 1.5,
                    0f
            );
            GlStateManager.scale(0.5f, 0.5f, 1f);
            SFMFontUtils.draw(
                    this.fontRenderer,
                    menu.logLevel,
                    0,
                    0,
                    0xFFFFFF,
                    false
            );
            GlStateManager.popMatrix();
        }

        // draw status string
        if (statusCountdown > 0) {
            SFMFontUtils.draw(
                    this.fontRenderer,
                    status,
                    inventoryLabelX + fontRenderer.getStringWidth(menu.PLAYER_INVENTORY.getDisplayName().getUnformattedText()) + 5,
                    inventoryLabelY,
                    0,
                    false
            );
        }

        boolean isShifted = GuiScreen.isShiftKeyDown();

        // Find the maximum tick time for normalization
        Duration peakTickTime = Duration.ZERO;
        for (int i = 0; i < menu.tickTimes.length; i++) {
            Duration candidate = menu.tickTimes[i];
            if (candidate != null && candidate.compareTo(peakTickTime) > 0) {
                peakTickTime = candidate;
            }
        }
        long yMax = Long.max(peakTickTime.toNanos(), isShifted ? 1_000_000 : 50_000_000); // Start with max at 10 ms but allow it to grow

        // Constants for the plot size and position
        final int plotX = titleLabelX + 45;
        final int plotY = 40;
        final int spaceBetweenPoints = 6;
        final int plotWidth = spaceBetweenPoints * (menu.tickTimes.length - 1);
        final int plotHeight = 30;


        // Set up rendering
        disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

//        GlStateManager.tryBlendFuncSeparate(
//                GlStateManager.SourceFactor.SRC_ALPHA,
//                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
//                GlStateManager.SourceFactor.ONE,
//                GlStateManager.DestFactor.ZERO
//        );

        Tessellator tesselator = Tessellator.getInstance();
        BufferBuilder buffer;

        // Draw the plot background
        buffer = tesselator.getBuffer();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        buffer.pos(plotX + plotWidth, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        buffer.pos(plotX + plotWidth, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        buffer.pos(plotX, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        buffer.pos(plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        tesselator.draw();


        // Draw lines for each data point
        buffer = tesselator.getBuffer();
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int mouseTickTimeIndex = -1;
        for (int i = 0; i < menu.tickTimes.length; i++) {
            if (menu.tickTimes[i] == null) {
                continue;
            }
            long y = menu.tickTimes[i].toNanos();
            float normalizedTickTime = y == 0 ? 0 : isShifted ? ((float)(y) / (yMax)) : (float) (Math.log10(y) / Math.log10(yMax));
            int plotPosY = plotY + plotHeight - Math.round(normalizedTickTime * plotHeight);

            int plotPosX = plotX + spaceBetweenPoints * i;

            // Color the lines based on their tick times (green to red)
            var c = TextFormattingColors.getColorCode(getMillisecondColour(y / 1_000_000f));
            float red = ((c >> 16) & 0xFF) / 255f;
            float green = ((c >> 8) & 0xFF) / 255f;
            float blue = (c & 0xFF) / 255f;

            buffer.pos(plotPosX, plotPosY, getBlitOffsetGood())
                    .color(red, green, blue, 1.0F)
                    .endVertex();

            // Check if the mouse is hovering over this line
            if (mx - guiLeft >= plotPosX - spaceBetweenPoints / 2
                    && mx - guiLeft <= plotPosX + spaceBetweenPoints / 2
                    && my - guiTop >= plotY - 2
                    && my - guiTop <= plotY + plotHeight + 2) {
                mouseTickTimeIndex = i;
            }
        }
        tesselator.draw();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < menu.externalTickTimes.length; i++) {
            if (menu.externalTickTimes[i] == null) {
                continue;
            }
            long y = menu.externalTickTimes[i].toNanos();
            float normalizedTickTime = y == 0 ? 0 : isShifted ? ((float)(y) / (yMax)) : (float) (Math.log10(y) / Math.log10(yMax));
            int plotPosY = plotY + plotHeight - Math.round(normalizedTickTime * plotHeight);

            int plotPosX = plotX + spaceBetweenPoints * i;

            // Color the lines based on their tick times (green to red)
            var c = TextFormattingColors.getColorCode(getMillisecondColour(y / 1_000_000f));
            float red = ((c >> 16) & 0xFF) / 255f;
            float green = ((c >> 8) & 0xFF) / 255f;
            float blue = (c & 0xFF) / 255f;

            buffer.pos(plotPosX, plotPosY, getBlitOffsetGood())
                    .color(red, green, blue, 1.0F)
                    .endVertex();

        }
        tesselator.draw();

        // Draw the tick time text
        var format = new DecimalFormat("0.000");
        if (mouseTickTimeIndex != -1) { // We are hovering over the plot
            // Draw the tick time text for the hovered point instead of peak
            {
                long hoveredTickTimeNanoseconds = menu.tickTimes[mouseTickTimeIndex].toNanos();
                long hoveredExternalTickTimeNanoseconds = menu.externalTickTimes[mouseTickTimeIndex].toNanos();
                var hoveredTickTimeMilliseconds = hoveredTickTimeNanoseconds / 1_000_000f;
                var hoveredExternalTickTimeMilliseconds = hoveredExternalTickTimeNanoseconds / 1_000_000f;
                String formattedMillis = format.format(hoveredTickTimeMilliseconds);
                String formattedMillisExternal = format.format(hoveredExternalTickTimeMilliseconds);
                TextFormatting lagColor = getMillisecondColour(hoveredTickTimeMilliseconds);
                ITextComponent milliseconds = new TextComponentString(formattedMillis).setStyle(new Style().setColor(lagColor));
                ITextComponent millisecondsExternal = new TextComponentString(formattedMillisExternal).setStyle(new Style().setColor(
                        lagColor));

                enableTexture();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                if (isShifted) {
                    SFMFontUtils.draw(
                            this.fontRenderer,
                            MANAGER_GUI_EXTERNAL_TICK_TIME_MS.getComponent(millisecondsExternal).getFormattedText(),
                            titleLabelX,
                            20 + fontRenderer.FONT_HEIGHT,
                            0xFFFFFF, //  TextFormattingColors.getColorCode(lagColor),
                            false
                    );
                } else {
                    SFMFontUtils.draw(
                            this.fontRenderer,
                            MANAGER_GUI_HOVERED_TICK_TIME_MS.getComponent(milliseconds).getFormattedText(),
                            titleLabelX,
                            20 + fontRenderer.FONT_HEIGHT,
                            0xFFFFFF, //  TextFormattingColors.getColorCode(lagColor),
                            false
                    );
                }
            }

            // draw a vertical line
            disableTexture();
            buffer = tesselator.getBuffer();
            buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

            int x = plotX + spaceBetweenPoints * mouseTickTimeIndex;
            buffer
                    .pos(x, plotY, getBlitOffsetGood())
                    .color(1f, 1f, 1f, 1f)
                    .endVertex();
            buffer
                    .pos(x, plotY + plotHeight, getBlitOffsetGood())
                    .color(1f, 1f, 1f, 1f)
                    .endVertex();
            tesselator.draw();
        } else {
            // Draw the tick time text for peak value
            var peakTickTimeMilliseconds = peakTickTime.toNanos() / 1_000_000f; // we want decimal precision
            String formattedMillis = format.format(peakTickTimeMilliseconds);
            TextFormatting lagColor = getMillisecondColour(peakTickTimeMilliseconds);
            ITextComponent milliseconds = new TextComponentString(formattedMillis).setStyle(new Style().setColor(lagColor));
            enableTexture();

            SFMFontUtils.draw(
                    this.fontRenderer,
                    MANAGER_GUI_PEAK_TICK_TIME_MS.getComponent(milliseconds),
                    titleLabelX,
                    20 + fontRenderer.FONT_HEIGHT,
                    0,
                    false
            );
        }

        // Restore stuff
        GlStateManager.disableBlend();
        enableTexture();
    }

    @MCVersionDependentBehaviour
    private void enableTexture() {
        GlStateManager.enableTexture2D();
    }

    @MCVersionDependentBehaviour
    public float getBlitOffsetGood() {
        return 0F;
    }

    @Override
    public Class<ManagerScreen> getGuiContainerClass() {
        return ManagerScreen.class;
    }

    @Nullable
    @Override
    public List<Rectangle> getGuiExtraAreas(ManagerScreen guiContainer) {
        return getButtonsForJEIExclusionZones()
                .stream()
                .map(button -> new Rectangle(button.getX(), button.getY(), button.getWidth(), button.getHeight()))
                .collect(Collectors.toList());
    }

}
