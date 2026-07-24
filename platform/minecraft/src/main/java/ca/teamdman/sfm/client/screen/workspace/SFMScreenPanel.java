package ca.teamdman.sfm.client.screen.workspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


/**
 * Content hosted by {@link SFMScreenMultiplexer}.
 *
 * <p>This is intentionally narrower than Minecraft's {@code Screen}. A
 * vanilla screen owns a complete viewport and can replace the global current
 * screen from its close path, so it cannot be embedded safely without an
 * explicit adapter.</p>
 */
public interface SFMScreenPanel {
    Component title();

    default Component narration() {
        return title();
    }

    default void opened(
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            SFMWorkspacePanelContext context
    ) {
    }

    default void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
    }

    default void closed() {
    }

    default void tick() {
    }

    void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    );

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char character, int modifiers) {
        return false;
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

}
