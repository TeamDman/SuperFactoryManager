package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.BiConsumer;

public class ExamplesScreen extends Screen {
    private final BiConsumer<String, List<SFMExampleProgram>> CALLBACK;

    public ExamplesScreen(BiConsumer<String, List<SFMExampleProgram>> callback) {

        super(LocalizationKeys.EXAMPLES_GUI_TITLE.getComponent());
        CALLBACK = callback;
    }

    @Override
    public void render(
            PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {

        // Darken background
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);

        // Draw widgets
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);

        // Draw the warning that informs the user that this can overwrite their program
        MutableComponent warning1 = LocalizationKeys.EXAMPLES_GUI_WARNING_1.getComponent();
        SFMFontUtils.draw(
                pPoseStack,
                this.font,
                warning1,
                this.width / 2 - this.font.width(warning1) / 2,
                20,
                0xffffff,
                false
        );

        MutableComponent warning2 = LocalizationKeys.EXAMPLES_GUI_WARNING_2.getComponent();
        SFMFontUtils.draw(
                pPoseStack,
                this.font,
                warning2,
                this.width / 2 - this.font.width(warning2) / 2,
                36,
                0xffffff,
                false
        );
    }

    @Override
    protected void init() {

        super.init();

        // discover template programs
        List<SFMExampleProgram> sfmExamplePrograms = SFMExampleProgram.gatherAll();

        // determine largest button size
        int buttonWidth = sfmExamplePrograms
                                  .stream()
                                  .map(SFMExampleProgram::displayName)
                                  .mapToInt(this.font::width)
                                  .max().orElse(50) + 10;

        // declare sizing information
        int buttonHeight = 16;
        int paddingX = 5;
        int paddingY = 1;

        // derive sizing information
        int buttonsPerRow = this.width / (buttonWidth + paddingX);
        int rowWidth = (buttonWidth + paddingX) * Math.min(buttonsPerRow, sfmExamplePrograms.size());
        int marginX = (this.width - rowWidth) / 2;


        // create a button for each program
        int buttonIndex = 0;
        for (var entry : sfmExamplePrograms) {

            // determine position using button index
            int x = marginX
                    + paddingX
                    + (buttonIndex % buttonsPerRow) * (buttonWidth + paddingX);

            int y = 50
                    + (buttonIndex / buttonsPerRow) * (buttonHeight + paddingY);

            // create the button
            this.addRenderableWidget(
                    new SFMButtonBuilder()
                            .setText(Component.literal(entry.displayName()))
                            .setOnPress(btn -> {
                                onClose();
                                CALLBACK.accept(entry.programString(), sfmExamplePrograms);
                            })
                            .setPosition(x, y)
                            .setSize(buttonWidth, buttonHeight)
                            .build()
            );

            // increment button index
            buttonIndex++;
        }
    }

}
