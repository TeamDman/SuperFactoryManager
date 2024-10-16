package ca.teamdman.sfm.client.gui.screen;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundLabelGunClearPacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunPrunePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUpdatePacket;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LabelGunScreen extends Screen {
    private final InteractionHand HAND;
    private final LabelPositionHolder LABEL_HOLDER;
    private final ArrayList<Button> labelButtons = new ArrayList<>();
    @SuppressWarnings("NotNullFieldNotInitialized")
    private EditBox labelField;
    private boolean shouldRebuildWidgets = false;

    public LabelGunScreen(ItemStack labelGunStack, InteractionHand hand) {
        super(LocalizationKeys.LABEL_GUN_GUI_TITLE.getComponent());
        LABEL_HOLDER = LabelPositionHolder.from(labelGunStack);
        HAND = hand;
    }

    @Override
    protected void init() {
        super.init();
        assert this.minecraft != null;
        this.labelField = addRenderableWidget(new EditBox(
                this.font,
                this.width / 2 - 150,
                50,
                300,
                20,
                LocalizationKeys.LABEL_GUN_GUI_LABEL_PLACEHOLDER.getComponent()
        ));
        this.labelField.setResponder(this::onTextUpdated);

        this.setInitialFocus(labelField);
        this.setFocused(labelField);

        this.addRenderableWidget(
                new Button.Builder(LocalizationKeys.LABEL_GUN_GUI_CLEAR_BUTTON.getComponent(), __ -> {
                    PacketDistributor.sendToServer(new ServerboundLabelGunClearPacket(HAND));
                    LABEL_HOLDER.clear();
                    shouldRebuildWidgets = true;
                })
                        .pos(this.width / 2 - 210, 50)
                        .size(50, 20)
                        .build()
        );
        this.addRenderableWidget(
                new Button.Builder(LocalizationKeys.LABEL_GUN_GUI_PRUNE_BUTTON.getComponent(), (btn) -> {
                    PacketDistributor.sendToServer(new ServerboundLabelGunPrunePacket(HAND));
                    LABEL_HOLDER.prune();
                    shouldRebuildWidgets = true;
                })
                        .pos(this.width / 2 + 160, 50)
                        .size(50, 20)
                        .build()
        );
        this.addRenderableWidget(
                new Button.Builder(CommonComponents.GUI_DONE, __ -> this.onDone())
                        .pos(this.width / 2 - 2 - 150, this.height - 50)
                        .size(300, 20)
                        .build()
        );

        onTextUpdated(""); // Call once to draw all labels
    }

    @Override
    public boolean keyPressed(int key, int mod1, int mod2) {
        if (super.keyPressed(key, mod1, mod2)) return true;
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return false;
        onDone();
        return true;
    }

    public void onDone() {
        PacketDistributor.sendToServer(new ServerboundLabelGunUpdatePacket(
                labelField.getValue(),
                HAND
        ));
        onClose();
    }

    @Override
    public void resize(Minecraft mc, int x, int y) {
        var prev = this.labelField.getValue();
        init(mc, x, y);
        super.resize(mc, x, y);
        this.labelField.setValue(prev);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTicks) {
        if (shouldRebuildWidgets) {
            // we delay this because focus gets reset _after_ the button event handler
            // we want to end with the label input field focused
            shouldRebuildWidgets = false;
            rebuildWidgets();
        }
        this.renderTransparentBackground(graphics);
        super.render(graphics, mx, my, partialTicks);
    }

    private void onTextUpdated(String newText) {
        labelButtons.forEach(this::removeWidget);
        labelButtons.clear();

        int buttonWidth = LABEL_HOLDER.labels().entrySet().stream()
                .map(entry -> LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.getComponent(entry.getKey(), entry.getValue()
                        .size()).getString()).mapToInt(this.font::width).max().orElse(50) + 10;
        int paddingX = 5;
        int paddingY = 5;
        int buttonHeight = 20;

        int buttonsPerRow = this.width / (buttonWidth + paddingX);

        int i = 0;
        List<String> labels = LABEL_HOLDER.labels().keySet().stream()
                .filter(text -> text.toLowerCase().contains(newText.toLowerCase()))
                .sorted(Comparator.naturalOrder()).toList();

        for (String label : labels) {
            int x = (this.width - (buttonWidth + paddingX) * Math.min(buttonsPerRow, labels.size())) / 2 + paddingX + (i % buttonsPerRow) * (buttonWidth + paddingX);
            int y = 80 + (i / buttonsPerRow) * (buttonHeight + paddingY);
            addLabelButton(label, x, y, buttonWidth, buttonHeight);

            i++;
        }
    }

    private void addLabelButton(String label, int x, int y, int width, int height) {
        int count = LABEL_HOLDER.getPositions(label).size();
        Button button = this.addRenderableWidget(
                new Button.Builder(
                        LocalizationKeys.LABEL_GUN_GUI_LABEL_BUTTON.getComponent(label, count), (btn) -> {
                    this.labelField.setValue(label);
                    this.onDone();
                }).pos(x, y)
                        .size(width, height)
                        .build());

        labelButtons.add(button);
    }
}
