package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.examples.SFMExampleProgram;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.GuiGraphics;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.BiConsumer;

public class ExamplesScreen extends Screen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry EXAMPLES_GUI_WARNING_1 = new LocalizationEntry(
            "gui.sfm.program_template_picker.warning1",
            "Hitting \"Done\" will on the next screen will overwrite your existing program!"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry EXAMPLES_GUI_WARNING_2 = new LocalizationEntry(
            "gui.sfm.program_template_picker.warning2",
            "Hit <esc> to cancel instead."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry EXAMPLES_GUI_TITLE = new LocalizationEntry(
            "gui.sfm.title.program_template_picker",
            "Program Template Picker"
    );

    private final BiConsumer<String, List<SFMExampleProgram>> CALLBACK;

    public ExamplesScreen(BiConsumer<String, List<SFMExampleProgram>> callback) {

        super(EXAMPLES_GUI_TITLE.getComponent());
        CALLBACK = callback;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    public void render(
            PoseStack pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public void render(
            GuiGraphics graphics,
{% when '26.1.2' %}
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
{% endcase %}
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {

        // Darken background
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);
{% when '1.20', '1.20.1' %}
        this.renderBackground(graphics);
        this.renderBackground(graphics);
        this.renderBackground(graphics);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        this.renderTransparentBackground(graphics);
        this.renderTransparentBackground(graphics);
        this.renderTransparentBackground(graphics);
{% when '26.1.2' %}
        this.extractTransparentBackground(graphics);
{% endcase %}

        // Draw widgets
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super.render(graphics, pMouseX, pMouseY, pPartialTick);
{% when '26.1.2' %}
        super.extractRenderState(graphics, pMouseX, pMouseY, pPartialTick);
{% endcase %}

        // Draw the warning that informs the user that this can overwrite their program
        MutableComponent warning1 = EXAMPLES_GUI_WARNING_1.getComponent();
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                graphics,
{% endcase %}
                this.font,
                warning1,
                this.width / 2 - this.font.width(warning1) / 2,
                20,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                0xffffff,
{% when '26.1.2' %}
                0xffffffff,
{% endcase %}
                false
        );

        MutableComponent warning2 = EXAMPLES_GUI_WARNING_2.getComponent();
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                pPoseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                graphics,
{% endcase %}
                this.font,
                warning2,
                this.width / 2 - this.font.width(warning2) / 2,
                36,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                0xffffff,
{% when '26.1.2' %}
                0xffffffff,
{% endcase %}
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
