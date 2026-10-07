package ca.teamdman.sfm.client.screen.history.chamber;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
{% if features.document_history %}
import ca.teamdman.sfm.client.history.SFMDocumentHistoryTarget;
{% endif %}
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.chamber.SFMChamberAmbientCheckoutProbe;
import ca.teamdman.sfm.client.history.chamber.SFMChamberDocumentState;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
{% if features.document_history %}
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
{% endif %}
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
{% if features.workspace_widget_hosts %}
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
{% endif %}
{% if features.workspace_lifecycle %}
import ca.teamdman.sfm.client.screen.workspace.SFMPanelCloseState;
{% endif %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
{% if features.java_symbols %}
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionEvidenceSource;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
{% endif %}
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Writable Text Editor V3 host for the bounded temporal-numbering chamber. */
{% if features.document_history %}
{% if features.java_symbols %}
public final class SFMDecimalNumberingChamberPanel implements SFMScreenPanel, SFMEpisodeContext,
        SFMDocumentHistoryTarget, SFMTextDocumentPanelState, SFMContextContributor,
        SFMSymbolInspectionEvidenceSource {
{% else %}
public final class SFMDecimalNumberingChamberPanel implements SFMScreenPanel, SFMEpisodeContext,
        SFMDocumentHistoryTarget, SFMTextDocumentPanelState, SFMContextContributor {
{% endif %}
{% else %}
{% if features.java_symbols %}
public final class SFMDecimalNumberingChamberPanel implements SFMScreenPanel, SFMEpisodeContext,
        SFMTextDocumentPanelState, SFMContextContributor,
        SFMSymbolInspectionEvidenceSource {
{% else %}
public final class SFMDecimalNumberingChamberPanel implements SFMScreenPanel, SFMEpisodeContext,
        SFMTextDocumentPanelState, SFMContextContributor {
{% endif %}
{% endif %}
    private static final AtomicLong NEXT_SESSION = new AtomicLong();
    private static final String DOCUMENT_ID = "document-1";

    private final SFMDecimalNumberingTrajectoryController controller;
    private final SFMTextEditorPanel editor;
    private SFMHistoryGraphRuntime.Registration registration;
{% if features.document_history %}
    private SFMDocumentHistoryRuntime.Registration documentHistoryRegistration;
{% endif %}
    private long observedControllerRevision = -1L;
    private boolean focused;
    private String lastOperation = "Ready for supervised planning";

{% if features.client_properties %}
    public static SFMDecimalNumberingChamberPanel createForCurrentCheckout() {
        long session = NEXT_SESSION.incrementAndGet();
        String episodeId = "sfm:chamber/temporal-numbering/session-" + session;
        SFMChamberAmbientCheckoutProbe probe = SFMChamberAmbientCheckoutProbe.discover(
                ca.teamdman.sfm.properties.SFMProperties.userDirectory()
        );
        return new SFMDecimalNumberingChamberPanel(new SFMDecimalNumberingTrajectoryController(
                episodeId,
                DOCUMENT_ID,
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                probe
        ));
    }
{% endif %}

    public SFMDecimalNumberingChamberPanel(SFMDecimalNumberingTrajectoryController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        editor = SFMTextEditorPanel.textEditorV3WithoutIndependentHistory(new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                controller.currentText(),
                false,
                "Temporal Numbering Chamber"
        ));
    }

    public SFMDecimalNumberingTrajectoryController controller() {
        return controller;
    }

    @Override
    public Optional<String> episodeId() {
        return Optional.of(controller.machineId());
    }

    @Override
    public Component title() {
        return Component.literal("Temporal Numbering Chamber");
    }

{% if features.keyboard_profiles %}
    @Override
    public ResourceLocation keyboardUsageSituationId() {
        return ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT;
    }
{% endif %}

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(". " + lastOperation));
    }

{% if features.workspace_widget_hosts %}
    @Override
    public Optional<SFMPanelWidgetHost> widgetHost() {
        return editor.widgetHost();
    }

    @Override
    public boolean widgetHostOwnsInput() {
        return editor.widgetHostOwnsInput();
    }
{% endif %}

    @Override
    public boolean isReadOnly() {
        return false;
    }

{% if features.workspace_lifecycle %}
    @Override
    public SFMPanelCloseState closeState() {
        return editor.closeState();
    }
{% endif %}

    @Override
    public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
        return editor.documentSnapshot();
    }

    @Override
    public boolean navigateToRange(SFMTextDocumentRange range) {
        return editor.navigateToRange(range);
    }

    @Override
    public String id() {
        return editor.id();
    }

    @Override
    public Optional<SFMContextOriginId> focusedOriginId() {
        return editor.focusedOriginId();
    }

    @Override
    public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        return editor.capture(request);
    }

{% if features.java_symbols %}
    @Override
    public Optional<SFMSymbolInspectionSnapshot.SemanticEvidence> captureSymbolInspectionEvidence(
            ca.teamdman.sfm.client.context.SFMContextDocumentProjection document,
            SFMSymbolInspectionSnapshot.CapturedPoint point
    ) {
        return editor.captureSymbolInspectionEvidence(document, point);
    }
{% endif %}

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        editor.opened(minecraft, bounds, context);
        registration = SFMHistoryGraphRuntime.get().register(controller);
{% if features.document_history %}
        documentHistoryRegistration = SFMDocumentHistoryRuntime.get()
                .registerFocused(controller.documentHistorySession());
{% endif %}
        SFMHistoryGraphRuntime.get().setActiveMachine(controller.machineId());
        checkoutControllerRevision();
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        editor.resized(minecraft, bounds);
    }

    @Override
    public void closed() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
{% if features.document_history %}
        if (documentHistoryRegistration != null) {
            documentHistoryRegistration.close();
            documentHistoryRegistration = null;
        }
{% endif %}
        editor.closed();
    }

    @Override
    public void tick() {
        editor.tick();
        synchronizeEditorAndController();
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        if (focused && !this.focused && registration != null) {
            SFMHistoryGraphRuntime.get().setActiveMachine(controller.machineId());
{% if features.document_history %}
            if (documentHistoryRegistration != null) documentHistoryRegistration.focus();
{% endif %}
        }
        this.focused = focused;
        editor.render(poseStack, minecraft, bounds, mouseX, mouseY, partialTick, focused);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = editor.keyPressed(keyCode, scanCode, modifiers);
        synchronizeEditorAndController();
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = editor.keyReleased(keyCode, scanCode, modifiers);
        synchronizeEditorAndController();
        return handled;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        boolean handled = editor.charTyped(character, modifiers);
        synchronizeEditorAndController();
        return handled;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = editor.mouseClicked(mouseX, mouseY, button);
        synchronizeEditorAndController();
        return handled;
    }
    @Override public void mouseMoved(double mouseX, double mouseY) { editor.mouseMoved(mouseX, mouseY); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = editor.mouseReleased(mouseX, mouseY, button);
        synchronizeEditorAndController();
        return handled;
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean handled = editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        synchronizeEditorAndController();
        return handled;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return editor.mouseScrolled(mouseX, mouseY, delta);
    }

{% if features.document_history %}
    @Override
    public SFMHistoryGraphRuntime.OperationResult undoDocumentHistory() {
        SFMHistoryGraphRuntime.OperationResult result = controller.undo("text-editor-v3", "ctrl-z");
        lastOperation = result.message();
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED) {
            checkoutControllerRevision();
            publish();
        }
        return result;
    }
{% endif %}

    private void synchronizeEditorAndController() {
        if (registration == null) return;
        if (controller.revision() != observedControllerRevision) {
            checkoutControllerRevision();
            return;
        }
        String authoritative = controller.currentText();
        String edited = restoreAuthoritativeTrailingLineEnding(authoritative, editor.currentText());
        if (edited.equals(authoritative)) return;
        SFMHistoryGraphRuntime.OperationResult result = controller.acceptEditorText(edited);
        lastOperation = result.message();
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED) {
            observedControllerRevision = controller.revision();
            publish();
        } else {
            checkoutControllerRevision();
        }
    }

    private void checkoutControllerRevision() {
        SFMChamberDocumentState state = controller.currentState();
        editor.checkoutDocument(state.text(), selectionRanges(state));
        observedControllerRevision = controller.revision();
    }

    private void publish() {
        if (registration != null) SFMHistoryGraphRuntime.get().publish(controller.machineId());
    }

    static List<SFMTextDocumentRange> selectionRanges(SFMChamberDocumentState state) {
        Objects.requireNonNull(state, "state");
        ArrayList<SFMTextDocumentRange> answer = new ArrayList<>();
        for (SFMChamberDocumentState.SourceRegion region : state.selection()
                .map(SFMChamberDocumentState.SelectionWitness::regions)
                .orElse(List.of())) {
            int startUtf16 = state.text().offsetByCodePoints(0, region.startCodePointOffset());
            int endUtf16 = state.text().offsetByCodePoints(0, region.endCodePointOffset());
            SFMTextDocumentPosition start = SFMContextTextCoordinates.atUtf16Offset(state.text(), startUtf16);
            SFMTextDocumentPosition end = SFMContextTextCoordinates.atUtf16Offset(state.text(), endUtf16);
            answer.add(new SFMTextDocumentRange(start, end));
        }
        return List.copyOf(answer);
    }

    /**
     * Text Editor V3 stores visible glyph positions, so a terminal line ending
     * has no glyph and is absent from its projection. Preserve that exact
     * authoritative suffix while accepting a natural edit elsewhere.
     */
    static String restoreAuthoritativeTrailingLineEnding(String authoritative, String projected) {
        Objects.requireNonNull(authoritative, "authoritative");
        Objects.requireNonNull(projected, "projected");
        String ending = authoritative.endsWith("\r\n")
                ? "\r\n"
                : authoritative.endsWith("\n") || authoritative.endsWith("\r")
                        ? authoritative.substring(authoritative.length() - 1)
                        : "";
        if (ending.isEmpty() || projected.endsWith(ending)) return projected;
        return projected + ending;
    }
}
