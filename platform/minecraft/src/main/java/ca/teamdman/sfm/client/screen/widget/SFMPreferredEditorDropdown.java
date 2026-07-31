package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMPreferredEditorChangedEvent;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SFMPreferredEditorDropdown {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PREFERRED_EDITOR = new LocalizationEntry(
            "gui.sfm.preferred_editor",
            "Editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PREFERRED_EDITOR_V1 = new LocalizationEntry(
            "gui.sfm.preferred_editor.sfm.v1",
            "V1 (Default)"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PREFERRED_EDITOR_V2 = new LocalizationEntry(
            "gui.sfm.preferred_editor.sfm.v2",
            "V2"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PREFERRED_EDITOR_DRAW = new LocalizationEntry(
            "gui.sfm.preferred_editor.sfm.draw",
            "Draw"
    );

    public static Button create(
            int x,
            int y,
            int width,
            int height,
            boolean compact
    ) {

        return new SFMButtonBuilder()
                .setPosition(x, y)
                .setSize(width, height)
                .setText(buttonMessage(getCurrentEditorId(), compact))
                .setOnPress(button -> SFMScreenChangeHelpers.setOrPushScreen(new PickerScreen(
                        x,
                        y,
                        width,
                        height,
                        id -> button.setMessage(buttonMessage(id, compact))
                )))
                .build();
    }

    public static Button createForEditor(
            int x,
            int y,
            int width,
            int height,
            ISFMTextEditScreen editorScreen,
            Supplier<String> latestContent
    ) {

        return new SFMButtonBuilder()
                .setPosition(x, y)
                .setSize(width, height)
                .setText(buttonMessage(getCurrentEditorId(), true))
                .setOnPress(button -> SFMScreenChangeHelpers.setOrPushScreen(new PickerScreen(
                        x,
                        y,
                        width,
                        height,
                        id -> switchEditor(editorScreen, latestContent.get())
                )))
                .build();
    }

    private static void switchEditor(
            ISFMTextEditScreen editorScreen,
            String latestContent
    ) {

        ISFMTextEditScreenOpenContext oldContext = editorScreen.openContext();
        ISFMTextEditScreenOpenContext newContext = new ISFMTextEditScreenOpenContext() {
            @Override
            public String initialValue() {

                return latestContent;
            }

            @Override
            public void onTryClose(
                    String newLatestContent,
                    Runnable finalizeClose
            ) {

                oldContext.onTryClose(newLatestContent, finalizeClose);
            }

            @Override
            public void onSaveAndClose(String newLatestContent) {

                oldContext.onSaveAndClose(newLatestContent);
            }

            @Override
            public Consumer<String> saveWriter() {

                return oldContext.saveWriter();
            }

            @Override
            public LabelPositionHolder labelPositionHolder() {

                return oldContext.labelPositionHolder();
            }
        };
        ISFMTextEditScreen newScreen = SFMScreenChangeHelpers.createProgramEditScreen(newContext);
        switch (editorScreen.openBehaviour()) {
            case Push -> {
                SFMScreenChangeHelpers.popScreen();
                SFMScreenChangeHelpers.showTextEditScreen(newScreen);
            }
            case Replace -> SFMScreenChangeHelpers.setScreen(newScreen.asScreen());
        }
    }

    public static Component getDisplayName(Identifier id) {

        return Component.translatableWithFallback(
                PREFERRED_EDITOR.key().get() + "." + id.getNamespace() + "." + id.getPath(),
                id.toString()
        );
    }

    public static List<Identifier> getEditorIds() {

        List<Identifier> editorIds = new ArrayList<>();
        for (ISFMTextEditorRegistration registration : SFMTextEditors.registry()) {
            Identifier id = SFMTextEditors.registry().getId(registration);
            if (id != null) {
                editorIds.add(id);
            }
        }
        return editorIds;
    }

    public static Identifier getCurrentEditorId() {

        List<Identifier> editorIds = getEditorIds();
        @Nullable Identifier current = SFMResourceLocation.tryParse(
                SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get()
        );
        if (current == null || !editorIds.contains(current)) {
            current = editorIds.getFirst();
        }
        return current;
    }

    private static Component buttonMessage(
            Identifier id,
            boolean compact
    ) {

        MutableComponent message = compact
                                   ? getDisplayName(id).copy()
                                   : PREFERRED_EDITOR.getComponent().append(": ").append(getDisplayName(id));
        return message.append(" ▼");
    }

    private static class PickerScreen extends Screen {
        private static final int PANEL_PADDING = 3;

        private static final int PANEL_BACKGROUND = 0xF0141414;

        private static final int PANEL_BORDER = 0xFF8B8B8B;

        private static final int ROW_HOVER_BACKGROUND = 0x40FFFFFF;

        private static final int TEXT_COLOR = 0xFFE0E0E0;

        private static final int TEXT_COLOR_HOVERED = 0xFFFFFFFF;

        private static final int TEXT_COLOR_CURRENT = 0xFFFFD83D;

        private final int anchorX;

        private final int anchorY;

        private final int anchorWidth;

        private final int anchorHeight;

        private final Consumer<Identifier> onSelect;

        private List<Identifier> editorIds = List.of();

        private Identifier currentEditorId;

        private int popupX;

        private int popupY;

        private int popupWidth;

        private int popupHeight;

        private int rowHeight;

        private PickerScreen(
                int anchorX,
                int anchorY,
                int anchorWidth,
                int anchorHeight,
                Consumer<Identifier> onSelect
        ) {

            super(PREFERRED_EDITOR.getComponent());
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorWidth = anchorWidth;
            this.anchorHeight = anchorHeight;
            this.onSelect = onSelect;
        }

        @Override
        public boolean isInGameUi() {

            return true;
        }

        @Override
        protected void init() {

            super.init();
            editorIds = getEditorIds();
            currentEditorId = getCurrentEditorId();
            rowHeight = this.font.lineHeight + 4;
            popupWidth = anchorWidth;
            for (Identifier id : editorIds) {
                popupWidth = Math.max(popupWidth, this.font.width(getDisplayName(id)) + 2 * PANEL_PADDING + 8);
            }
            popupHeight = editorIds.size() * rowHeight + 2 * PANEL_PADDING;
            boolean dropUp = anchorY + anchorHeight + popupHeight + 2 > this.height;
            popupX = Math.max(0, Math.min(anchorX, this.width - popupWidth));
            popupY = dropUp ? anchorY - popupHeight - 2 : anchorY + anchorHeight + 2;
        }

        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {

            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            graphics.fill(
                    popupX - 1,
                    popupY - 1,
                    popupX + popupWidth + 1,
                    popupY + popupHeight + 1,
                    PANEL_BORDER
            );
            graphics.fill(
                    popupX,
                    popupY,
                    popupX + popupWidth,
                    popupY + popupHeight,
                    PANEL_BACKGROUND
            );
            int hoveredIndex = rowIndexAt(mouseX, mouseY);
            for (int i = 0; i < editorIds.size(); i++) {
                Identifier id = editorIds.get(i);
                int rowY = popupY + PANEL_PADDING + i * rowHeight;
                if (i == hoveredIndex) {
                    graphics.fill(
                            popupX + 1,
                            rowY,
                            popupX + popupWidth - 1,
                            rowY + rowHeight,
                            ROW_HOVER_BACKGROUND
                    );
                }
                int color = id.equals(currentEditorId)
                            ? TEXT_COLOR_CURRENT
                            : i == hoveredIndex ? TEXT_COLOR_HOVERED : TEXT_COLOR;
                SFMFontUtils.draw(
                        graphics,
                        this.font,
                        getDisplayName(id),
                        popupX + PANEL_PADDING + 4,
                        rowY + 2,
                        color,
                        true
                );
            }
        }

        @Override
        public void extractBackground(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {

        }

        private int rowIndexAt(
                double mouseX,
                double mouseY
        ) {

            if (mouseX < popupX || mouseX >= popupX + popupWidth) {
                return -1;
            }
            if (mouseY < popupY + PANEL_PADDING) {
                return -1;
            }
            int index = (int) ((mouseY - popupY - PANEL_PADDING) / rowHeight);
            return index >= 0 && index < editorIds.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(
                MouseButtonEvent event,
                boolean doubleClick
        ) {

            int index = rowIndexAt(event.x(), event.y());
            this.onClose();
            if (index >= 0) {
                Identifier id = editorIds.get(index);
                if (!id.equals(currentEditorId)) {
                    SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(id.toString());
                    SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.save();
                    NeoForge.EVENT_BUS.post(new SFMPreferredEditorChangedEvent(currentEditorId, id));
                    onSelect.accept(id);
                }
            }
            return true;
        }

        @Override
        public void onClose() {

            SFMScreenChangeHelpers.popScreen();
        }
    }
}
