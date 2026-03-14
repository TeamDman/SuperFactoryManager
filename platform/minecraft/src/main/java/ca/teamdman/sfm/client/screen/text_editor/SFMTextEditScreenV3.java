package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * V3 Text Editor - Grammar Explorer Tool
 * <p>
 * Layout:
 * - Top Left: Text field with syntax highlighting and line numbers
 * - Top Right: AST nodes near the caret (tree view)
 * - Bottom Left: Label Explorer
 * - Bottom Right: Documentation area
 */
public class SFMTextEditScreenV3 extends Screen implements ISFMTextEditScreen {
    private final ISFMTextEditScreenOpenContext openContext;
    private final @Nullable Screen previousScreen;

    public SFMTextEditScreenV3(
            ISFMTextEditScreenOpenContext openContext,
            @Nullable Screen previousScreen
    ) {
        super(LocalizationKeys.TEXT_EDIT_SCREEN_TITLE.getComponent());
        this.openContext = openContext;
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        // TODO: Initialize the four quarters
        // - Top left: text editor with syntax highlighting
        // - Top right: AST tree view
        // - Bottom left: label explorer
        // - Bottom right: documentation area
    }

    @Override
    public void render(
            PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        // Render background
        this.renderBackground(pPoseStack);

        // Calculate quarter boundaries
        int halfWidth = this.width / 2;
        int halfHeight = this.height / 2;

        // Draw divider lines for the four quarters
        // Vertical divider
        fill(pPoseStack, halfWidth - 1, 0, halfWidth + 1, this.height, 0xFF555555);
        // Horizontal divider
        fill(pPoseStack, 0, halfHeight - 1, this.width, halfHeight + 1, 0xFF555555);

        // TODO: Render each quarter's content
        // Top left: text editor
        // Top right: AST view
        // Bottom left: label explorer
        // Bottom right: documentation

        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public void onClose() {
        if (previousScreen != null) {
            SFMScreenChangeHelpers.setScreen(previousScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public ISFMTextEditScreenOpenContext openContext() {
        return openContext;
    }

    @Override
    public OpenBehaviour openBehaviour() {
        return OpenBehaviour.Push;
    }
}
