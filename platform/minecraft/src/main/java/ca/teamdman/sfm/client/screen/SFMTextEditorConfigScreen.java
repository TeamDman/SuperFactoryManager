package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("NotNullFieldNotInitialized")
public class SFMTextEditorConfigScreen extends Screen {
    private final SFMClientTextEditorConfig config;
    private final ISFMTextEditScreen parent;
    private final Runnable closeCallback;
    private Button lineNumbersOnButton;
    private Button lineNumbersOffButton;
    private Button intellisenseOffButton;
    private Button intellisenseBasicButton;
    private Button intellisenseAdvancedButton;
    private Button preferredEditorV1Button;
    private Button preferredEditorV2Button;
    private Button preferredEditorV3Button;
    private final boolean editorSelectorFeatureFlag = SFMEnvironmentUtils.isInIDE();

    public SFMTextEditorConfigScreen(
            ISFMTextEditScreen parent,
            SFMClientTextEditorConfig config,
            Runnable closeCallback
    ) {
        super(LocalizationKeys.PROGRAM_EDITOR_CONFIG_SCREEN_TITLE.getComponent());
        this.config = config;
        this.parent = parent;
        this.closeCallback = closeCallback;
    }

    @Override
    public void onClose() {
        SFMScreenChangeHelpers.popScreen();
        closeCallback.run();
    }

    @Override
    public void render(
            @NotNull PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        this.renderBackground(pPoseStack);
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);

        int y = this.height / 2 - 65;
        int x = this.width / 2 - 150; // Shifted to the left for centering
        drawString(
                pPoseStack,
                font,
                LocalizationKeys.PROGRAM_EDITOR_CONFIG_LINE_NUMBERS.getComponent(),
                x,
                y,
                0xFFFFFF
        );
        drawString(
                pPoseStack,
                font,
                LocalizationKeys.PROGRAM_EDITOR_CONFIG_INTELLISENSE.getComponent(),
                x,
                y + 50,
                0xFFFFFF
        );
        if (editorSelectorFeatureFlag) {
            drawString(
                    pPoseStack,
                    font,
                    LocalizationKeys.PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR.getComponent(),
                    x,
                    y + 100,
                    0xFFFFFF
            );
        }
        drawCenteredString(
                pPoseStack,
                font,
                this.title,
                this.width / 2,
                15,
                0xFFFFFF
        ); // Ensure title is still displayed
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = this.width / 2 - (3 * buttonWidth) / 2
                - 10; // Centering the buttons
        int y = this.height / 2 - 50;
        int spacing = 50;
        int buttonSpacing = 10; // Space between buttons

        // Line Numbers Buttons
        lineNumbersOnButton =
                new SFMButtonBuilder()
                        .setPosition(x + buttonWidth + buttonSpacing, y)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(CommonComponents.OPTION_ON)
                        .setOnPress(button -> {
                            config.showLineNumbers.set(true);
                            updateButtonStates();
                        })
                        .build();
        lineNumbersOffButton =
                new SFMButtonBuilder()
                        .setPosition(x, y)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(CommonComponents.OPTION_OFF)
                        .setOnPress(button -> {
                            config.showLineNumbers.set(false);
                            updateButtonStates();
                        })
                        .build();

        this.addRenderableWidget(lineNumbersOnButton);
        this.addRenderableWidget(lineNumbersOffButton);

        // Intellisense Buttons
        intellisenseOffButton =
                new SFMButtonBuilder()
                        .setPosition(x, y + spacing)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_INTELLISENSE_OFF)
                        .setOnPress(button -> {
                            config.intellisenseLevel.set(SFMTextEditorIntellisenseLevel.OFF);
                            updateButtonStates();
                            parent.onPreferenceChanged();
                        })
                        .build();
        intellisenseBasicButton =
                new SFMButtonBuilder()
                        .setPosition(x + buttonWidth + buttonSpacing, y + spacing)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_INTELLISENSE_BASIC)
                        .setOnPress(button -> {
                            config.intellisenseLevel.set(SFMTextEditorIntellisenseLevel.BASIC);
                            updateButtonStates();
                            parent.onPreferenceChanged();
                        })
                        .build();
        intellisenseAdvancedButton =
                new SFMButtonBuilder()
                        .setPosition(
                                x + 2 * (buttonWidth + buttonSpacing), y + spacing
                        )
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_INTELLISENSE_ADVANCED)
                        .setOnPress(button -> {
                            config.intellisenseLevel.set(SFMTextEditorIntellisenseLevel.ADVANCED);
                            updateButtonStates();
                            parent.onPreferenceChanged();
                        })
                        .build();

        this.addRenderableWidget(intellisenseOffButton);
        this.addRenderableWidget(intellisenseBasicButton);
        this.addRenderableWidget(intellisenseAdvancedButton);

        // Preferred Editor Buttons
        preferredEditorV1Button =
                new SFMButtonBuilder()
                        .setPosition(x, y + 2 * spacing)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR_V1)
                        .setOnPress(button -> {
                            //noinspection OptionalGetWithoutIsPresent
                            config.preferredEditor.set(SFMTextEditors.V1.getId().get().location().toString());
                            updateButtonStates();
                        })
                        .build();
        preferredEditorV2Button =
                new SFMButtonBuilder()
                        .setPosition(x + buttonWidth + buttonSpacing, y + 2 * spacing)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR_V2)
                        .setOnPress(button -> {
                            //noinspection OptionalGetWithoutIsPresent
                            config.preferredEditor.set(SFMTextEditors.V2.getId().get().location().toString());
                            updateButtonStates();
                        })
                        .build();
        preferredEditorV3Button =
                new SFMButtonBuilder()
                        .setPosition(x + 2 * (buttonWidth + buttonSpacing), y + 2 * spacing)
                        .setSize(buttonWidth, buttonHeight)
                        .setText(LocalizationKeys.PROGRAM_EDITOR_CONFIG_PREFERRED_EDITOR_V3)
                        .setOnPress(button -> {
                            //noinspection OptionalGetWithoutIsPresent
                            config.preferredEditor.set(SFMTextEditors.V3.getId().get().location().toString());
                            updateButtonStates();
                        })
                        .build();
        if (editorSelectorFeatureFlag) {
            // This behaviour is not ready for release.
            this.addRenderableWidget(preferredEditorV1Button);
            this.addRenderableWidget(preferredEditorV2Button);
            this.addRenderableWidget(preferredEditorV3Button);
        }


        // Done Button
        this.addRenderableWidget(
                new SFMButtonBuilder()
                        .setPosition(this.width / 2 - 100, this.height - 50)
                        .setSize(200, 20)
                        .setText(CommonComponents.GUI_DONE)
                        .setOnPress((button) -> this.onClose())
                        .build());

        updateButtonStates();
    }

    private void updateButtonStates() {
        lineNumbersOnButton.active = !config.showLineNumbers.get();
        lineNumbersOffButton.active = config.showLineNumbers.get();

        intellisenseOffButton.active =
                config.intellisenseLevel.get() != SFMTextEditorIntellisenseLevel.OFF;
        intellisenseBasicButton.active =
                config.intellisenseLevel.get() != SFMTextEditorIntellisenseLevel.BASIC;
        intellisenseAdvancedButton.active =
                config.intellisenseLevel.get() != SFMTextEditorIntellisenseLevel.ADVANCED;

        String currentEditor = config.preferredEditor.get();
        //noinspection OptionalGetWithoutIsPresent
        preferredEditorV1Button.active = !currentEditor.equals(SFMTextEditors.V1.getId().get().location().toString());
        //noinspection OptionalGetWithoutIsPresent
        preferredEditorV2Button.active = !currentEditor.equals(SFMTextEditors.V2.getId().get().location().toString());
        //noinspection OptionalGetWithoutIsPresent
        preferredEditorV3Button.active = !currentEditor.equals(SFMTextEditors.V3.getId().get().location().toString());
    }
}
