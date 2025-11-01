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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.lwjgl.input.Mouse;

import java.util.*;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_EDIT_SCREEN_DONE_BUTTON_TOOLTIP;

// todo: checkbox for auto-scrolling
public class LogsScreen extends GuiScreen {
    private final ManagerContainerMenu MENU;
    private MyMultiLineEditBox textarea;
    private List<ITextComponent> content = Collections.emptyList();
    private int lastSize = 0;
    private Map<Level, GuiButton> levelButtons = new HashMap<>();
    private String lastKnownLogLevel;


    public LogsScreen(ManagerContainerMenu menu) {
        super();
        this.MENU = menu;
        this.lastKnownLogLevel = MENU.logLevel;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean shouldRebuildText() {
        return MENU.logs.size() != lastSize;
//        return false;
    }

    private void rebuildText() {
        List<ITextComponent> processedLogs = new ArrayList<>();
        var toProcess = MENU.logs;
        if (toProcess.isEmpty() && MENU.logLevel.equals(Level.OFF.name())) {
            MutableInstant instant = new MutableInstant();
            instant.initFromEpochMilli(System.currentTimeMillis());
            toProcess.add(new TranslatableLogEvent(
                    Level.INFO,
                    instant,
                    new TextComponentString(LocalizationKeys.LOGS_GUI_NO_CONTENT.get())
            ));
        }
        for (TranslatableLogEvent log : toProcess) {
            int seconds = (int) (System.currentTimeMillis() - log.instant().getEpochMillisecond()) / 1000;
            int minutes = seconds / 60;
            seconds = seconds % 60;
            var ago = new TextComponentString(minutes + "m" + seconds + "s ago").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GRAY));

            var level = new TextComponentString(" [" + log.level() + "] ");
            if (log.level() == Level.ERROR) {
                level.getStyle().setColor(TextFormatting.RED);
            } else if (log.level() == Level.WARN) {
                level.getStyle().setColor(TextFormatting.YELLOW);
            } else if (log.level() == Level.INFO) {
                level.getStyle().setColor(TextFormatting.GREEN);
            } else if (log.level() == Level.DEBUG) {
                level.getStyle().setColor(TextFormatting.AQUA);
            } else if (log.level() == Level.TRACE) {
                level.getStyle().setColor(TextFormatting.DARK_GRAY);
            }

            String[] lines = ClientTranslationHelpers.resolveTranslation(log.contents()).split("\n", -1);

            StringBuilder codeBlock = new StringBuilder();
            boolean insideCodeBlock = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                ITextComponent lineComponent;

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
                    lineComponent = new TextComponentString(line).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.WHITE));
                    if (i == 0) {
                        lineComponent = ago
                                .appendSibling(level)
                                .appendSibling(lineComponent);
                    }
                    processedLogs.add(lineComponent);
                }
            }
        }
        this.content = processedLogs;


        // update textarea with plain string contents so select and copy works
        StringBuilder sb = new StringBuilder();
        for (var line : this.content) {
            sb.append(line.getUnformattedText()).append("\n");
        }
        textarea.setText(sb.toString());
        lastSize = MENU.logs.size();
    }

    public boolean isReadOnly() {
        return mc.player == null || mc.player.isSpectator();
    }

    public void onLogLevelChange() {
        // disable buttons that equal the current level
        for (var entry : levelButtons.entrySet()) {
            var level = entry.getKey();
            var button = entry.getValue();
            button.enabled = !MENU.logLevel.equals(level.name());
        }
        lastKnownLogLevel = MENU.logLevel;
    }

    @Override
    public void initGui() {
        super.initGui();
        assert this.mc != null;
        this.textarea = new MyMultiLineEditBox();

        rebuildText();


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
            GuiButton levelButton = new SFMButtonBuilder()
                    .setSize(buttonWidth, buttonHeight)
                    .setPosition(
                            startX + (buttonWidth + spacing) * buttonIndex,
                            startY
                    )
                    .setText(level.name())
                    .setOnPress(button -> {
                        String logLevel = level.name();
                        SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundManagerSetLogLevelPacket(
                                MENU.windowId,
                                MENU.MANAGER_POSITION,
                                logLevel
                        ));
                        MENU.logLevel = logLevel;
                        onLogLevelChange();
                    })
                    .build(this.buttonList.size());
            levelButtons.put(level, levelButton);
            this.buttonList.add(levelButton);
            buttonIndex++;
        }
        onLogLevelChange();


        this.buttonList.add(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 200, this.height / 2 - 100 + 195)
                        .setSize(80, 20)
                        .setText(LocalizationKeys.LOGS_GUI_COPY_LOGS_BUTTON.get())
                        .setOnPress(this::onCopyLogsClicked)
                        .build(this.buttonList.size())
        );
        this.buttonList.add(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 100, this.height / 2 - 100 + 195)
                        .setSize(200, 20)
                        .setText("Done")
                        .setOnPress((p_97691_) -> this.onClose())
                        .build(this.buttonList.size())
        );
        if (!isReadOnly()) {
            this.buttonList.add(
                    new SFMButtonBuilder()
                            .setPosition(this.width / 2 + 115, this.height / 2 - 100 + 195)
                            .setSize(80, 20)
                            .setText(LocalizationKeys.LOGS_GUI_CLEAR_LOGS_BUTTON.get())
                            .setOnPress((button) -> {
                                SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundManagerClearLogsPacket(
                                        MENU.windowId,
                                        MENU.MANAGER_POSITION
                                ));
                                MENU.logs.clear();
                            })
                            .build(this.buttonList.size())
            );
        }
    }

    private void onCopyLogsClicked(GuiButton button) {
        StringBuilder clip = new StringBuilder();
        clip.append(SFMDiagnostics.getDiagnosticsSummary(
                MENU.getDisk()
        ));
        clip.append("\n-- LOGS --\n");
        if (isShiftKeyDown()) {
            for (TranslatableLogEvent log : MENU.logs) {
                clip.append(log.level().name()).append(" ");
                clip.append(log.instant().toString()).append(" ");
                clip.append(log.contents().getUnformattedText());
                clip.append("\n");
            }
        } else {
            for (ITextComponent line : content) {
                clip.append(line.getUnformattedText()).append("\n");
            }
        }
        setClipboardString(clip.toString());
    }

    @Override
    public void onGuiClosed() {
        SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundManagerLogDesireUpdatePacket(
                MENU.windowId,
                MENU.MANAGER_POSITION,
                false
        ));
        super.onGuiClosed();
    }

    public void scrollToBottom() {
        textarea.scrollToBottom();
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        this.drawDefaultBackground();
        textarea.drawTextBox();
        super.drawScreen(mx, my, partialTicks);
        if (!MENU.logLevel.equals(lastKnownLogLevel)) {
            onLogLevelChange();
        }
    }

    // TODO: enable scrolling without focus
    private class MyMultiLineEditBox {
        private int scrollAmount;
        private String text;

        public MyMultiLineEditBox() {
        }

        public void scrollToBottom() {
            setScrollAmount(Integer.MAX_VALUE);
        }

        public void setText(String text) {
            this.text = text;
        }

        public void drawTextBox() {
            if (shouldRebuildText()) {
                rebuildText();
            }

            int x = LogsScreen.this.width / 2 - 200;
            int y = LogsScreen.this.height / 2 - 90;
            int width = 400;
            int height = 180;

            drawRect(x, y, x + width, y + height, 0xFF000000);

            int scrollY = this.scrollAmount;
            int lineY = y + 5 - scrollY;

            for (int i = 0; i < content.size(); i++) {
                if (lineY > y && lineY < y + height) {
                    LogsScreen.this.fontRenderer.drawString(content.get(i).getFormattedText(), x + 5, lineY, 0xFFFFFF);
                }
                lineY += LogsScreen.this.fontRenderer.FONT_HEIGHT;
            }
        }

        public void handleMouseInput() {
            int i = Mouse.getEventDWheel();
            if (i != 0) {
                if (i > 0) {
                    i = -1;
                } else {
                    i = 1;
                }
                this.scrollAmount += i * LogsScreen.this.fontRenderer.FONT_HEIGHT;
                if (this.scrollAmount < 0) {
                    this.scrollAmount = 0;
                }
            }
        }

        public void setScrollAmount(int scrollAmount) {
            this.scrollAmount = scrollAmount;
        }
    }
}