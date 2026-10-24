package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorSelection;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Changes only the preference for the next open, keeping the current document and editor alive. */
public final class SFMTextEditorSelectionScreen extends Screen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CHOOSE_EDITOR = new LocalizationEntry(
            "gui.sfm.editor_selection.title", "Choose editor");
    @SFMLocalizationDatagen
    public static final LocalizationEntry NEXT_OPEN = new LocalizationEntry(
            "gui.sfm.editor_selection.next_open", "Applies to the next editor opened. Your current text stays here.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SEARCH = new LocalizationEntry(
            "gui.sfm.editor_selection.search", "Search editor IDs");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CURRENT = new LocalizationEntry(
            "gui.sfm.editor_selection.current", "Current: {}");
    @SFMLocalizationDatagen
    public static final LocalizationEntry UNAVAILABLE = new LocalizationEntry(
            "gui.sfm.editor_selection.unavailable", "Unavailable (using sfm:v1): {}");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SELECTED = new LocalizationEntry(
            "gui.sfm.editor_selection.selected", "Selected: {}");
    @SFMLocalizationDatagen
    public static final LocalizationEntry APPLY = new LocalizationEntry(
            "gui.sfm.editor_selection.apply", "Apply");
    @SFMLocalizationDatagen
    public static final LocalizationEntry EMPTY = new LocalizationEntry(
            "gui.sfm.editor_selection.empty", "No matching registered editors");
    @SFMLocalizationDatagen
    public static final LocalizationEntry STALE = new LocalizationEntry(
            "gui.sfm.editor_selection.stale", "That editor is unavailable. Choose another editor or cancel.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SAVE_FAILED = new LocalizationEntry(
            "gui.sfm.editor_selection.save_failed", "Could not save the editor preference: {}");

    private final SFMClientTextEditorConfig config;
    private final SFMTextEditorSelection selection;
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private Button previous;
    private Button next;
    private Button apply;
    private Component diagnostic;
    private int ticks;

    public SFMTextEditorSelectionScreen(SFMClientTextEditorConfig config) {
        super(CHOOSE_EDITOR.getComponent());
        this.config = config;
        selection = new SFMTextEditorSelection(config.preferredEditor.get(),
                () -> SFMTextEditors.registry().keys().stream().map(Object::toString).toList());
    }

    @Override
    protected void init() {
        selection.refresh();
        rows.clear();
        int contentWidth = Math.max(40, Math.min(440, width - 24));
        int left = (width - contentWidth) / 2;
        search = addRenderableWidget(new EditBox(font, left, 60, contentWidth, 20, SEARCH.getComponent()));
        search.setMaxLength(32767);
        search.setValue(selection.query());
        search.setSuggestion(selection.query().isEmpty() ? SEARCH.getString() : null);
        search.setResponder(value -> {
            selection.setQuery(value);
            search.setSuggestion(value.isEmpty() ? SEARCH.getString() : null);
            updateRows();
        });
        int count = Math.max(1, (height - 152) / 22);
        selection.setPageSize(count);
        for (int index = 0; index < count; index++) {
            final int row = index;
            rows.add(addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left, 86 + index * 22).setSize(contentWidth, 20)
                    .setText(Component.empty())
                    .setTooltipSupplier(this, font, () -> Component.literal(rowId(row)))
                    .setOnPress(button -> {
                        if (selection.select(rowId(row))) diagnostic = null;
                        else diagnostic = STALE.getComponent();
                        updateRows();
                    }).build()));
        }
        previous = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left, height - 52).setSize(45, 20).setText(Component.literal("<"))
                .setOnPress(button -> { selection.setPage(selection.page() - 1); updateRows(); }).build());
        next = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + contentWidth - 45, height - 52).setSize(45, 20).setText(Component.literal(">"))
                .setOnPress(button -> { selection.setPage(selection.page() + 1); updateRows(); }).build());
        apply = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left, height - 28).setSize((contentWidth - 8) / 2, 20).setText(APPLY)
                .setOnPress(button -> applySelection()).build());
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + (contentWidth + 8) / 2, height - 28).setSize((contentWidth - 8) / 2, 20)
                .setText(CommonComponents.GUI_CANCEL).setOnPress(button -> onClose()).build());
        updateRows();
        setInitialFocus(search);
    }

    private String rowId(int row) {
        List<String> visible = selection.visibleIds();
        return row < visible.size() ? visible.get(row) : "";
    }

    private void updateRows() {
        for (int index = 0; index < rows.size(); index++) {
            Button button = rows.get(index);
            String id = rowId(index);
            button.visible = !id.isEmpty();
            String label = id.equals(selection.selectedId()) ? "> " + id : id;
            button.setMessage(Component.literal(font.plainSubstrByWidth(label, button.getWidth() - 12)));
        }
        if (previous != null) previous.active = selection.page() > 0;
        if (next != null) next.active = selection.page() + 1 < selection.pageCount();
        if (apply != null) apply.active = selection.canApply();
    }

    private void applySelection() {
        try {
            if (selection.apply(id -> {
                String before = config.preferredEditor.get();
                config.preferredEditor.set(id);
                try {
                    SFMConfig.CLIENT_TEXT_EDITOR_CONFIG_SPEC.save();
                } catch (RuntimeException failure) {
                    config.preferredEditor.set(before);
                    throw failure;
                }
            })) {
                onClose();
                return;
            }
            diagnostic = STALE.getComponent();
        } catch (RuntimeException failure) {
            diagnostic = SAVE_FAILED.getComponent(Component.literal(String.valueOf(failure.getMessage())));
        }
        updateRows();
    }

    @Override
    public void tick() {
        super.tick();
        search.tick();
        if (++ticks % 20 == 0 && selection.refresh()) updateRows();
    }

    @Override
    public void onClose() {
        SFMScreenChangeHelpers.popScreen();
    }

    @Override
    @MCVersionDependentBehaviour
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        drawLine(pose, title, 10);
        drawLine(pose, (selection.configuredAvailable() ? CURRENT : UNAVAILABLE)
                .getComponent(Component.literal(selection.configuredId())), 24);
        drawLine(pose, NEXT_OPEN.getComponent(), 36);
        drawLine(pose, diagnostic != null ? diagnostic : SELECTED.getComponent(Component.literal(selection.selectedId())), 48);
        if (selection.visibleIds().isEmpty()) drawLine(pose, EMPTY.getComponent(), 88);
        drawLine(pose, Component.literal((selection.page() + 1) + " / " + selection.pageCount()), height - 46);
    }

    @MCVersionDependentBehaviour
    private void drawLine(PoseStack pose, Component text, int y) {
        String visible = font.plainSubstrByWidth(text.getString(), Math.max(1, width - 24));
{% if features.canvas_text_editor %}
        SFMFontUtils.draw(pose, font, visible, (width - font.width(visible)) / 2, y, 0xFFFFFF, true);
{% else %}
        drawString(pose, font, visible, (width - font.width(visible)) / 2, y, 0xFFFFFF);
{% endif %}
    }
}
