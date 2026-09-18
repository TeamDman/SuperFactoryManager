package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SFMKeyBindingScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private EditBox search;
    private List<Identifier> visibleActions = List.of();
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
                    var action = SFMClientActions.registry().get(id).map(reference -> reference.value()).orElse(null);
                    return id.toString().toLowerCase(Locale.ROOT).contains(query)
                            || action != null && action.title().getString().toLowerCase(Locale.ROOT).contains(query);
                })
                .sorted(Comparator.comparing(Identifier::toString))
                .limit(Math.max(1, (height - 92) / ROW_HEIGHT))
                .toList();
    }

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Component heading = title.copy().withStyle(ChatFormatting.BOLD);
        SFMFontUtils.draw(graphics, font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        int rowWidth = Math.min(420, width - 24);
        int left = (width - rowWidth) / 2;
        int y = 68;
        for (Identifier actionId : visibleActions) {
            var action = SFMClientActions.registry().get(actionId).map(reference -> reference.value()).orElse(null);
            if (action == null) continue;
            boolean hovered = mouseX >= left && mouseX < left + rowWidth && mouseY >= y - 4 && mouseY < y + 18;
            graphics.fill(left, y - 4, left + rowWidth, y + 18, hovered ? 0xFF404040 : 0xCC252525);
            String actionTitle = font.plainSubstrByWidth(action.title().getString(), Math.max(20, rowWidth - 145));
            SFMFontUtils.draw(graphics, font, actionTitle, left + 6, y + 2, 0xFFFFFFFF, false);
            String count = ca.teamdman.sfm.client.keybinding.SFMKeyBindingService.INSTANCE
                    .bindingsForAction(actionId).size() + " bindings   [?]";
            SFMFontUtils.draw(graphics, font, count, left + rowWidth - 6 - font.width(count), y + 2,
                    0xFF80D8FF, false);
            y += ROW_HEIGHT;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int rowWidth = Math.min(420, width - 24);
        int left = (width - rowWidth) / 2;
        int index = (int) ((mouseY - 64) / ROW_HEIGHT);
        if (mouseX >= left && mouseX < left + rowWidth && index >= 0 && index < visibleActions.size()) {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, visibleActions.get(index)));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }
}
