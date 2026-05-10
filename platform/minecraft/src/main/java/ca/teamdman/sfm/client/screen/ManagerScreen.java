package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.diagnostics.SFMDiagnostics;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.apache.logging.log4j.Level;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused", "NotNullFieldNotInitialized"})
public class ManagerScreen extends AbstractContainerScreen<ManagerContainerMenu> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.tooltip.paste",
            "Press Ctrl+V to paste."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_EDIT_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.edit_button.tooltip",
            "Press %s to edit."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_EDIT_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.edit_button",
            "Edit"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_RESET_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.tooltip.reset",
            "Wipes ALL disk data."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATUS_FIX = new LocalizationEntry(
            "gui.sfm.manager.status.fix",
            "Fixing problems!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATUS_RESET = new LocalizationEntry(
            "gui.sfm.manager.status.reset",
            "Reset program and labels!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATUS_REBUILD = new LocalizationEntry(
            "gui.sfm.manager.status.rebuild",
            "Rebuilding cache!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_VIEW_EXAMPLES_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.button.view_examples.tooltip",
            "Press Ctrl+Shift+E to view examples."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_RESET_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.reset",
            "Reset"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_WARNING_BUTTON_TOOLTIP = new LocalizationEntry(
            "gui.sfm.manager.button.warning.tooltip",
            "Click to copy code with warnings and errors.\nShift-click to attempt to fix warnings."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_WARNING_BUTTON_TOOLTIP_READ_ONLY = new LocalizationEntry(
            "gui.sfm.manager.button.warning.tooltip.read_only",
            "Click to copy code with warnings and errors."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATUS_LOADED_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.manager.status.loaded_clipboard",
            "Loaded from clipboard!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATUS_SAVED_CLIPBOARD = new LocalizationEntry(
            "gui.sfm.manager.status.saved_clipboard",
            "Saved to clipboard!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_SERVER_CONFIG_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.server_config",
            "View server config"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.paste_clipboard",
            "Paste from clipboard"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_COPY_TO_CLIPBOARD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.copy_to_clipboard",
            "Copy to clipboard"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_VIEW_EXAMPLES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.view_examples",
            "View examples"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_VIEW_LOGS_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.view_logs",
            "View logs"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_DISCORD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.discord",
            "Discord"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_REBUILD_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.button.rebuild",
            "Rebuild cable network"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_PEAK_TICK_TIME_MS = new LocalizationEntry(
            "gui.sfm.manager.peak_tick_time",
            "Peak tick time: %s ms"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_HOVERED_TICK_TIME_MS = new LocalizationEntry(
            "gui.sfm.manager.hovered_tick_time",
            "Hovered tick time: %s ms"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_GUI_STATE = new LocalizationEntry(
            "gui.sfm.manager.state",
            "State: %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.title",
            "Reset disk?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.message",
            "Are you sure you want to reset this disk?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.yes_button",
            "Wipe program and labels"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_RESET_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.reset_confirm_screen.no_button",
            "Never mind, make no changes"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_TITLE = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.title",
            "Paste from clipboard?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_MESSAGE = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.message",
            "Are you sure you want to overwrite this disk?"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_YES_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.yes_button",
            "Paste clipboard"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_PASTE_CONFIRM_SCREEN_NO_BUTTON = new LocalizationEntry(
            "gui.sfm.manager.paste_confirm_screen.no_button",
            "Never mind, make no changes"
    );

    private static final Identifier BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/manager.png"
    );

    private final float STATUS_DURATION = 40;

    private Component status = Component.empty();

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

    public ManagerScreen(
            ManagerContainerMenu menu,
            Inventory inv,
            Component title
    ) {

        super(menu, inv, title);
    }

    public List<Button> getButtonsForJEIExclusionZones() {

        return List.of(
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

        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || player.isSpectator();
    }

    public void updateVisibilities() {

        boolean diskPresent = menu.getSlot(0).hasItem();
        diagButton.visible = shouldShowDiagButton();
        clipboardCopyButton.visible = diskPresent;
        logsButton.visible = diskPresent;
        rebuildButton.visible = diskPresent && !isReadOnly();
        clipboardPasteButton.visible = diskPresent && !isReadOnly();
        resetButton.visible = diskPresent && !isReadOnly();
        editButton.visible = diskPresent && !isReadOnly();
    }

    @Override
    public boolean keyPressed(
            KeyEvent event
    ) {

        if (event.isPaste() && clipboardPasteButton.visible) {
            onClipboardPasteButtonClicked();
            return true;
        } else if (event.isCopy() && clipboardCopyButton.visible) {
            onClipboardCopyButtonClicked();
            return true;
        } else if (event.key() == GLFW.GLFW_KEY_E
                   && event.hasControlDown()
                   && event.hasShiftDown()
                   && examplesButton.visible) {
            onExamplesButtonClicked();
            return true;
        } else if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY)
                   && editButton.visible) {
            onEditButtonClicked();
            return true;
        }
        return super.keyPressed(event);
    }

    public ChatFormatting getMillisecondColour(float ms) {

        if (ms <= 5) {
            return ChatFormatting.GREEN;
        } else if (ms <= 15) {
            return ChatFormatting.YELLOW;
        } else {
            return ChatFormatting.RED;
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks
    ) {

        this.extractTransparentBackground(graphics);
        super.extractRenderState(graphics, mx, my, partialTicks);
        this.extractTooltip(graphics, mx, my);

        updateVisibilities();

        // update status countdown
        statusCountdown -= partialTicks;
    }


    @Override
    protected void init() {

        super.init();
        int buttonWidth = 120;
        int buttonHeight = 16;
        clipboardPasteButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON)
                        .setOnPress(button -> this.onClipboardPasteButtonClicked())
                        .setTooltip(
                                this,
                                font,
                                MANAGER_GUI_PASTE_FROM_CLIPBOARD_BUTTON_TOOLTIP
                        )
                        .build()
        );
        editButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16 + 50
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_EDIT_BUTTON)
                        .setOnPress(button -> onEditButtonClicked())
                        .setTooltip(
                                this,
                                font,
                                MANAGER_GUI_EDIT_BUTTON_TOOLTIP.getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY))
                        )
                        .build()
        );
        examplesButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16 * 2 + 50
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_VIEW_EXAMPLES_BUTTON)
                        .setOnPress(button -> onExamplesButtonClicked())
                        .setTooltip(
                                this,
                                font,
                                MANAGER_GUI_VIEW_EXAMPLES_BUTTON_TOOLTIP
                        )
                        .build()
        );
        discordButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 112
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_DISCORD_BUTTON)
                        .setOnPress(button -> this.onDiscordButtonClicked())
                        .build()
        );
        clipboardCopyButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 128
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_COPY_TO_CLIPBOARD_BUTTON)
                        .setOnPress(button -> this.onClipboardCopyButtonClicked())
                        .build()
        );
        logsButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16 * 9
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_VIEW_LOGS_BUTTON)
                        .setOnPress(button -> onLogsButtonClicked())
                        .build()
        );
        rebuildButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16 * 10
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_REBUILD_BUTTON)
                        .setOnPress(button -> this.onRebuildButtonClicked())
                        .build()
        );
        serverConfigButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 - buttonWidth,
                                (this.height - this.imageHeight) / 2 + 16 * 11
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(MANAGER_GUI_SERVER_CONFIG_BUTTON)
                        .setOnPress(button -> this.onServerConfigButtonClicked())
                        .build()
        );
        resetButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 + 120,
                                (this.height - this.imageHeight) / 2 + 10
                        )
                        .setSize(50, 12)
                        .setText(MANAGER_GUI_RESET_BUTTON)
                        .setOnPress(button -> onResetButtonClicked())
                        .setTooltip(this, font, MANAGER_GUI_RESET_BUTTON_TOOLTIP)
                        .build()
        );
        diagButton = this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(
                                (this.width - this.imageWidth) / 2 + 35,
                                (this.height - this.imageHeight) / 2 + 48
                        )
                        .setSize(12, 14)
                        .setText(Component.literal("!"))
                        .setOnPress(button -> onDiagButtonClicked())
                        .setTooltip(
                                this, font, isReadOnly()
                                            ? MANAGER_GUI_WARNING_BUTTON_TOOLTIP_READ_ONLY
                                            : MANAGER_GUI_WARNING_BUTTON_TOOLTIP
                        )
                        .build()
        );
        updateVisibilities();
    }

    private void onDiagButtonClicked() {

        if (SFMWidgetUtils.hasShiftDown() && !isReadOnly()) {
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
                menu.containerId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_RESET.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void onResetButtonClicked() {

        if (getProgram().isBlank() && LabelPositionHolder.from(menu.getDisk()).isEmpty()) {
            performReset();
            return;
        }
        SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                this::performReset,
                MANAGER_RESET_CONFIRM_SCREEN_TITLE.getComponent(),
                MANAGER_RESET_CONFIRM_SCREEN_MESSAGE.getComponent(),
                MANAGER_RESET_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                MANAGER_RESET_CONFIRM_SCREEN_NO_BUTTON.getComponent(),
                20
        ));
    }

    private void onRebuildButtonClicked() {

        SFMPackets.sendToServer(new ServerboundManagerRebuildPacket(
                menu.containerId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_REBUILD.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void onServerConfigButtonClicked() {

        SFMPackets.sendToServer(new ServerboundServerConfigRequestPacket(ConfigCommandBehaviourInput.SHOW));
    }

    private void sendAttemptFix() {

        SFMPackets.sendToServer(new ServerboundManagerFixPacket(
                menu.containerId,
                menu.MANAGER_POSITION
        ));
        status = MANAGER_GUI_STATUS_FIX.getComponent();
        statusCountdown = STATUS_DURATION;
    }

    private void sendProgram(String program) {

        program = SFMPacketDaddy.truncate(program, Program.MAX_PROGRAM_LENGTH);
        SFMPackets.sendToServer(new ServerboundManagerProgramPacket(
                menu.containerId,
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
                new ConfirmLinkScreen(
                        proceed -> {
                            if (proceed) {
                                Util.getPlatform().openUri(discordUrl);
                            }
                            SFMScreenChangeHelpers.popScreen();
                        },
                        discordUrl,
                        false
                )
        );
    }

    private void onClipboardCopyButtonClicked() {

        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(menu.program);
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
            var disk = menu.CONTAINER.getItem(0);
            if (!(disk.getItem() instanceof DiskItem)) return;
            String diagnosticInfo = SFMDiagnostics.getDiagnosticsSummary(disk);
            Minecraft.getInstance().keyboardHandler.setClipboard(diagnosticInfo);
            status = MANAGER_GUI_STATUS_SAVED_CLIPBOARD.getComponent();
            statusCountdown = STATUS_DURATION;
        } catch (Throwable t) {
            SFM.LOGGER.error("failed saving clipboard", t);
        }
    }

    private void onClipboardPasteButtonClicked() {

        String clipboardContents;
        try {
            clipboardContents = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (Throwable t) {
            SFM.LOGGER.error("failed loading clipboard", t);
            return;
        }
        String existingProgram = getProgram();
        boolean shouldConfirm = !existingProgram.isBlank() && !existingProgram.equals(clipboardContents);
        if (!shouldConfirm) {
            sendProgram(clipboardContents);
            return;
        }
        SFMScreenChangeHelpers.setOrPushScreen(new SFMConfirmationScreen(
                () -> sendProgram(clipboardContents),
                MANAGER_PASTE_CONFIRM_SCREEN_TITLE.getComponent(),
                MANAGER_PASTE_CONFIRM_SCREEN_MESSAGE.getComponent(),
                MANAGER_PASTE_CONFIRM_SCREEN_YES_BUTTON.getComponent(),
                MANAGER_PASTE_CONFIRM_SCREEN_NO_BUTTON.getComponent(),
                20
        ));
    }

    @Override
    protected void extractLabels(
            GuiGraphicsExtractor graphics,
            int mx,
            int my
    ) {
        Matrix3x2fStack poseStack = graphics.pose();        // draw title
        super.extractLabels(graphics, mx, my);

        // draw state string
        var state = menu.state;
        SFMFontUtils.draw(
                graphics,
                this.font,
                MANAGER_GUI_STATE.getComponent(state.LOC.getComponent().withStyle(state.COLOR)),
                titleLabelX,
                20,
                0,
                false
        );

        // draw log level
        if (!menu.logLevel.equals(Level.OFF.name())) {
            poseStack.pushMatrix();
            poseStack.translate(
                    (float)titleLabelX,
                    (float) (font.lineHeight * 1.5)
            );
            poseStack.scale(0.5f, 0.5f);
            SFMFontUtils.draw(
                    graphics,
                    this.font,
                    Component.literal(menu.logLevel),
                    0,
                    0,
                    0,
                    false
            );
            poseStack.popMatrix();
        }

        // draw status string
        if (statusCountdown > 0) {
            SFMFontUtils.draw(
                    graphics,
                    this.font,
                    status,
                    inventoryLabelX + font.width(playerInventoryTitle.getString()) + 5,
                    inventoryLabelY,
                    0,
                    false
            );
        }

        // Find the maximum tick time for normalization
        Duration peakTickTime = Duration.ZERO;
        for (int i = 0; i < menu.tickTimes.length; i++) {
            Duration candidate = menu.tickTimes[i];
            if (candidate.compareTo(peakTickTime) > 0) {
                peakTickTime = candidate;
            }
        }
        long yMax = Long.max(peakTickTime.toNanos(), 50_000_000); // Start with max at 50 ms but allow it to grow

        // Constants for the plot size and position
        final int plotX = titleLabelX + 45;
        final int plotY = 40;
        final int spaceBetweenPoints = 6;
        final int plotWidth = spaceBetweenPoints * (menu.tickTimes.length - 1);
        final int plotHeight = 30;


        // Draw the plot background
        graphics.outline(plotX, plotY, plotWidth, plotHeight, ARGB.color(128, 0, 0, 0));

        // Draw data points
        int mouseTickTimeIndex = -1;
        for (int i = 0; i < menu.tickTimes.length; i++) {
            long y = menu.tickTimes[i].toNanos();
            float normalizedTickTime = y == 0 ? 0 : (float) (Math.log10(y) / Math.log10(yMax));
            int barHeight = (int) (normalizedTickTime * plotHeight);
            int plotPosY = plotY + plotHeight - barHeight;

            int plotPosX = plotX + spaceBetweenPoints * i;

            // Color based on tick times (green to red)
            var c = getMillisecondColour(y / 1_000_000f);
            int color = c.getColor() != null ? ARGB.opaque(c.getColor()) : -1;

            graphics.fill(plotPosX, plotPosY, plotPosX + 1, plotY + plotHeight, color);

            // Check if the mouse is hovering over this data point
            if (mx - leftPos >= plotPosX - spaceBetweenPoints / 2
                && mx - leftPos <= plotPosX + spaceBetweenPoints / 2
                && my - topPos >= plotY - 2
                && my - topPos <= plotY + plotHeight + 2) {
                mouseTickTimeIndex = i;
            }
        }

        // Draw the tick time text
        var format = new DecimalFormat("0.000");
        if (mouseTickTimeIndex != -1) { // We are hovering over the plot
            // Draw the tick time text for the hovered point instead of peak
            {
                long hoveredTickTimeNanoseconds = menu.tickTimes[mouseTickTimeIndex].toNanos();
                var hoveredTickTimeMilliseconds = hoveredTickTimeNanoseconds / 1_000_000f;
                String formattedMillis = format.format(hoveredTickTimeMilliseconds);
                ChatFormatting lagColor = getMillisecondColour(hoveredTickTimeMilliseconds);
                Component milliseconds = Component.literal(formattedMillis).withStyle(lagColor);
                SFMFontUtils.draw(
                        graphics,
                        this.font,
                        MANAGER_GUI_HOVERED_TICK_TIME_MS.getComponent(milliseconds),
                        titleLabelX,
                        20 + font.lineHeight,
                        0,
                        false
                );
            }

            // draw a vertical line
            int x = plotX + spaceBetweenPoints * mouseTickTimeIndex;
            graphics.fill(x, plotY, x + 1, plotY + plotHeight, ARGB.color(255, 255, 255, 255));
        } else {
            // Draw the tick time text for peak value
            var peakTickTimeMilliseconds = peakTickTime.toNanos() / 1_000_000f; // we want decimal precision
            String formattedMillis = format.format(peakTickTimeMilliseconds);
            ChatFormatting lagColor = getMillisecondColour(peakTickTimeMilliseconds);
            Component milliseconds = Component.literal(formattedMillis).withStyle(lagColor);
            SFMFontUtils.draw(
                    graphics,
                    this.font,
                    MANAGER_GUI_PEAK_TICK_TIME_MS.getComponent(milliseconds),
                    titleLabelX,
                    20 + font.lineHeight,
                    0,
                    false
            );
        }
    }

    @Override
    protected void extractTooltip(
            GuiGraphicsExtractor pGuiGraphics,
            int pX,
            int pY
    ) {

        if (Minecraft.getInstance().screen != this) {
            // this should fix the annoying Ctrl+E popup when editing
            this.renderables
                    .stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .forEach(w -> w.setFocused(false));
            return;
        }
        drawChildTooltips(pGuiGraphics, pX, pY);
        // render hovered item
        super.extractTooltip(pGuiGraphics, pX, pY);
    }

    @SuppressWarnings("unused")
    @MCVersionDependentBehaviour
    private void drawChildTooltips(
            GuiGraphicsExtractor guiGraphics,
            int mx,
            int my
    ) {
        // 1.19.2: manually render button tooltips
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks
    ) {
        int color;
        if (!menu.logLevel.equals(Level.OFF.name())) {
            color = ARGB.color(255, (int)(0.2f * 255), (int)(0.8f * 255), 255);
        } else {
            color = -1;
        }
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight, 256, 256, color);
    }

}
