package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ClientTranslationHelpers;
import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.diagnostics.SFMDiagnostics;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.net.ServerboundManagerClearLogsPacket;
import ca.teamdman.sfm.common.net.ServerboundManagerLogDesireUpdatePacket;
import ca.teamdman.sfm.common.net.ServerboundManagerSetLogLevelPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.joml.Matrix4f;

import java.util.*;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP;

// todo: checkbox for auto-scrolling
public class LogsScreen extends Screen {
    private final ManagerContainerMenu MENU;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private MyMultiLineEditBox textarea;
    private List<MutableComponent> content = Collections.emptyList();
    private int lastSize = 0;
    private Map<Level,Button> levelButtons = new HashMap<>();
    private String lastKnownLogLevel;


    public LogsScreen(ManagerContainerMenu menu) {
        super(LocalizationKeys.LOGS_SCREEN_TITLE.getComponent());
        this.MENU = menu;
        this.lastKnownLogLevel = MENU.logLevel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean shouldRebuildText() {
        return MENU.logs.size() != lastSize;
//        return false;
    }

    private void rebuildText() {
        List<MutableComponent> processedLogs = new ArrayList<>();
        var toProcess = MENU.logs;
        if (toProcess.isEmpty() && MENU.logLevel.equals(Level.OFF.name())) {
            MutableInstant instant = new MutableInstant();
            instant.initFromEpochMilli(System.currentTimeMillis(), 0);
            toProcess.add(new TranslatableLogEvent(
                    Level.INFO,
                    instant,
                    LocalizationKeys.LOGS_GUI_NO_CONTENT.get()
            ));
        }
        for (TranslatableLogEvent log : toProcess) {
            int seconds = (int) (System.currentTimeMillis() - log.instant().getEpochMillisecond()) / 1000;
            int minutes = seconds / 60;
            seconds = seconds % 60;
            var ago = Component.literal(minutes + "m" + seconds + "s ago").withStyle(ChatFormatting.GRAY);

            var level = Component.literal(" [" + log.level() + "] ");
            if (log.level() == Level.ERROR) {
                level = level.withStyle(ChatFormatting.RED);
            } else if (log.level() == Level.WARN) {
                level = level.withStyle(ChatFormatting.YELLOW);
            } else if (log.level() == Level.INFO) {
                level = level.withStyle(ChatFormatting.GREEN);
            } else if (log.level() == Level.DEBUG) {
                level = level.withStyle(ChatFormatting.AQUA);
            } else if (log.level() == Level.TRACE) {
                level = level.withStyle(ChatFormatting.DARK_GRAY);
            }

            String[] lines = ClientTranslationHelpers.resolveTranslation(log.contents()).split("\n", -1);

            StringBuilder codeBlock = new StringBuilder();
            boolean insideCodeBlock = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                MutableComponent lineComponent;

                if (line.equals("```")) {
                    if (insideCodeBlock) {
                        // output processed code
                        var codeLines = ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                                codeBlock.toString(),
                                false
                        );
                        processedLogs.addAll(codeLines);
                        codeBlock = new StringBuilder();
                    } else {
                        // begin tracking code
                        insideCodeBlock = true;
                    }
                } else if (insideCodeBlock) {
                    codeBlock.append(line).append("\n");
                } else {
                    lineComponent = Component.literal(line).withStyle(ChatFormatting.WHITE);
                    if (i == 0) {
                        lineComponent = ago
                                .append(level)
                                .append(lineComponent);
                    }
                    processedLogs.add(lineComponent);
                }
            }
        }
        this.content = processedLogs;


        // update textarea with plain string contents so select and copy works
        StringBuilder sb = new StringBuilder();
        for (var line : this.content) {
            sb.append(line.getString()).append("\n");
        }
        textarea.setValue(sb.toString());
        lastSize = MENU.logs.size();
    }

    public boolean isReadOnly() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || player.isSpectator();
    }

    public void onLogLevelChange() {
        // disable buttons that equal the current level
        for (var entry : levelButtons.entrySet()) {
            var level = entry.getKey();
            var button = entry.getValue();
            button.active = !MENU.logLevel.equals(level.name());
        }
        lastKnownLogLevel = MENU.logLevel;
    }

    @Override
    protected void init() {
        super.init();
        assert this.minecraft != null;
        this.textarea = this.addRenderableWidget(new MyMultiLineEditBox());

        rebuildText();

        this.setInitialFocus(textarea);


        var buttons = isReadOnly() ? new Level[]{} : new Level[]{
                Level.OFF,
                Level.TRACE,
                Level.DEBUG,
                Level.INFO,
                Level.WARN,
                Level.ERROR
        };
        int buttonWidth = 60;
        int buttonHeight = 20;
        int spacing = 5;
        int startX = (this.width - (buttonWidth * buttons.length + spacing * 4)) / 2;
        int startY = this.height / 2 - 115;
        int buttonIndex = 0;

        this.levelButtons = new HashMap<>();
        for (var level : buttons) {
            Button levelButton = new SFMButtonBuilder()
                    .setSize(buttonWidth, buttonHeight)
                    .setPosition(
                            startX + (buttonWidth + spacing) * buttonIndex,
                            startY
                    )
                    .setText(Component.literal(level.name()))
                    .setOnPress(button -> {
                        String logLevel = level.name();
                        SFMPackets.sendToServer(new ServerboundManagerSetLogLevelPacket(
                                MENU.containerId,
                                MENU.MANAGER_POSITION,
                                logLevel
                        ));
                        MENU.logLevel = logLevel;
                        onLogLevelChange();
                    })
                    .build();
            levelButtons.put(level, levelButton);
            this.addRenderableWidget(levelButton);
            buttonIndex++;
        }
        onLogLevelChange();


        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
                        .setSize(80, 20)
                        .setText(LocalizationKeys.LOGS_GUI_COPY_LOGS_BUTTON)
                        .setOnPress(this::onCopyLogsClicked)
                        .setTooltip(this, font, LocalizationKeys.LOGS_GUI_COPY_LOGS_BUTTON_TOOLTIP)
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 2 - 100, this.height / 2 - 100 + 195)
                        .setSize(200, 20)
                        .setText(CommonComponents.GUI_DONE)
                        .setOnPress((p_97691_) -> this.onClose())
                        .setTooltip(this, font, PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP)
                        .build()
        );
        if (!isReadOnly()) {
            this.addRenderableWidget(
                    new SFMButtonBuilder()
                            .setPosition(this.width / 2 - 2 + 115, this.height / 2 - 100 + 195)
                            .setSize(80, 20)
                            .setText(LocalizationKeys.LOGS_GUI_CLEAR_LOGS_BUTTON)
                            .setOnPress((button) -> {
                                SFMPackets.sendToServer(new ServerboundManagerClearLogsPacket(
                                        MENU.containerId,
                                        MENU.MANAGER_POSITION
                                ));
                                MENU.logs.clear();
                            })
                            .build()
            );
        }
    }

    private void onCopyLogsClicked(Button button) {
        StringBuilder clip = new StringBuilder();
        clip.append(SFMDiagnostics.getDiagnosticsSummary(
                MENU.getDisk()
        ));
        clip.append("\n-- LOGS --\n");
        if (hasShiftDown()) {
            for (TranslatableLogEvent log : MENU.logs) {
                clip.append(log.level().name()).append(" ");
                clip.append(log.instant().toString()).append(" ");
                clip.append(log.contents().getKey());
                for (Object arg : log.contents().getArgs()) {
                    clip.append(" ").append(arg);
                }
                clip.append("\n");
            }
        } else {
            for (MutableComponent line : content) {
                clip.append(line.getString()).append("\n");
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(clip.toString());
    }

    @Override
    public void onClose() {
        SFMPackets.sendToServer(new ServerboundManagerLogDesireUpdatePacket(
                MENU.containerId,
                MENU.MANAGER_POSITION,
                false
        ));
        super.onClose();
    }

    public void scrollToBottom() {
        textarea.scrollToBottom();
    }

    @Override
    public void resize(Minecraft mc, int x, int y) {
        var prev = this.textarea.getValue();
        init(mc, x, y);
        super.resize(mc, x, y);
        this.textarea.setValue(prev);
    }

    @Override
    public void render(PoseStack poseStack, int mx, int my, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mx, my, partialTicks);
        if (!MENU.logLevel.equals(lastKnownLogLevel)) {
            onLogLevelChange();
        }
    }

    // TODO: enable scrolling without focus
    private class MyMultiLineEditBox extends MultiLineEditBox {
        public MyMultiLineEditBox() {
            super(
                    LogsScreen.this.font,
                    LogsScreen.this.width / 2 - 200,
                    LogsScreen.this.height / 2 - 90,
                    400,
                    180,
                    Component.literal(""),
                    Component.literal("")
            );
        }

        public void scrollToBottom() {
            setScrollAmount(Double.MAX_VALUE);
        }

        @Override
        public void setValue(String p_240160_) {
//            var cursorListener = textField::scro
            this.textField.setValue(p_240160_);
//            setCursorPosition(cursor);
        }

        @Override
        public boolean mouseClicked(double p_239101_, double p_239102_, int p_239103_) {
            try {
                return super.mouseClicked(p_239101_, p_239102_, p_239103_);
            } catch (Exception e) {
                SFM.LOGGER.error("Error in LogsScreen.MyMultiLineEditBox.mouseClicked", e);
                return false;
            }
        }

        @Override
        public int getInnerHeight() {
            // parent method uses this.textField.getLineCount() which is split for text wrapping
            // we don't use the wrapped text, so we need to calculate the height ourselves to avoid overshooting
            return this.font.lineHeight * (content.size() + 2);
        }

        @Override
        protected void renderContents(PoseStack poseStack, int mx, int my, float partialTicks) {
            Matrix4f matrix4f = poseStack.last().pose();
            if (shouldRebuildText()) {
                rebuildText();
            }
            boolean isCursorVisible = this.isFocused() && this.frame / 6 % 2 == 0;
            boolean isCursorAtEndOfLine = false;
            int cursorIndex = textField.cursor();
            int lineX = SFMScreenRenderUtils.getX(this) + this.innerPadding();
            int lineY = SFMScreenRenderUtils.getY(this) + this.innerPadding();
            int charCount = 0;
            int cursorX = 0;
            int cursorY = 0;
            MultilineTextField.StringView selectedRange = this.textField.getSelected();
            int selectionStart = selectedRange.beginIndex();
            int selectionEnd = selectedRange.endIndex();

//            for (int line = 0; line < content.size(); ++line) {
            // draw the last 500 lines
            for (int line = Math.max(0, content.size() - 500); line < content.size(); ++line) {
                var componentColoured = content.get(line);
                int lineLength = componentColoured.getString().length();
                int lineHeight = this.font.lineHeight + (line == 0 ? 2 : 0);
                boolean cursorOnThisLine = isCursorVisible
                                           && cursorIndex >= charCount
                                           && cursorIndex <= charCount + lineLength;
                var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

                if (cursorOnThisLine) {
                    isCursorAtEndOfLine = cursorIndex == charCount + lineLength;
                    cursorY = lineY;
                    // we draw the raw before coloured in case of token recognition errors
                    // draw before cursor
                    cursorX = SFMFontUtils.drawInBatch(
                            SFMTextEditScreenV1.substring(componentColoured, 0, cursorIndex - charCount),
                            font,
                            lineX,
                            lineY,
                            true,
                            false
                    ) - 1;
                    SFMFontUtils.drawInBatch(
                            SFMTextEditScreenV1.substring(componentColoured, cursorIndex - charCount, lineLength),
                            font,
                            cursorX,
                            lineY,
                            true,
                            false
                    );
                } else {
                    SFMFontUtils.drawInBatch(
                            componentColoured,
                            font,
                            lineX,
                            lineY,
                            true,
                            false
                    );
                }
                buffer.endBatch();

                // Check if the selection is within the current line
                if (selectionStart <= charCount + lineLength && selectionEnd > charCount) {
                    int lineSelectionStart = Math.max(selectionStart - charCount, 0);
                    int lineSelectionEnd = Math.min(selectionEnd - charCount, lineLength);

                    int highlightStartX = this.font.width(SFMTextEditScreenV1.substring(
                            componentColoured,
                            0,
                            lineSelectionStart
                    ));
                    int highlightEndX = this.font.width(SFMTextEditScreenV1.substring(
                            componentColoured,
                            0,
                            lineSelectionEnd
                    ));

                    SFMScreenRenderUtils.renderHighlight(
                            poseStack,
                            lineX + highlightStartX,
                            lineY,
                            lineX + highlightEndX,
                            lineY + lineHeight
                    );
                }

                lineY += lineHeight;
                charCount += lineLength + 1;
            }

            if (isCursorAtEndOfLine) {
                SFMFontUtils.draw(
                        poseStack,
                        this.font,
                        "_",
                        cursorX,
                        cursorY,
                        -1,
                        true
                );
            } else {
                GuiComponent.fill(poseStack, cursorX, cursorY - 1, cursorX + 1, cursorY + 1 + 9, -1);
            }
        }
    }
}

