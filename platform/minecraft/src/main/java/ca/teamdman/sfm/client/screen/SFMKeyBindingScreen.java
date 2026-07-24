package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SFMKeyBindingScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private EditBox search;
    private List<ResourceLocation> visibleActions = List.of();
    private final boolean pushed;

    public SFMKeyBindingScreen() {
        super(Component.literal("SFM Shortcuts"));
        this.pushed = SFMScreenChangeHelpers.getCurrentScreen() != null;
    }

    @Override
    protected void init() {
        int searchWidth = Math.min(300, width - 24);
        search = addRenderableWidget(new EditBox(font, (width - searchWidth) / 2, 34, searchWidth, 20,
                Component.literal("Search actions")));
        search.setResponder(ignored -> refresh());
        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        visibleActions = SFMClientActions.registry().keys().stream()
                .filter(id -> {
                    var action = SFMClientActions.registry().get(id);
                    return id.toString().toLowerCase(Locale.ROOT).contains(query)
                            || action != null && action.title().getString().toLowerCase(Locale.ROOT).contains(query);
                })
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .limit(Math.max(1, (height - 92) / ROW_HEIGHT))
                .toList();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        Component heading = title.copy().withStyle(ChatFormatting.BOLD);
        SFMFontUtils.draw(poseStack, font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        int rowWidth = Math.min(420, width - 24);
        int left = (width - rowWidth) / 2;
        int y = 68;
        for (ResourceLocation actionId : visibleActions) {
            var action = SFMClientActions.registry().get(actionId);
            if (action == null) continue;
            boolean hovered = mouseX >= left && mouseX < left + rowWidth && mouseY >= y - 4 && mouseY < y + 18;
            fill(poseStack, left, y - 4, left + rowWidth, y + 18, hovered ? 0xFF404040 : 0xCC252525);
            String actionTitle = font.plainSubstrByWidth(action.title().getString(), Math.max(20, rowWidth - 145));
            SFMFontUtils.draw(poseStack, font, actionTitle, left + 6, y + 2, 0xFFFFFFFF, false);
            String count = ca.teamdman.sfm.client.keybinding.SFMKeyBindingService.INSTANCE
                    .bindingsForAction(actionId).size() + " bindings   [?]";
            SFMFontUtils.draw(poseStack, font, count, left + rowWidth - 6 - font.width(count), y + 2,
                    0xFF80D8FF, false);
            y += ROW_HEIGHT;
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowWidth = Math.min(420, width - 24);
        int left = (width - rowWidth) / 2;
        int index = (int) ((mouseY - 64) / ROW_HEIGHT);
        if (mouseX >= left && mouseX < left + rowWidth && index >= 0 && index < visibleActions.size()) {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, visibleActions.get(index)));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }
}
