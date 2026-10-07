package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.manager_editor_actions %}
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
{% endif %}
{% when "26.1.2" %}
import ca.teamdman.sfm.client.screen.tick_graph.TickTimeGraphRenderState;
{% endcase %}
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.manager_editor_actions %}
import ca.teamdman.sfm.client.screen.widget.SFMActionButton;
{% endif %}
import ca.teamdman.sfm.client.screen.widget.SFMExtendedButtonWithTooltip;
{% when "1.19.4" %}
{% if features.manager_editor_actions %}
import ca.teamdman.sfm.client.screen.widget.SFMActionButton;
{% endif %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
{% endcase %}
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.Util;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.screens.Screen;
{% endcase %}
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.client.input.KeyEvent;
{% endcase %}
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.renderer.GameRenderer;
{% when "26.1.2" %}
import net.minecraft.client.renderer.RenderPipelines;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
{% endcase %}
import net.minecraft.world.entity.player.Inventory;
import org.apache.logging.log4j.Level;
{% case minecraft_version %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import org.joml.Matrix4f;
{% when "26.1.2" %}
import org.joml.Matrix3x2fStack;
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
{% when "26.1.2" %}
    private static final Identifier BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int pKeyCode,
            int pScanCode,
            int pModifiers
{% when "26.1.2" %}
            KeyEvent event
{% endcase %}
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (Screen.isPaste(pKeyCode) && clipboardPasteButton.visible) {
{% when "26.1.2" %}
        if (event.isPaste() && clipboardPasteButton.visible) {
{% endcase %}
            onClipboardPasteButtonClicked();
            return true;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        } else if (Screen.isCopy(pKeyCode) && clipboardCopyButton.visible) {
{% when "26.1.2" %}
        } else if (event.isCopy() && clipboardCopyButton.visible) {
{% endcase %}
            onClipboardCopyButtonClicked();
            return true;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        } else if (pKeyCode == GLFW.GLFW_KEY_E
                   && Screen.hasControlDown()
                   && Screen.hasShiftDown()
{% when "26.1.2" %}
        } else if (event.key() == GLFW.GLFW_KEY_E
                   && event.hasControlDown()
                   && event.hasShiftDown()
{% endcase %}
                   && examplesButton.visible) {
            onExamplesButtonClicked();
            return true;
        } else if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY)
                   && editButton.visible) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.manager_editor_actions %}
            invokeEditAction();
{% else %}
            onEditButtonClicked();
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            onEditButtonClicked();
{% endcase %}
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
{% when "26.1.2" %}
        return super.keyPressed(event);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public ChatFormatting getMillisecondColour(float ms) {
{% when "26.1.2" %}
    public static ChatFormatting getMillisecondColour(float ms) {
{% endcase %}

        if (ms <= 5) {
            return ChatFormatting.GREEN;
        } else if (ms <= 15) {
            return ChatFormatting.YELLOW;
        } else {
            return ChatFormatting.RED;
        }
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(
            PoseStack poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public void render(
            GuiGraphics graphics,
{% when "26.1.2" %}
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
{% endcase %}
            int mx,
            int my,
            float partialTicks
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this.renderBackground(poseStack);
        super.render(poseStack, mx, my, partialTicks);
        this.renderTooltip(poseStack, mx, my);
{% when "1.20", "1.20.1" %}
        this.renderBackground(graphics);
        super.render(graphics, mx, my, partialTicks);
        this.renderTooltip(graphics, mx, my);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        this.renderTransparentBackground(graphics);
        super.render(graphics, mx, my, partialTicks);
        this.renderTooltip(graphics, mx, my);
{% when "26.1.2" %}
//        this.extractTransparentBackground(graphics);
        super.extractRenderState(graphics, mx, my, partialTicks);
        this.extractTooltip(graphics, mx, my);
{% endcase %}

        updateVisibilities();

        // update status countdown
        statusCountdown -= partialTicks;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @MCVersionDependentBehaviour
    public float getBlitOffsetGood() {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
        return (float) getBlitOffset();
    }

{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return 0F;
    }

{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.manager_editor_actions %}
        editButton = this.addRenderableWidget(new SFMActionButton(
                (this.width - this.imageWidth) / 2 - buttonWidth,
                (this.height - this.imageHeight) / 2 + 16 + 50,
                buttonWidth,
                buttonHeight,
                MANAGER_GUI_EDIT_BUTTON.getComponent(),
                new ResourceLocation(SFM.MOD_ID, "manager/edit"),
                new ResourceLocation(SFM.MOD_ID, "default"),
                () -> MANAGER_GUI_EDIT_BUTTON_TOOLTIP.getComponent(),
                () -> "sfm action invoke sfm:manager/edit",
                button -> invokeEditAction()
        ));
{% else %}
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
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
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (Screen.hasShiftDown() && !isReadOnly()) {
{% when "26.1.2" %}
        if (SFMWidgetUtils.hasShiftDown() && !isReadOnly()) {
{% endcase %}
            sendAttemptFix();
        } else {
            this.onSaveDiagnosticsToClipboard();
        }
    }

    private String getProgram() {

        return menu.program;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.manager_editor_actions %}
    public void openProgramEditorFromAction() {
{% else %}
    private void onEditButtonClicked() {
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private void onEditButtonClicked() {
{% endcase %}

        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                getProgram(),
                LabelPositionHolder.from(menu.getDisk()),
                this::sendProgram
        ));
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.manager_editor_actions %}
    private void invokeEditAction() {
        try {
            SFMClientActionExecutor.execute(
                    "sfm action invoke sfm:manager/edit",
                    SFMClientActionContext.create(this, () -> Minecraft.getInstance().screen == this),
                    message -> SFM.LOGGER.warn("Manager edit action feedback: {}", message.getString()));
        } catch (Exception exception) {
            SFM.LOGGER.error("Manager edit action failed", exception);
        }
    }

{% endif %}
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2" %}
    @MCVersionDependentBehaviour
    private void disableTexture() {

        RenderSystem.disableTexture();
    }

{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @MCVersionDependentBehaviour
    private void disableTexture() {

//        RenderSystem.disableTexture(); // 1.19.2
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    protected void renderLabels(
            PoseStack poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    protected void renderLabels(
            GuiGraphics graphics,
{% when "26.1.2" %}
    protected void extractLabels(
            GuiGraphicsExtractor graphics,
{% endcase %}
            int mx,
            int my
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        // draw title
        super.renderLabels(poseStack, mx, my);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        PoseStack poseStack = graphics.pose();        // draw title
        super.renderLabels(graphics, mx, my);
{% when "26.1.2" %}
        Matrix3x2fStack poseStack = graphics.pose();        // draw title
        super.extractLabels(graphics, mx, my);
{% endcase %}

        // draw state string
        var state = menu.state;
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                graphics,
{% endcase %}
                this.font,
                MANAGER_GUI_STATE.getComponent(state.LOC.getComponent().withStyle(state.COLOR)),
                titleLabelX,
                20,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                0,
{% when "26.1.2" %}
                0xFF000000,
{% endcase %}
                false
        );

        // draw log level
        if (!menu.logLevel.equals(Level.OFF.name())) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            poseStack.pushPose();
{% when "26.1.2" %}
            poseStack.pushMatrix();
{% endcase %}
            poseStack.translate(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    titleLabelX,
                    font.lineHeight * 1.5,
                    0f
{% when "26.1.2" %}
                    (float)titleLabelX,
                    (float) (font.lineHeight * 1.5)
{% endcase %}
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            poseStack.scale(0.5f, 0.5f, 1f);
{% when "26.1.2" %}
            poseStack.scale(0.5f, 0.5f);
{% endcase %}
            SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                    graphics,
{% endcase %}
                    this.font,
                    Component.literal(menu.logLevel),
                    0,
                    0,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    0,
{% when "26.1.2" %}
                    0xFF000000,
{% endcase %}
                    false
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            poseStack.popPose();
{% when "26.1.2" %}
            poseStack.popMatrix();
{% endcase %}
        }

        // draw status string
        if (statusCountdown > 0) {
            SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                    graphics,
{% endcase %}
                    this.font,
                    status,
                    inventoryLabelX + font.width(playerInventoryTitle.getString()) + 5,
                    inventoryLabelY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    0,
{% when "26.1.2" %}
                    0xFF000000,
{% endcase %}
                    false
            );
        }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        // Find the maximum tick time for normalization
        Duration peakTickTime = Duration.ZERO;
        for (int i = 0; i < menu.tickTimes.length; i++) {
            Duration candidate = menu.tickTimes[i];
{% if features.tick_graph_null_safety %}
            if (candidate != null && candidate.compareTo(peakTickTime) > 0) {
{% else %}
            if (candidate.compareTo(peakTickTime) > 0) {
{% endif %}
                peakTickTime = candidate;
            }
        }
        long yMax = Long.max(peakTickTime.toNanos(), 50_000_000); // Start with max at 50 ms but allow it to grow
{% endcase %}

        // Constants for the plot size and position
        final int plotX = titleLabelX + 45;
        final int plotY = 40;
        final int spaceBetweenPoints = 6;
        final int plotWidth = spaceBetweenPoints * (menu.tickTimes.length - 1);
        final int plotHeight = 30;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

        // Set up rendering
        disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f pose = poseStack.last().pose();
        BufferBuilder bufferbuilder;

        // Draw the plot background
        bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(pose, plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX + plotWidth, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX + plotWidth, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        tesselator.end();

        // Draw lines for each data point
        bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}

        // Set up rendering
        disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder bufferbuilder;

        // Draw the plot background
        bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(pose, plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX + plotWidth, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX + plotWidth, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX, plotY + plotHeight, 0).color(0, 0, 0, 0.5f).endVertex();
        bufferbuilder.vertex(pose, plotX, plotY, 0).color(0, 0, 0, 0.5f).endVertex();
        tesselator.end();

        // Draw lines for each data point
        bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
{% when "1.21", "1.21.1" %}

        // Set up rendering
        disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder bufferbuilder;


        // Draw the plot background
        bufferbuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.addVertex(pose, plotX, plotY, 0).setColor(0, 0, 0, 0.5f);
        bufferbuilder.addVertex(pose, plotX + plotWidth, plotY, 0).setColor(0, 0, 0, 0.5f);
        bufferbuilder.addVertex(pose, plotX + plotWidth, plotY + plotHeight, 0).setColor(0, 0, 0, 0.5f);
        bufferbuilder.addVertex(pose, plotX, plotY + plotHeight, 0).setColor(0, 0, 0, 0.5f);
        bufferbuilder.addVertex(pose, plotX, plotY, 0).setColor(0, 0, 0, 0.5f);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        // Draw lines for each data point
        bufferbuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
{% endcase %}
        int mouseTickTimeIndex = -1;
{% case minecraft_version %}
{% when "26.1.2" %}

        // Find the maximum tick time for normalization
        Duration peakTickTime = Duration.ZERO;
{% endcase %}
        for (int i = 0; i < menu.tickTimes.length; i++) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.tick_graph_null_safety %}
            Duration tickTime = menu.tickTimes[i];
            if (tickTime == null) {
                continue;
            }
            long y = tickTime.toNanos();
{% else %}
            long y = menu.tickTimes[i].toNanos();
{% endif %}
            float normalizedTickTime = y == 0 ? 0 : (float) (Math.log10(y) / Math.log10(yMax));
            int plotPosY = plotY + plotHeight - (int) (normalizedTickTime * plotHeight);
{% when "26.1.2" %}
            Duration candidate = menu.tickTimes[i];
{% if features.tick_graph_null_safety %}
            if (candidate != null && candidate.compareTo(peakTickTime) > 0) {
{% else %}
            if (candidate.compareTo(peakTickTime) > 0) {
{% endif %}
                peakTickTime = candidate;
            }
{% endcase %}

            int plotPosX = plotX + spaceBetweenPoints * i;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            // Color the lines based on their tick times (green to red)
            var c = getMillisecondColour(y / 1_000_000f);
            //noinspection DataFlowIssue
            float red = ((c.getColor() >> 16) & 0xFF) / 255f;
            float green = ((c.getColor() >> 8) & 0xFF) / 255f;
            float blue = (c.getColor() & 0xFF) / 255f;

            bufferbuilder
                    .vertex(pose, (float) plotPosX, (float) plotPosY, getBlitOffsetGood())
                    .color(red, green, blue, 1f)
                    .endVertex();

            // Check if the mouse is hovering over this line
            if (mx - leftPos >= plotPosX - spaceBetweenPoints / 2
                && mx - leftPos <= plotPosX + spaceBetweenPoints / 2
                && my - topPos >= plotY - 2
                && my - topPos <= plotY + plotHeight + 2) {
{% when "1.21", "1.21.1" %}
            // Color the lines based on their tick times (green to red)
            var c = getMillisecondColour(y / 1_000_000f);
            //noinspection DataFlowIssue
            float red = ((c.getColor() >> 16) & 0xFF) / 255f;
            float green = ((c.getColor() >> 8) & 0xFF) / 255f;
            float blue = (c.getColor() & 0xFF) / 255f;

            bufferbuilder
                    .addVertex(pose, (float) plotPosX, (float) plotPosY, getBlitOffsetGood())
                    .setColor(red, green, blue, 1f);

            // Check if the mouse is hovering over this line
            if (mx - leftPos >= plotPosX - spaceBetweenPoints / 2
                && mx - leftPos <= plotPosX + spaceBetweenPoints / 2
                && my - topPos >= plotY - 2
                && my - topPos <= plotY + plotHeight + 2) {
{% when "26.1.2" %}
{% if features.tick_graph_null_safety %}
            if (candidate != null
                && mx - leftPos >= plotPosX - spaceBetweenPoints / 2
                && mx - leftPos <= plotPosX + spaceBetweenPoints / 2
                && my - topPos >= plotY - 2
                && my - topPos <= plotY + plotHeight + 2) {
{% else %}
            if (mx - leftPos >= plotPosX - spaceBetweenPoints / 2
                    && mx - leftPos <= plotPosX + spaceBetweenPoints / 2
                    && my - topPos >= plotY - 2
                    && my - topPos <= plotY + plotHeight + 2) {
{% endif %}
{% endcase %}
                mouseTickTimeIndex = i;
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        tesselator.end();
{% when "1.21", "1.21.1" %}
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
{% when "26.1.2" %}
        long yMax = Long.max(peakTickTime.toNanos(), 50_000_000); // Start with max at 50 ms but allow it to grow
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        // Draw the plot background
        graphics.outline(plotX, plotY, plotWidth, plotHeight, ARGB.color(128, 0, 0, 0));
        var tickTimeGraph = new TickTimeGraphRenderState(
                menu.tickTimes,
                plotX + leftPos,
                plotY + topPos,
                plotWidth * guiScale,
                plotHeight * guiScale,
                yMax,
                spaceBetweenPoints * guiScale,
                graphics.peekScissorStack()
        );
        graphics.submitPictureInPictureRenderState(tickTimeGraph);

{% endcase %}
        // Draw the tick time text
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.tick_graph_null_safety %}
        var format = new DecimalFormat("0.000"); // TODO: this should respect the user's locale
{% else %}
        var format = new DecimalFormat("0.000");
{% endif %}
{% when "26.1.2" %}
        var format = new DecimalFormat("0.000");
{% endcase %}
        if (mouseTickTimeIndex != -1) { // We are hovering over the plot
            // Draw the tick time text for the hovered point instead of peak
            {
                long hoveredTickTimeNanoseconds = menu.tickTimes[mouseTickTimeIndex].toNanos();
                var hoveredTickTimeMilliseconds = hoveredTickTimeNanoseconds / 1_000_000f;
                String formattedMillis = format.format(hoveredTickTimeMilliseconds);
                ChatFormatting lagColor = getMillisecondColour(hoveredTickTimeMilliseconds);
                Component milliseconds = Component.literal(formattedMillis).withStyle(lagColor);
                SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                        graphics,
{% endcase %}
                        this.font,
                        MANAGER_GUI_HOVERED_TICK_TIME_MS.getComponent(milliseconds),
                        titleLabelX,
                        20 + font.lineHeight,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                        0,
{% when "26.1.2" %}
                        0xFF000000,
{% endcase %}
                        false
                );
            }

            // draw a vertical line
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            tesselator = Tesselator.getInstance();
            bufferbuilder = tesselator.getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            pose = poseStack.last().pose();

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            tesselator = Tesselator.getInstance();
            bufferbuilder = tesselator.getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            pose = graphics.pose().last().pose();

{% when "1.21", "1.21.1" %}
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            tesselator = Tesselator.getInstance();
            bufferbuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            pose = graphics.pose().last().pose();

{% endcase %}
            int x = plotX + spaceBetweenPoints * mouseTickTimeIndex;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            bufferbuilder
                    .vertex(pose, (float) x, (float) plotY, getBlitOffsetGood())
                    .color(1f, 1f, 1f, 1f)
                    .endVertex();
            bufferbuilder
                    .vertex(pose, (float) x, (float) plotY + plotHeight, getBlitOffsetGood())
                    .color(1f, 1f, 1f, 1f)
                    .endVertex();
            tesselator.end();
{% when "1.21", "1.21.1" %}
            bufferbuilder
                    .addVertex(pose, (float) x, (float) plotY, getBlitOffsetGood())
                    .setColor(1f, 1f, 1f, 1f);
            bufferbuilder
                    .addVertex(pose, (float) x, (float) plotY + plotHeight, getBlitOffsetGood())
                    .setColor(1f, 1f, 1f, 1f);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
{% when "26.1.2" %}
            graphics.fill(x, plotY, x + 1, plotY + plotHeight, ARGB.color(255, 255, 255, 255));
{% endcase %}
        } else {
            // Draw the tick time text for peak value
            var peakTickTimeMilliseconds = peakTickTime.toNanos() / 1_000_000f; // we want decimal precision
            String formattedMillis = format.format(peakTickTimeMilliseconds);
            ChatFormatting lagColor = getMillisecondColour(peakTickTimeMilliseconds);
            Component milliseconds = Component.literal(formattedMillis).withStyle(lagColor);
            SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                    graphics,
{% endcase %}
                    this.font,
                    MANAGER_GUI_PEAK_TICK_TIME_MS.getComponent(milliseconds),
                    titleLabelX,
                    20 + font.lineHeight,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    0,
{% when "26.1.2" %}
                    0xFF000000,
{% endcase %}
                    false
            );
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}

        // Restore stuff
        RenderSystem.disableBlend();
        enableTexture();
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2" %}
    @MCVersionDependentBehaviour
    private void enableTexture() {

        RenderSystem.enableTexture();
    }

{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @MCVersionDependentBehaviour
    private void enableTexture() {
//        RenderSystem.enableTexture(); // 1.19.2
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    protected void renderTooltip(
            PoseStack pose,
            int mx,
            int my
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    protected void renderTooltip(
            GuiGraphics pGuiGraphics,
            int pX,
            int pY
{% when "26.1.2" %}
    protected void extractTooltip(
            GuiGraphicsExtractor pGuiGraphics,
            int pX,
            int pY
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        drawChildTooltips(pose, mx, my);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        drawChildTooltips(pGuiGraphics, pX, pY);
{% endcase %}
        // render hovered item
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        super.renderTooltip(pose, mx, my);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.renderTooltip(pGuiGraphics, pX, pY);
{% when "26.1.2" %}
        super.extractTooltip(pGuiGraphics, pX, pY);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @SuppressWarnings("unused")
{% endcase %}
    @MCVersionDependentBehaviour
    private void drawChildTooltips(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack pose,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics guiGraphics,
{% when "26.1.2" %}
            GuiGraphicsExtractor guiGraphics,
{% endcase %}
            int mx,
            int my
    ) {
        // 1.19.2: manually render button tooltips
{% case minecraft_version %}
{% when "1.19.2" %}
        this.renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    protected void renderBg(
            PoseStack matrixStack,
            float partialTicks,
            int mx,
            int my
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    protected void renderBg(
            GuiGraphics graphics,
            float partialTicks,
            int mx,
            int my
{% when "26.1.2" %}
    public void extractTransparentBackground(
            GuiGraphicsExtractor graphics
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
{% when "26.1.2" %}
        super.extractTransparentBackground(graphics);
        int color;
{% endcase %}
        if (!menu.logLevel.equals(Level.OFF.name())) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            RenderSystem.setShaderColor(0.2f, 0.8f, 1f, 1f);
{% when "26.1.2" %}
            color = ARGB.color(255, (int)(0.2f * 255), (int)(0.8f * 255), 255);
{% endcase %}
        } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
{% when "26.1.2" %}
            color = -1;
{% endcase %}
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE_LOCATION);
{% endcase %}
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
{% case minecraft_version %}
{% when "1.19.2" %}
        blit(matrixStack, i, j, 0, 0, this.imageWidth, this.imageHeight);
{% when "1.19.4" %}
        //noinspection SuspiciousNameCombination
        blit(matrixStack, i, j, 0, 0, this.imageWidth, this.imageHeight);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        graphics.blit(BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
{% when "26.1.2" %}
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight, 256, 256, color);
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}

{% endcase %}
}
