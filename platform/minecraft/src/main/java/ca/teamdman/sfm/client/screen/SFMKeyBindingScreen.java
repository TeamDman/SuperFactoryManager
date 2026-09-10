package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMCommandPaletteActions;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingListModel;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.screen.widget.SFMExtendedButtonWithTooltip;
import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class SFMKeyBindingScreen extends Screen {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.keybindings.title", "SFM Shortcuts");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SEARCH = new LocalizationEntry(
            "gui.sfm.keybindings.search", "Search actions");
    @SFMLocalizationDatagen
    public static final LocalizationEntry PERSISTENCE_WARNING = new LocalizationEntry(
            "gui.sfm.keybindings.persistence_warning",
            "Binding file needs recovery; edits are session-only");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_NAME = new LocalizationEntry(
            "gui.sfm.keybindings.sort.name", "Name");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_COUNT = new LocalizationEntry(
            "gui.sfm.keybindings.sort.binding_count", "Binding count");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_NAME_ASC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.name.asc", ">Name (Asc)<");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_NAME_DESC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.name.desc", ">Name (Desc)<");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_COUNT_ASC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.binding_count.asc", ">Binding count (Asc)<");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_COUNT_DESC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.binding_count.desc", ">Binding count (Desc)<");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_TOOLTIP_ASC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.tooltip.ascending",
            "Sort by %s. Ascending. Click to sort descending.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_TOOLTIP_DESC = new LocalizationEntry(
            "gui.sfm.keybindings.sort.tooltip.descending",
            "Sort by %s. Descending. Click to sort ascending.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SORT_TOOLTIP_INACTIVE = new LocalizationEntry(
            "gui.sfm.keybindings.sort.tooltip.inactive",
            "Sort by %s. Ascending when selected.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISPLAY_NAMES = new LocalizationEntry(
            "gui.sfm.keybindings.display.names", "Showing names");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISPLAY_IDS = new LocalizationEntry(
            "gui.sfm.keybindings.display.ids", "Showing IDs");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISPLAY_NAMES_TOOLTIP = new LocalizationEntry(
            "gui.sfm.keybindings.display.names.tooltip",
            "Rows show display names; hover reveals raw action IDs");
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISPLAY_IDS_TOOLTIP = new LocalizationEntry(
            "gui.sfm.keybindings.display.ids.tooltip",
            "Rows show raw action IDs; hover reveals display names");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_ALL = new LocalizationEntry(
            "gui.sfm.keybindings.scope.all", "Scope: all situations");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_SELECTED = new LocalizationEntry(
            "gui.sfm.keybindings.scope.selected", "Scope: %s");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_TOOLTIP = new LocalizationEntry(
            "gui.sfm.keybindings.scope.tooltip",
            "Left click cycles scopes. Right click lists every scope.");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_CHOICE_TITLE = new LocalizationEntry(
            "gui.sfm.keybindings.scope.choice.title", "Choose key-binding scope");
    @SFMLocalizationDatagen
    public static final LocalizationEntry SCOPE_CHOICE_ALL = new LocalizationEntry(
            "gui.sfm.keybindings.scope.choice.all", "All situations");
    @SFMLocalizationDatagen
    public static final LocalizationEntry BINDING_COUNT = new LocalizationEntry(
            "gui.sfm.keybindings.row.binding_count", "%1$s/%2$s bindings   [?]");

    static final int LIST_TOP = 88;
    static final int LIST_BOTTOM_MARGIN = 28;
    static final int ROW_STRIDE = 24;
    static final int ROW_HEIGHT = 22;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    private EditBox search;
    private List<ResourceLocation> visibleActions = List.of();
    private final SFMVerticalListViewport viewport = new SFMVerticalListViewport();
    private final SFMKeyBindingListModel listModel = new SFMKeyBindingListModel(
            id -> SFMKeyBindingService.INSTANCE.bindingsForAction(id));
    private String query = "";
    private final boolean pushed;
    private Button nameSortButton;
    private Button bindingCountSortButton;
    private Button scopeButton;
    private Button identityButton;
    private IdentityMode identityMode = IdentityMode.DISPLAY_NAME;

    public enum IdentityMode {
        DISPLAY_NAME,
        ACTION_ID
    }

    public SFMKeyBindingScreen() {
        super(TITLE.getComponent());
        this.pushed = SFMScreenChangeHelpers.getCurrentScreen() != null;
    }

    @Override
    protected void init() {
        int searchWidth = Math.max(20, Math.min(300, width - 24));
        search = addRenderableWidget(new ca.teamdman.sfm.client.input.SFMSingleLineEditBox(font, (width - searchWidth) / 2, 34, searchWidth, 20,
                SEARCH.getComponent()));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refresh();
        });
        int left = Math.max(12, (width - Math.min(420, width - 24)) / 2);
        nameSortButton = addRenderableWidget(new SFMExtendedButtonWithTooltip(
                left, 62, 112, 20, sortLabel(SFMKeyBindingListModel.SortColumn.NAME),
                button -> {
                    listModel.toggleSort(SFMKeyBindingListModel.SortColumn.NAME);
                    updateControlLabels();
                    refresh();
                },
                (button, poseStack, mouseX, mouseY) -> renderTooltip(
                        poseStack,
                        sortTooltip(SFMKeyBindingListModel.SortColumn.NAME),
                        mouseX,
                        mouseY)));
        bindingCountSortButton = addRenderableWidget(new SFMExtendedButtonWithTooltip(
                left + 116, 62, 130, 20,
                sortLabel(SFMKeyBindingListModel.SortColumn.BINDING_COUNT),
                button -> {
                    listModel.toggleSort(SFMKeyBindingListModel.SortColumn.BINDING_COUNT);
                    updateControlLabels();
                    refresh();
                },
                (button, poseStack, mouseX, mouseY) -> renderTooltip(
                        poseStack,
                        sortTooltip(SFMKeyBindingListModel.SortColumn.BINDING_COUNT),
                        mouseX,
                        mouseY)));
        scopeButton = addRenderableWidget(new SFMExtendedButtonWithTooltip(
                left + 250, 62, 170, 20, scopeLabel(),
                button -> {
                    cycleScope();
                    updateControlLabels();
                    refresh();
                },
                (button, poseStack, mouseX, mouseY) -> renderTooltip(
                        poseStack,
                        SCOPE_TOOLTIP.getComponent(),
                        mouseX,
                        mouseY)));
        identityButton = addRenderableWidget(new SFMExtendedButtonWithTooltip(
                Math.max(4, width - 112), 8, 108, 20, identityLabel(),
                button -> setIdentityMode(identityMode == IdentityMode.DISPLAY_NAME
                        ? IdentityMode.ACTION_ID
                        : IdentityMode.DISPLAY_NAME),
                (button, poseStack, mouseX, mouseY) -> renderTooltip(
                        poseStack,
                        identityMode == IdentityMode.DISPLAY_NAME
                                ? DISPLAY_NAMES_TOOLTIP.getComponent()
                                : DISPLAY_IDS_TOOLTIP.getComponent(),
                        mouseX,
                        mouseY)));
        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        ResourceLocation previousSelection = selectedAction();
        int previousFirstVisible = viewport.firstVisibleRow();
        listModel.setActions(SFMClientActions.registry().keys().stream().toList());
        listModel.setQuery(query);
        visibleActions = listModel.visibleActions(
                id -> Optional.ofNullable(SFMClientActions.registry().get(id))
                        .map(action -> action.title().getString()).orElse(id.toString()),
                id -> Optional.ofNullable(SFMClientActions.registry().get(id))
                        .map(action -> action.description().getString()).orElse("")).stream().toList();
        viewport.configure(visibleActions.size(), visibleRowCount(height));
        if (visibleActions.isEmpty()) {
            viewport.select(SFMVerticalListViewport.NO_SELECTION);
            return;
        }
        int selectedIndex = previousSelection == null ? -1 : visibleActions.indexOf(previousSelection);
        if (selectedIndex < 0) selectedIndex = Math.max(0, viewport.selectedRow());
        viewport.select(Math.min(selectedIndex, visibleActions.size() - 1));
        viewport.scrollRows(previousFirstVisible - viewport.firstVisibleRow());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        Component heading = title.copy().withStyle(ChatFormatting.BOLD);
        SFMFontUtils.draw(poseStack, font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        if (!SFMKeyBindingService.INSTANCE.persistenceWritable()) {
            String warning = PERSISTENCE_WARNING.getComponent().getString();
            SFMFontUtils.draw(poseStack, font, warning,
                    width / 2 - font.width(warning) / 2, 24, 0xFFFF7777, true);
        }
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = scrollbarGeometry();
        SFMVerticalListViewport.Bounds rows = rowBounds(scrollbar.visible());
        OptionalInt hoveredRow = viewport.rowAt(mouseX, mouseY, rows, ROW_STRIDE, ROW_HEIGHT);
        for (int index = viewport.firstVisibleRow(); index < viewport.lastVisibleRowExclusive(); index++) {
            ResourceLocation actionId = visibleActions.get(index);
            var action = SFMClientActions.registry().get(actionId);
            if (action == null) continue;
            int rowTop = LIST_TOP + (index - viewport.firstVisibleRow()) * ROW_STRIDE;
            boolean hovered = hoveredRow.isPresent() && hoveredRow.getAsInt() == index;
            boolean selected = viewport.selectedRow() == index;
            int background = selected ? 0xFF315A70 : hovered ? 0xFF404040 : 0xCC252525;
            fill(poseStack, rows.x(), rowTop, rows.x() + rows.width(), rowTop + ROW_HEIGHT, background);
            String primaryIdentity = identityMode == IdentityMode.DISPLAY_NAME
                    ? action.title().getString()
                    : actionId.toString();
            String actionTitle = font.plainSubstrByWidth(primaryIdentity, Math.max(20, rows.width() - 145));
            SFMFontUtils.draw(poseStack, font, actionTitle, rows.x() + 6, rowTop + 6, 0xFFFFFFFF, false);
            int total = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId).size();
            int visible = listModel.filteredBindings(actionId).size();
            String count = BINDING_COUNT.getComponent(visible, total).getString();
            SFMFontUtils.draw(poseStack, font, count, rows.x() + rows.width() - 6 - font.width(count), rowTop + 6,
                    0xFF80D8FF, false);
        }
        renderScrollbar(poseStack, mouseX, mouseY, scrollbar);
        super.render(poseStack, mouseX, mouseY, partialTick);
        SFMWidgetUtils.hideTooltipsWhenNotFocused(this, this.renderables);
        SFMWidgetUtils.renderChildTooltips(poseStack, mouseX, mouseY, this.renderables);
        if (hoveredRow.isPresent()) {
            ResourceLocation actionId = visibleActions.get(hoveredRow.getAsInt());
            var action = SFMClientActions.registry().get(actionId);
            if (action != null) {
                String alternateIdentity = identityMode == IdentityMode.DISPLAY_NAME
                        ? actionId.toString()
                        : action.title().getString();
                renderTooltip(poseStack, Component.literal(alternateIdentity), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && scopeButton != null
                && scopeButton.isMouseOver(mouseX, mouseY)) {
            openScopeChoices();
            return true;
        }
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = scrollbarGeometry();
        if (viewport.mouseClickedScrollbar(mouseX, mouseY, button, scrollbar)) return true;
        OptionalInt row = actionRowAt(viewport, mouseX, mouseY, width, height, scrollbar.visible());
        if (button == 0 && row.isPresent()) {
            viewport.select(row.getAsInt());
            openSelectedAction();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return viewport.mouseReleasedScrollbar(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return viewport.mouseDraggedScrollbar(mouseY, button, scrollbarGeometry())
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (listBounds().contains(mouseX, mouseY) && viewport.scrollWheel(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (viewport.itemCount() > 0) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                viewport.moveSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                viewport.moveSelection(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                viewport.pageSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                viewport.pageSelection(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                viewport.selectFirst();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                viewport.selectLast();
                return true;
            }
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && viewport.selectedRow() != SFMVerticalListViewport.NO_SELECTION) {
            openSelectedAction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openSelectedAction() {
        ResourceLocation selected = selectedAction();
        if (selected != null) {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, selected));
        }
    }

    private ResourceLocation selectedAction() {
        int selected = viewport.selectedRow();
        return selected >= 0 && selected < visibleActions.size() ? visibleActions.get(selected) : null;
    }

    private SFMVerticalListViewport.ScrollbarGeometry scrollbarGeometry() {
        SFMVerticalListViewport.Bounds list = listBounds();
        int trackWidth = Math.min(SCROLLBAR_WIDTH, list.width());
        return viewport.scrollbarGeometry(new SFMVerticalListViewport.Bounds(
                list.x() + list.width() - trackWidth,
                list.y(),
                trackWidth,
                list.height()
        ), SCROLLBAR_MIN_THUMB_HEIGHT);
    }

    private SFMVerticalListViewport.Bounds rowBounds(boolean scrollbarVisible) {
        return rowBounds(width, height, scrollbarVisible);
    }

    private SFMVerticalListViewport.Bounds listBounds() {
        return listBounds(width, height);
    }

    private void renderScrollbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            SFMVerticalListViewport.ScrollbarGeometry geometry
    ) {
        if (!geometry.visible()) return;
        var track = geometry.track();
        var thumb = geometry.thumb();
        fill(poseStack, track.x(), track.y(), track.x() + track.width(), track.y() + track.height(), 0x55303030);
        boolean hovered = thumb.contains(mouseX, mouseY);
        fill(
                poseStack,
                thumb.x(),
                thumb.y(),
                thumb.x() + thumb.width(),
                thumb.y() + thumb.height(),
                hovered || viewport.isScrollbarDragActive() ? 0xFFAAAAAA : 0xFF707070
        );
    }

    static int visibleRowCount(int screenHeight) {
        return Math.max(0, (screenHeight - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_STRIDE);
    }

    static SFMVerticalListViewport.Bounds listBounds(int screenWidth, int screenHeight) {
        int rowWidth = Math.max(0, Math.min(420, screenWidth - 24));
        int left = (screenWidth - rowWidth) / 2;
        return new SFMVerticalListViewport.Bounds(
                left,
                LIST_TOP,
                rowWidth,
                visibleRowCount(screenHeight) * ROW_STRIDE
        );
    }

    static SFMVerticalListViewport.Bounds rowBounds(int screenWidth, int screenHeight, boolean scrollbarVisible) {
        SFMVerticalListViewport.Bounds list = listBounds(screenWidth, screenHeight);
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        return new SFMVerticalListViewport.Bounds(
                list.x(),
                list.y(),
                Math.max(0, list.width() - scrollbarSpace),
                list.height()
        );
    }

    static OptionalInt actionRowAt(
            SFMVerticalListViewport viewport,
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            boolean scrollbarVisible
    ) {
        return viewport.rowAt(
                mouseX,
                mouseY,
                rowBounds(screenWidth, screenHeight, scrollbarVisible),
                ROW_STRIDE,
                ROW_HEIGHT
        );
    }

    private void cycleScope() {
        List<ResourceLocation> scopes = SFMKeyBindingService.INSTANCE.situationIds();
        if (scopes.isEmpty()) return;
        Optional<ResourceLocation> current = listModel.situationFilter();
        if (current.isEmpty()) {
            listModel.setSituationFilter(scopes.get(0));
            return;
        }
        int index = scopes.indexOf(current.get());
        if (index < 0 || index + 1 >= scopes.size()) listModel.setSituationFilter(null);
        else listModel.setSituationFilter(scopes.get(index + 1));
    }

    public void setSort(
            SFMKeyBindingListModel.SortColumn column,
            SFMKeyBindingListModel.Direction direction
    ) {
        listModel.setSort(column, direction);
        updateControlLabels();
        refresh();
    }

    public void setSituationFilter(@Nullable ResourceLocation situationId) {
        listModel.setSituationFilter(situationId);
        updateControlLabels();
        refresh();
    }

    public void setIdentityMode(IdentityMode identityMode) {
        this.identityMode = java.util.Objects.requireNonNull(identityMode);
        updateControlLabels();
        refresh();
    }

    private void openScopeChoices() {
        ResourceLocation actionId = SFMCommandPaletteActions.KEY_BINDINGS_SCOPE_SET
                .getId().orElseThrow().location();
        java.util.ArrayList<SFMActionChoice> choices = new java.util.ArrayList<>();
        choices.add(SFMActionChoice.invoke(actionId, "all", SCOPE_CHOICE_ALL.getComponent().getString()));
        for (ResourceLocation situationId : SFMKeyBindingService.INSTANCE.situationIds()) {
            String title = SFMKeyBindingService.INSTANCE.situation(situationId)
                    .map(situation -> situation.title().getString())
                    .orElse(situationId.toString());
            choices.add(SFMActionChoice.invoke(actionId, situationId.toString(), title));
        }
        SFMClientActionContext captured = SFMClientActionContext.create(this, () -> true);
        SFMCommandPaletteScreen.openChoices(
                captured,
                SCOPE_CHOICE_TITLE.getComponent(),
                choices,
                this::refresh
        );
    }

    private void updateControlLabels() {
        if (nameSortButton != null) {
            nameSortButton.setMessage(sortLabel(SFMKeyBindingListModel.SortColumn.NAME));
        }
        if (bindingCountSortButton != null) {
            bindingCountSortButton.setMessage(sortLabel(SFMKeyBindingListModel.SortColumn.BINDING_COUNT));
        }
        if (scopeButton != null) scopeButton.setMessage(scopeLabel());
        if (identityButton != null) identityButton.setMessage(identityLabel());
    }

    private Component sortLabel(SFMKeyBindingListModel.SortColumn column) {
        if (listModel.sortColumn() != column) {
            return column == SFMKeyBindingListModel.SortColumn.NAME
                    ? SORT_NAME.getComponent()
                    : SORT_COUNT.getComponent();
        }
        return switch (column) {
            case NAME -> listModel.direction() == SFMKeyBindingListModel.Direction.ASCENDING
                    ? SORT_NAME_ASC.getComponent()
                    : SORT_NAME_DESC.getComponent();
            case BINDING_COUNT -> listModel.direction() == SFMKeyBindingListModel.Direction.ASCENDING
                    ? SORT_COUNT_ASC.getComponent()
                    : SORT_COUNT_DESC.getComponent();
        };
    }

    private Component sortTooltip(SFMKeyBindingListModel.SortColumn column) {
        Component name = column == SFMKeyBindingListModel.SortColumn.NAME
                ? SORT_NAME.getComponent()
                : SORT_COUNT.getComponent();
        if (listModel.sortColumn() != column) return SORT_TOOLTIP_INACTIVE.getComponent(name);
        return listModel.direction() == SFMKeyBindingListModel.Direction.ASCENDING
                ? SORT_TOOLTIP_ASC.getComponent(name)
                : SORT_TOOLTIP_DESC.getComponent(name);
    }

    private Component identityLabel() {
        return identityMode == IdentityMode.DISPLAY_NAME
                ? DISPLAY_NAMES.getComponent()
                : DISPLAY_IDS.getComponent();
    }

    private Component scopeLabel() {
        return listModel.situationFilter()
                .map(id -> SCOPE_SELECTED.getComponent(SFMKeyBindingService.INSTANCE.situation(id)
                        .map(situation -> situation.title()).orElse(Component.literal(id.toString()))))
                .orElse(SCOPE_ALL.getComponent());
    }

    @Override
    public void onClose() {
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }
}
