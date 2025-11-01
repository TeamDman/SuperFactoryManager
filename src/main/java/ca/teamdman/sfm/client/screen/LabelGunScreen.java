package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundLabelGunClearPacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunCycleViewModePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunPrunePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumHand;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LabelGunScreen extends GuiScreen {
    private final EnumHand HAND;
    private final LabelPositionHolder LABEL_HOLDER;
    private final ArrayList<GuiButton> labelButtons = new ArrayList<>();
    private GuiTextField labelField;
    private boolean shouldRebuildWidgets = false;

    public LabelGunScreen(ItemStack labelGunStack, EnumHand hand) {
        super();
        LABEL_HOLDER = LabelPositionHolder.from(labelGunStack);
        HAND = hand;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.labelField = new GuiTextField(0, this.fontRenderer, this.width / 2 - 150, 50, 300, 20);
        this.labelField.setGuiResponder(this::onTextUpdated);
        this.labelField.setSuggestion(LocalizationKeys.LABEL_GUN_GUI_LABEL_EDIT_PLACEHOLDER.get());
        this.labelField.setMaxStringLength(ServerboundLabelGunSetActiveLabelPacket.MAX_LABEL_LENGTH);
        this.labelField.setFocused(true);

        this.buttonList.add(
                new SFMButtonBuilder()
                        .setSize(50, 20)
                        .setPosition(this.width / 2 - 210, 50)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_CLEAR_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundLabelGunClearPacket(HAND));
                            LABEL_HOLDER.clear();
                            shouldRebuildWidgets = true;
                        })
                        .build(this.buttonList.size())
        );
        this.buttonList.add(
                new SFMButtonBuilder()
                        .setSize(50, 20)
                        .setPosition(this.width / 2 + 160, 50)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_PRUNE_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundLabelGunPrunePacket(HAND));
                            LABEL_HOLDER.prune();
                            shouldRebuildWidgets = true;
                        })
                        .build(this.buttonList.size())
        );
        this.buttonList.add(
                new SFMButtonBuilder()
                        .setSize(200, 20)
                        .setPosition(this.width / 2 - 100, this.height - 25)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_CYCLE_VIEW_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundLabelGunCycleViewModePacket(HAND));
                            mc.displayGuiScreen(null);
                        })
                        .build(this.buttonList.size())
        );
        this.buttonList.add(
                new SFMButtonBuilder()
                        .setSize(300, 20)
                        .setPosition(this.width / 2 - 150, this.height - 50)
                        .setText("Done")
                        .setOnPress((p_97691_) -> this.onDone())
                        .build(this.buttonList.size())
        );
        onTextUpdated("", 0);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.labelField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
        if (keyCode != Keyboard.KEY_RETURN && keyCode != Keyboard.KEY_NUMPADENTER) return;
        onDone();
    }

    public void onDone() {
        SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(
                labelField.getText(),
                HAND
        ));
        mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shouldRebuildWidgets) {
            // we delay this because focus gets reset _after_ the button event handler
            // we want to end with the label input field focused
            shouldRebuildWidgets = false;
            rebuildWidgets();
        }
        this.drawDefaultBackground();
        this.labelField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    private void onTextUpdated(int id, String newText) {
        labelField.setSuggestion(newText.isEmpty() ? LocalizationKeys.LABEL_GUN_GUI_LABEL_EDIT_PLACEHOLDER.get() : "");
        labelButtons.forEach(this.buttonList::remove);
        labelButtons.clear();

        int buttonWidth = LABEL_HOLDER.labels().keySet().stream()
                                  .map(entry -> LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.get(entry, LABEL_HOLDER.getPositions(entry).size())).mapToInt(this.fontRenderer::getStringWidth).max().orElse(50) + 10;
        int paddingX = 5;
        int paddingY = 5;
        int buttonHeight = 20;

        int buttonsPerRow = this.width / (buttonWidth + paddingX);

        int i = 0;
        List<String> labels = new ArrayList<>(LABEL_HOLDER.labels().keySet());
        labels.removeIf(text -> !text.toLowerCase().contains(newText.toLowerCase()));
        labels.sort(Comparator.naturalOrder());

        for (String label : labels) {
            int x = (this.width - (buttonWidth + paddingX) * Math.min(buttonsPerRow, labels.size())) / 2 + paddingX + (i % buttonsPerRow) * (buttonWidth + paddingX);
            int y = 80 + (i / buttonsPerRow) * (buttonHeight + paddingY);
            addLabelButton(label, x, y, buttonWidth, buttonHeight);

            i++;
        }
    }

    private void addLabelButton(
            String label,
            int x,
            int y,
            int width,
            int height
    ) {
        int count = LABEL_HOLDER.getPositions(label).size();
        GuiButton button = new SFMButtonBuilder()
                .setSize(width, height)
                .setPosition(x, y)
                .setText(LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.get(label, count))
                .setOnPress((btn) -> {
                    this.labelField.setText(label);
                    this.onDone();
                })
                .build(this.buttonList.size());
        labelButtons.add(button);
        this.buttonList.add(button);
    }

    private void rebuildWidgets() {
        String text = this.labelField.getText();
        this.initGui();
        this.labelField.setText(text);
        this.labelField.setFocused(true);
    }
}