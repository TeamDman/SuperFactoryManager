package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundLabelGunClearPacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunCycleViewModePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunPrunePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunSetActiveLabelPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import com.bbscn.Button;
import com.bbscn.CommonComponents;
import com.bbscn.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LabelGunScreen extends GuiScreenExtend {
    private final EnumHand HAND;
    private final LabelPositionHolder LABEL_HOLDER;
    private final ArrayList<Button> labelButtons = new ArrayList<>();
    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox labelField;
    private boolean shouldRebuildWidgets = false;

    public LabelGunScreen(ItemStack stack, EnumHand hand) {
//        super(LocalizationKeys.LABEL_GUN_GUI_TITLE.getComponent());
        LABEL_HOLDER = LabelPositionHolder.from(stack);
        HAND = hand;
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuildWidgets();

        SFMScreenRenderUtils.enableKeyRepeating();
        this.labelField = addRenderableWidget(new EditBox(
                this.fontRenderer,
                this.width / 2 - 150,
                50,
                300,
                20,
                LocalizationKeys.LABEL_GUN_GUI_LABEL_PLACEHOLDER.getComponent()
        ));
        this.labelField.setResponder(this::onTextUpdated);
        this.labelField.setSuggestion(LocalizationKeys.LABEL_GUN_GUI_LABEL_EDIT_PLACEHOLDER.getString());

//        this.setInitialFocus(labelField);
        this.setFocused(labelField);
        this.labelField.setFocused(true);

        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setSize(50, 20)
                        .setPosition(this.width / 2 - 210, 50)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_CLEAR_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.sendToServer(new ServerboundLabelGunClearPacket(HAND));
                            LABEL_HOLDER.clear();
                            shouldRebuildWidgets = true;
                        })
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setSize(50, 20)
                        .setPosition(this.width / 2 + 160, 50)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_PRUNE_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.sendToServer(new ServerboundLabelGunPrunePacket(HAND));
                            LABEL_HOLDER.prune();
                            shouldRebuildWidgets = true;
                        })
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setSize(200, 20)
                        .setPosition(this.width / 2 - 2 - 100, this.height - 25)
                        .setText(LocalizationKeys.LABEL_GUN_GUI_CYCLE_VIEW_BUTTON)
                        .setOnPress((btn) -> {
                            SFMPackets.sendToServer(new ServerboundLabelGunCycleViewModePacket(HAND));
                            onClose();
                        })
                        .build()
        );
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setSize(300, 20)
                        .setPosition(this.width / 2 - 2 - 150, this.height - 50)
                        .setText(CommonComponents.GUI_DONE)
                        .setOnPress((p_97691_) -> this.onDone())
                        .build()
        );
        onTextUpdated("");
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (typedChar == '\r' && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))) {
            onDone();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onResize(Minecraft mc, int x, int y) {
        String prev = this.labelField.getValue();
        this.setWorldAndResolution(mc, x, y);
        super.onResize(mc, x, y);
        this.labelField.setValue(prev);
    }

    @Override
    public void drawScreen(
            int mx,
            int my,
            float partialTicks
    ) {
        if (shouldRebuildWidgets) {
            // we delay this because focus gets reset _after_ the button event handler
            // we want to end with the label input field focused
            shouldRebuildWidgets = false;
            initGui();

        }

        this.drawDefaultBackground();

        super.drawScreen(mx, my, partialTicks);
    }

    private void rebuildWidgets() {
        this.buttonList.clear();     // 清空所有按钮
        this.labelList.clear();
        this.children.clear();
        this.renderables.clear();
        this.labelButtons.clear();
    }


    private void onTextUpdated(String newText) {
        labelField.setSuggestion(newText.isEmpty() ? LocalizationKeys.LABEL_GUN_GUI_LABEL_EDIT_PLACEHOLDER.getString() : "");
        labelButtons.forEach(this::removeWidget);
        labelButtons.clear();

        int buttonWidth = LABEL_HOLDER.labels().entrySet().stream()
                                  .map(entry -> LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.getComponent(entry.getKey(), entry.getValue()
                                          .size()).getUnformattedText()).mapToInt(this.fontRenderer::getStringWidth).max().orElse(50) + 10;
        int paddingX = 5;
        int paddingY = 5;
        int buttonHeight = 20;

        int buttonsPerRow = this.width / (buttonWidth + paddingX);

        int i = 0;
        List<String> labels = LABEL_HOLDER.labels().keySet().stream()
                .filter(text -> text.toLowerCase().contains(newText.toLowerCase()))
                .sorted(Comparator.naturalOrder()).collect(Collectors.toList());

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
        Button button = new SFMButtonBuilder()
                .setSize(width, height)
                .setPosition(x, y)
                .setText(LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.getComponent(label, count))
                .setOnPress((btn) -> {
                    this.labelField.setValue(label);
                    this.onDone();
                })
                .build();
        labelButtons.add(button);
        this.addRenderableWidget(button);
    }

    public void onDone() {
        SFMPackets.sendToServer(new ServerboundLabelGunSetActiveLabelPacket(
                this.labelField.getValue(),
                HAND
        ));
        onClose();
    }
}
