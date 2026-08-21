package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMTrajectoryMachineAction;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionButton;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelActionExecution;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Live, push-fed panel for committed history and projected trajectory/search state. */
public final class SFMHistoryGraphPanel implements SFMScreenPanel, SFMEpisodeContext {
    private static final int BACKGROUND = 0xF0101218;
    private static final int HEADER = 0xFFEDF6F7;
    private static final int MUTED = 0xFF93A7AC;
    private static final int SELECTED = 0xFF315E88;
    private static final int COMMITTED = 0xFF71D790;
    private static final int EXECUTED_PREFIX = 0xFF63D7D0;
    private static final int PROJECTED = 0xFF72B7FF;
    private static final int FRONTIER = 0xFFFFC35A;
    private static final int BARRIER = 0xFFFF7373;
    private static final int TARGET = 0xFFFF8FE1;
    private static final int RETAINED = 0xFFC39BFF;
    private static final int CLOSED = 0xFF74858A;
    private static final int PADDING = 6;
    private static final int CONTROL_HEIGHT = 18;
    private static final ResourceLocation DEFAULT_USAGE = new ResourceLocation(SFM.MOD_ID, "default");

    private final SFMHistoryGraphRuntime runtime;
    private final SFMEntitySelector episodeSelector;
    private final SFMHistoryGraphPanelModel model = new SFMHistoryGraphPanelModel();
    private final SFMPanelWidgetHost widgetHost = new SFMPanelWidgetHost();
    private final List<SFMPanelActionButton> controls = new ArrayList<>();
    private final Consumer<String> actionOverride;
    private final Executor catalogEventHandoff;
    private final Object subscriptionLock = new Object();
    private volatile @Nullable SFMHistoryGraphRuntime.MachineSnapshot displayedSnapshot;
    private @Nullable SFMHistoryGraphRuntime.MachineSnapshot presentedSnapshot;
    private final AtomicReference<SFMHistoryGraphRuntime.CatalogEvent> pendingCatalogEvent =
            new AtomicReference<>();
    private volatile long catalogRevision = -1;
    private @Nullable SFMHistoryGraphRuntime.Subscription subscription;
    private long subscriptionGeneration;
    private @Nullable SFMWorkspacePanelContext context;
    private @Nullable Minecraft minecraft;
    private int bodyTop;
    private int bodyLineHeight = 11;
    private int bodyCapacity;
    private int bodyVisibleRows;
    private String feedback = "";
    private String selectionMessage = "Waiting for an episode snapshot";
    private String projectionMessage = "";

    public SFMHistoryGraphPanel(SFMEntitySelector episodeSelector) {
        this(SFMHistoryGraphRuntime.get(), episodeSelector, null, Runnable::run);
    }

    SFMHistoryGraphPanel(
            SFMHistoryGraphRuntime runtime,
            SFMEntitySelector episodeSelector,
            @Nullable Consumer<String> actionOverride
    ) {
        this(runtime, episodeSelector, actionOverride, Runnable::run);
    }

    SFMHistoryGraphPanel(
            SFMHistoryGraphRuntime runtime,
            SFMEntitySelector episodeSelector,
            @Nullable Consumer<String> actionOverride,
            Executor catalogEventHandoff
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.episodeSelector = Objects.requireNonNull(episodeSelector, "episodeSelector");
        if (episodeSelector.domain() != SFMEntitySelector.Domain.EPISODE) {
            throw new IllegalArgumentException("History Graph panel requires an episode selector");
        }
        this.actionOverride = actionOverride;
        this.catalogEventHandoff = Objects.requireNonNull(catalogEventHandoff, "catalogEventHandoff");
        for (SFMTrajectoryMachineAction.Kind kind : SFMTrajectoryMachineAction.Kind.values()) {
            SFMPanelActionButton control = new SFMPanelActionButton(
                    new ResourceLocation(SFM.MOD_ID, "episode/history/control/"
                            + kind.path().replace('/', '_')),
                    DEFAULT_USAGE,
                    Component.literal(shortLabel(kind)),
                    () -> Component.literal(kind.title() + " for "
                            + displayedMachineId().orElse("no active machine")),
                    () -> actionDraft(kind),
                    () -> invoke(kind)
            );
            controls.add(control);
        }
        widgetHost.setChildren(controls);
        controls.forEach(control -> control.active = false);
    }

    @Override
    public Component title() {
        return Component.literal("History Graph");
    }

    @Override
    public Component narration() {
        String machine = displayedMachineId().orElse("no active trajectory machine");
        String selected = model.selected()
                .map(SFMHistoryGraphPanelModel.Row::accessibleNarration)
                .orElse("No graph entity selected.");
        return Component.literal("History Graph for " + machine + ". " + selected);
    }

    @Override
    public Optional<SFMPanelWidgetHost> widgetHost() {
        return Optional.of(widgetHost);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.minecraft = minecraft;
        this.context = context;
        SFMHistoryGraphRuntime.Subscription previous;
        long generation;
        synchronized (subscriptionLock) {
            previous = subscription;
            subscription = null;
            generation = ++subscriptionGeneration;
            pendingCatalogEvent.set(null);
            catalogRevision = -1;
        }
        if (previous != null) previous.close();
        SFMHistoryGraphRuntime.Subscription openedSubscription = runtime.subscribe(event ->
                catalogEventHandoff.execute(() -> acceptCatalog(generation, event)));
        boolean retained;
        synchronized (subscriptionLock) {
            retained = generation == subscriptionGeneration;
            if (retained) subscription = openedSubscription;
        }
        if (!retained) openedSubscription.close();
    }

    @Override
    public void closed() {
        SFMHistoryGraphRuntime.Subscription closing;
        synchronized (subscriptionLock) {
            subscriptionGeneration++;
            closing = subscription;
            subscription = null;
            pendingCatalogEvent.set(null);
            catalogRevision = -1;
        }
        if (closing != null) closing.close();
        context = null;
        minecraft = null;
        displayedSnapshot = null;
        presentedSnapshot = null;
        model.clear();
        selectionMessage = "Waiting for an episode snapshot";
        projectionMessage = "";
        widgetHost.closed();
    }

    public Optional<String> displayedMachineId() {
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = displayedSnapshot;
        return snapshot == null ? Optional.empty() : Optional.of(snapshot.machineId());
    }

    @Override
    public Optional<String> episodeId() {
        return displayedMachineId();
    }

    public long catalogRevision() {
        return catalogRevision;
    }

    @Override
    public void tick() {
        SFMHistoryGraphRuntime.CatalogEvent event = pendingCatalogEvent.getAndSet(null);
        if (event != null && event.revision() > catalogRevision) applyCatalog(event);
        refreshPresentation();
    }

    SFMHistoryGraphPanelModel model() {
        return model;
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(),
                bounds.x() + bounds.width(), bounds.y() + bounds.height(), BACKGROUND);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + PADDING;
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        SFMFontUtils.draw(poseStack, minecraft.font, "History Graph", x, y, HEADER, false);
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = displayedSnapshot;
        String status = snapshot == null
                ? selectionMessage
                : snapshot.machineId() + " · " + snapshot.machine().status() + " · " + snapshot.summary()
                        + (projectionMessage.isBlank() ? "" : " · " + projectionMessage);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(status, Math.max(0, bounds.width() - PADDING * 2)),
                x, y + line, snapshot == null ? MUTED : HEADER, false);
        y += line * 2 + 2;

        y += layoutControls(bounds, y) + 4;
        drawLegend(poseStack, minecraft, bounds, x, y);
        y += line * 2 + 2;

        bodyTop = y;
        bodyLineHeight = line;
        int availableHeight = Math.max(0, bounds.y() + bounds.height() - y);
        int requestedDetailLines = model.selected().map(row -> Math.min(5, row.details().size()) + 1).orElse(1);
        int requestedDetailHeight = requestedDetailLines * line + 6;
        int minimumBodyHeight = model.rows().isEmpty() ? 0 : line;
        int detailHeight = Math.min(requestedDetailHeight, Math.max(0, availableHeight - minimumBodyHeight));
        bodyCapacity = Math.max(0, (availableHeight - detailHeight) / line);
        List<SFMHistoryGraphPanelModel.Row> visible = model.visibleRows(bodyCapacity);
        bodyVisibleRows = visible.size();
        int first = model.firstVisibleIndex(bodyCapacity);
        for (int rowOffset = 0; rowOffset < visible.size(); rowOffset++) {
            int rowIndex = first + rowOffset;
            SFMHistoryGraphPanelModel.Row row = visible.get(rowOffset);
            int rowY = y + rowOffset * line;
            if (rowIndex == model.selectedIndex()) {
                GuiComponent.fill(poseStack, bounds.x() + 2, rowY - 1,
                        bounds.x() + bounds.width() - 2, rowY + line - 1, SELECTED);
            }
            int indent = Math.min(Math.max(0, bounds.width() / 3), row.depth() * 8);
            String prefix = rowPrefix(row);
            if (row.actualHead()) prefix = "H " + prefix;
            if (row.instructionPointer()) prefix = "IP " + prefix;
            String text = prefix + row.label();
            int available = Math.max(0, bounds.width() - PADDING * 2 - indent);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(text, available),
                    x + indent, rowY, roleColour(row.roles()), false);
        }

        int detailsY = bounds.y() + bounds.height() - detailHeight;
        if (detailHeight > 0) {
            GuiComponent.fill(poseStack, bounds.x() + 2, detailsY,
                    bounds.x() + bounds.width() - 2, bounds.y() + bounds.height() - 2, 0xE021252D);
            drawDetails(poseStack, minecraft, bounds, x, detailsY + 2, line,
                    Math.max(0, detailHeight / line));
        }
        if (!feedback.isBlank()) {
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(feedback, Math.max(0, bounds.width() - PADDING * 2)),
                    x, bounds.y() + bounds.height() - line - 2, FRONTIER, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.selectPrevious();
            case GLFW.GLFW_KEY_DOWN -> model.selectNext();
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
            case GLFW.GLFW_KEY_PAGE_UP -> model.selectIndex(model.selectedIndex() - Math.max(1, bodyCapacity));
            case GLFW.GLFW_KEY_PAGE_DOWN -> model.selectIndex(model.selectedIndex() + Math.max(1, bodyCapacity));
            default -> false;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseY < bodyTop) return false;
        int offset = (int) ((mouseY - bodyTop) / Math.max(1, bodyLineHeight));
        if (offset < 0 || offset >= bodyVisibleRows) return false;
        return model.selectIndex(model.firstVisibleIndex(bodyCapacity) + offset);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0 || model.rows().isEmpty()) return false;
        int steps = Math.max(1, (int) Math.round(Math.abs(delta)));
        boolean changed = false;
        for (int index = 0; index < steps; index++) {
            changed |= delta > 0 ? model.selectPrevious() : model.selectNext();
        }
        return changed;
    }

    private void acceptCatalog(long generation, SFMHistoryGraphRuntime.CatalogEvent event) {
        synchronized (subscriptionLock) {
            if (generation != subscriptionGeneration) return;
            pendingCatalogEvent.accumulateAndGet(event, (previous, incoming) ->
                    previous == null || incoming.revision() >= previous.revision() ? incoming : previous);
        }
    }

    private void applyCatalog(SFMHistoryGraphRuntime.CatalogEvent event) {
        catalogRevision = event.revision();
        List<SFMHistoryGraphRuntime.MachineSnapshot> matches = event.resolve(episodeSelector);
        Optional<SFMHistoryGraphRuntime.MachineSnapshot> next = matches.size() == 1
                ? Optional.of(matches.get(0))
                : Optional.empty();
        selectionMessage = matches.isEmpty()
                ? "Episode selector " + episodeSelector.canonical() + " matched nothing; waiting for a push."
                : matches.size() == 1
                ? "Episode selected"
                : "Episode selector " + episodeSelector.canonical() + " matched " + matches.size()
                        + " episodes; use an exact id(...).";
        displayedSnapshot = next.orElse(null);
        presentedSnapshot = null;
        projectionMessage = next.isPresent() ? "preparing presentation" : "";
        model.clear();
        boolean enabled = next.isPresent();
        controls.forEach(control -> control.active = enabled);
    }

    private void refreshPresentation() {
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = displayedSnapshot;
        if (snapshot == null || snapshot == presentedSnapshot) return;
        SFMHistoryGraphRuntime.PresentationState state = snapshot.presentationState();
        if (state instanceof SFMHistoryGraphRuntime.PresentationPending) {
            projectionMessage = "preparing presentation";
            return;
        }
        if (state instanceof SFMHistoryGraphRuntime.PresentationReady ready) {
            if (snapshot != displayedSnapshot) return;
            model.update(ready.presentation());
            presentedSnapshot = snapshot;
            projectionMessage = "";
            return;
        }
        SFMHistoryGraphRuntime.PresentationFailed failed =
                (SFMHistoryGraphRuntime.PresentationFailed) state;
        if (snapshot != displayedSnapshot) return;
        model.clear();
        presentedSnapshot = snapshot;
        projectionMessage = "presentation failed: " + failed.message();
    }

    int layoutControls(SFMScreenPanelBounds bounds, int y) {
        List<SFMScreenPanelBounds> layout = controlBounds(bounds, y, controls.size());
        for (int index = 0; index < controls.size(); index++) {
            SFMPanelActionButton control = controls.get(index);
            SFMScreenPanelBounds controlBounds = layout.get(index);
            control.visible = controlBounds.width() > 0;
            control.setPanelBounds(controlBounds);
        }
        if (layout.isEmpty()) return 0;
        SFMScreenPanelBounds last = layout.get(layout.size() - 1);
        return last.y() + last.height() - y;
    }

    static List<SFMScreenPanelBounds> controlBounds(
            SFMScreenPanelBounds bounds,
            int y,
            int controlCount
    ) {
        if (controlCount < 0) throw new IllegalArgumentException("controlCount must not be negative");
        if (controlCount == 0) return List.of();
        int gap = 2;
        int minimumWidth = 42;
        int horizontalPadding = Math.min(PADDING, Math.max(0, bounds.width() - 1) / 2);
        int contentX = bounds.x() + horizontalPadding;
        int contentWidth = Math.max(1, bounds.width() - horizontalPadding * 2);
        int preferredColumns = Math.min(
                controlCount,
                Math.max(1, (contentWidth + gap) / (minimumWidth + gap))
        );
        int availableHeight = Math.max(0, bounds.y() + bounds.height() - y);
        int maximumRows = Math.max(1, (availableHeight + gap) / (CONTROL_HEIGHT + gap));
        int minimumColumnsForHeight = (controlCount + maximumRows - 1) / maximumRows;
        int columns = Math.min(controlCount, Math.max(preferredColumns, minimumColumnsForHeight));
        ArrayList<SFMScreenPanelBounds> answer = new ArrayList<>(controlCount);
        for (int first = 0, row = 0; first < controlCount; first += columns, row++) {
            int rowCount = Math.min(columns, controlCount - first);
            int widthWithoutGaps = Math.max(rowCount, contentWidth - gap * (rowCount - 1));
            int baseWidth = widthWithoutGaps / rowCount;
            int extraPixels = widthWithoutGaps % rowCount;
            int x = contentX;
            for (int column = 0; column < rowCount; column++) {
                int itemWidth = baseWidth + (column < extraPixels ? 1 : 0);
                answer.add(new SFMScreenPanelBounds(
                        x,
                        y + row * (CONTROL_HEIGHT + gap),
                        itemWidth,
                        CONTROL_HEIGHT
                ));
                x += itemWidth + gap;
            }
        }
        return List.copyOf(answer);
    }

    private void drawLegend(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y
    ) {
        String first = "green committed · teal executed · blue projected · purple retained";
        String second = "H head · IP next · amber frontier · pink target · gray closed · red barrier";
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(first, Math.max(0, bounds.width() - PADDING * 2)),
                x, y, MUTED, false);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(second, Math.max(0, bounds.width() - PADDING * 2)),
                x, y + Math.max(10, minecraft.font.lineHeight + 2), MUTED, false);
    }

    private void drawDetails(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int x,
            int y,
            int line,
            int availableLines
    ) {
        if (availableLines <= 0) return;
        Optional<SFMHistoryGraphPanelModel.Row> selected = model.selected();
        if (selected.isEmpty()) {
            SFMFontUtils.draw(poseStack, minecraft.font, "No graph entity selected", x, y, MUTED, false);
            return;
        }
        SFMHistoryGraphPanelModel.Row row = selected.orElseThrow();
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(row.narration(), Math.max(0, bounds.width() - PADDING * 2)),
                x, y, HEADER, false);
        int detailY = y + line;
        for (SFMHistoryGraphPresentationModel.Detail detail : visibleDetails(
                row,
                Math.max(0, availableLines - 1)
        )) {
            String text = detail.key() + ": " + detail.value();
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(text, Math.max(0, bounds.width() - PADDING * 2)),
                    x, detailY, MUTED, false);
            detailY += line;
        }
    }

    private String actionDraft(SFMTrajectoryMachineAction.Kind kind) {
        String machine = displayedMachineId()
                .orElse("unavailable");
        String selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                machine
        ).canonical();
        return "sfm action invoke " + kind.actionId() + " " + selector;
    }

    private void invoke(SFMTrajectoryMachineAction.Kind kind) {
        if (displayedMachineId().isEmpty()) return;
        String draft = actionDraft(kind);
        if (actionOverride != null) {
            actionOverride.accept(draft);
            return;
        }
        if (context == null || minecraft == null) return;
        SFMPanelActionExecution.execute(context, minecraft, draft, message -> feedback = message.getString());
    }

    private static String shortLabel(SFMTrajectoryMachineAction.Kind kind) {
        return switch (kind) {
            case PLAN -> "Plan";
            case STEP -> "Step";
            case RUN -> "Run";
            case PAUSE -> "Pause";
            case REPLAN -> "Replan";
            case SELECT_ROUTE -> "Route";
            case INSPECT_COST -> "Cost";
        };
    }

    static List<SFMHistoryGraphPresentationModel.Detail> visibleDetails(
            SFMHistoryGraphPanelModel.Row row,
            int maximum
    ) {
        if (maximum <= 0) return List.of();
        return row.details().stream()
                .sorted(Comparator
                        .comparingInt((SFMHistoryGraphPresentationModel.Detail detail) ->
                                detailPriority(detail.key()))
                        .thenComparing(SFMHistoryGraphPresentationModel.Detail::key))
                .limit(maximum)
                .toList();
    }

    private static int detailPriority(String key) {
        if (key.endsWith(".g")) return 0;
        if (key.endsWith(".h")) return 1;
        if (key.endsWith(".f")) return 2;
        if (key.contains("action")) return 3;
        if (key.endsWith("status")) return 4;
        if (key.contains("effect-class")) return 5;
        return 10;
    }

    private static int roleColour(List<SFMHistoryGraphPresentationModel.LegendRole> roles) {
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.BARRIER)) return BARRIER;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.TARGET)) return TARGET;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.OPEN_FRONTIER)) return FRONTIER;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX)) {
            return EXECUTED_PREFIX;
        }
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX)) return PROJECTED;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED)) return COMMITTED;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.CLOSED)) return CLOSED;
        if (roles.contains(SFMHistoryGraphPresentationModel.LegendRole.RETAINED_ALTERNATIVE)) return RETAINED;
        return HEADER;
    }

    private static String rowPrefix(SFMHistoryGraphPanelModel.Row row) {
        boolean committed = row.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.COMMITTED_EXECUTED);
        boolean executedPrefix = row.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_EXECUTED_PREFIX);
        boolean selectedProjection = row.roles().contains(
                SFMHistoryGraphPresentationModel.LegendRole.SELECTED_PROJECTED_SUFFIX);
        if (row.kind() == SFMHistoryGraphPanelModel.RowKind.NODE) {
            return committed ? "◆ " : executedPrefix ? "▶ " : selectedProjection ? "◇ " : "○ ";
        }
        return committed ? "━→ " : executedPrefix ? "═→ " : selectedProjection ? "┆→ " : "┄→ ";
    }
}
