package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.program.signing.*;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMSigningPassphraseWidget;
{% if features.editor_overlay_push %}
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOverlayOpenContext;
{% endif %}
{% if features.editor_overlay_push %}
import ca.teamdman.sfm.common.label.LabelPositionHolder;
{% endif %}
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningAcknowledgement;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Explicit review and Sign UI. The pen is cosmetic, ephemeral and never part of cryptographic input. */
public final class ClientProgramSigningScreen extends Screen {
    public record ControlBounds(int x, int y, int width, int height) {}
    private record Point(int x, int y, boolean start) {}
    private final ClientProgramSigningController controller;
    private final Supplier<Optional<ClientProgramSigningController.LiveRevision>> live;
    private final String previousSource;
    private final Path keyDirectory;
    private final ClientProgramSigningCeremony ceremony = new ClientProgramSigningCeremony();
    private final Map<String, ControlBounds> controls = new LinkedHashMap<>();
    private final List<Point> strokes = new ArrayList<>();
    private ClientSigningKeysScreen.Selection key;
    private SFMSigningPassphraseWidget passphrase;
    private Button sign, cancel, alternative, refresh, edit, keysButton;
    private ClientProgramSigningReview.View mode = ClientProgramSigningReview.View.SCOPE;
    private ClientManagerSigningAcknowledgement textAcknowledgement;
    private ClientProgramSigningController.Target target;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private boolean started, closed, childOpen, dragging;
    private boolean textDirty = true;

    public ClientProgramSigningScreen(ClientProgramSigningController controller,
                                      Supplier<Optional<ClientProgramSigningController.LiveRevision>> live,
                                      String previousSource, Path keyDirectory) {
        super(Component.literal("Review and sign the stored program"));
        this.controller = Objects.requireNonNull(controller);
        this.live = Objects.requireNonNull(live);
        this.previousSource = Objects.requireNonNull(previousSource);
        this.keyDirectory = Objects.requireNonNull(keyDirectory);
    }

    @Override protected void init() {
        childOpen = false;
        clearSensitiveInput();
        controls.clear();
        textAcknowledgement = null;
        textDirty = true;
        int left = left(), span = span(), gap = 4, cell = (span - gap * 3) / 4;
        keysButton = button("keys", "Keys", left, 36, cell, b -> openKeys());
        refresh = button("review", "Refresh review", left + cell + gap, 36, cell, b -> requestReview());
{% if features.editor_overlay_push %}
        edit = button("edit", "Edit / save", left + (cell + gap) * 2, 36, cell, b -> openEditor());
{% endif %}
        button("view", "View: " + mode.name(), left + (cell + gap) * 3, 36, cell, b -> {
            mode = ClientProgramSigningReview.View.values()[(mode.ordinal() + 1) % ClientProgramSigningReview.View.values().length];
            b.setMessage(Component.literal("View: " + mode.name()));
            textAcknowledgement = null;
            textDirty = true;
            scroll = 0;
        });
        passphrase = addRenderableWidget(new SFMSigningPassphraseWidget(font, left, height - 56, span,
                Component.literal("Key passphrase (typing only; 12+ characters)")));
        controls.put("passphrase", new ControlBounds(left, height - 56, span, 20));
        sign = button("sign", "Sign", left, height - 28, 76, b -> sign());
        alternative = button("alternative", "Use buttons instead", left + 80, height - 28, span - 164, b -> {
            if (controller.view().state() == ClientProgramSigningController.State.READY && key != null) ceremony.acknowledge();
        });
        cancel = button("cancel", "Cancel", left + span - 80, height - 28, 80, b -> onClose());
        controls.put("pad", new ControlBounds(left, height - 102, span, 40));
        setInitialFocus(cancel);
        setFocused(cancel);
        if (!started) {
            started = true;
            if (controller.view().state() == ClientProgramSigningController.State.IDLE) requestReview();
        }
        updateView();
    }

    private void requestReview() {
        clearSensitiveInput();
        live.get().ifPresent(current -> {
            target = current.target();
            controller.review(current);
        });
        textAcknowledgement = null;
        textDirty = true;
    }
    private void openKeys() {
        if (controller.view().workerInFlight() || controller.view().state() == ClientProgramSigningController.State.WAITING_RESULT) return;
        clearSensitiveInput();
        childOpen = true;
        SFMScreenChangeHelpers.setOrPushScreen(new ClientSigningKeysScreen(keyDirectory, selection -> {
            key = selection;
            textAcknowledgement = null;
            textDirty = true;
            clearSensitiveInput();
        }));
    }
{% if features.editor_overlay_push %}
    private void openEditor() {
        var current = live.get();
        if (current.isEmpty() || controller.view().workerInFlight()) return;
        var base = current.orElseThrow();
        controller.cancel();
        clearSensitiveInput();
        childOpen = true;
        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenOverlayOpenContext(base.body().source(),
                LabelPositionHolder.deserialize(base.body().toDiskProjection().getCompound("sfm:labels")),
                source -> {
                    // Never switch this compare-and-swap base to a later live revision while the editor is open.
                    if (!controller.save(base, source)) {
                        throw new IllegalStateException("The stored revision changed or the signing review is busy. Reopen this manager before saving.");
                    }
                    target = base.target();
                    textAcknowledgement = null;
                    textDirty = true;
                }));
    }
{% endif %}
    private void sign() {
        updateView();
        if (!sign.active || key == null) return;
        char[] captured = passphrase.consume();
        ceremony.clear();
        strokes.clear();
        dragging = false;
        controller.sign(key.identity().fingerprint(), new ClientSigningUnlockOperation(key.file(), captured));
        updateView();
    }
    private void clearSensitiveInput() {
        if (passphrase != null) passphrase.close();
        ceremony.clear();
        strokes.clear();
        dragging = false;
    }
    @Override public void tick() {
        controller.tick();
        updateView();
        ceremony.tick();
        updateControls();
    }
    private void updateView() {
        controller.update();
        var view = controller.view();
        var acknowledgement = view.review().orElse(null);
        if (target == null) live.get().ifPresent(current -> target = current.target());
        UUID challenge = view.state() == ClientProgramSigningController.State.READY && acknowledgement != null
                ? acknowledgement.challenge() : null;
        if (!ceremony.bind(challenge, key == null ? null : key.identity().fingerprint())) {
            if (passphrase != null) passphrase.close();
            strokes.clear();
            dragging = false;
        }
        if (textDirty || acknowledgement != textAcknowledgement) {
            textAcknowledgement = acknowledgement;
            textDirty = false;
            scroll = 0;
            List<String> publicLines = acknowledgement == null || target == null
                    ? List.of("Wait for the exact stored revision acknowledgement.", "No save or signature is automatic.")
                    : ClientProgramSigningReview.lines(acknowledgement, target, previousSource,
                            key == null ? null : key.identity().fingerprint(), mode);
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            publicLines.forEach(line -> wrapped.addAll(font.split(Component.literal(line), span() - 12)));
            lines = List.copyOf(wrapped);
        }
        updateControls();
    }
    private void updateControls() {
        if (sign == null) return;
        var view = controller.view();
        boolean ready = view.state() == ClientProgramSigningController.State.READY && !view.workerInFlight();
        sign.active = ready && key != null && ceremony.ready() && passphrase.usable();
        alternative.active = ready && key != null;
        passphrase.active = ready && key != null;
        boolean waiting = view.workerInFlight() || view.state() == ClientProgramSigningController.State.WAITING_ACK
                || view.state() == ClientProgramSigningController.State.WAITING_PROJECTION
                || view.state() == ClientProgramSigningController.State.WAITING_RESULT;
        keysButton.active = !view.workerInFlight() && view.state() != ClientProgramSigningController.State.WAITING_RESULT;
        refresh.active = !waiting;
{% if features.editor_overlay_push %}
        edit.active = !waiting && live.get().isPresent();
{% endif %}
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && insidePad(x, y) && controller.view().state() == ClientProgramSigningController.State.READY && key != null) {
            ceremony.acknowledge();
            dragging = true;
            if (strokes.size() < 2048) strokes.add(new Point((int) x, (int) y, true));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            if (insidePad(x, y) && strokes.size() < 2048) strokes.add(new Point((int) x, (int) y, false));
            else dragging = false;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (y >= 62 && y < height - 110) { scrollBy((int) -Math.signum(amount) * 3); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scrollBy(visibleLines()); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { scrollBy(-visibleLines()); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private void scrollBy(int amount) { scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines()), scroll + amount)); }
    private int visibleLines() { return Math.max(1, (height - 176) / 10); }
    private boolean insidePad(double x, double y) {
        return x >= left() && x < left() + span() && y >= height - 102 && y < height - 62;
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        updateView();
        // This review is stacked over the workspace; underlying text must not compete with signed content.
        fill(pose, 0, 0, width, height, 0xFF101218);
        SFMFontUtils.draw(pose, font, title, left(), 8, 0xFFFFFFFF, false);
        var view = controller.view();
        String status = view.state() + (view.problem() == ClientProgramSigningController.Problem.NONE ? "" : " / " + view.problem());
        SFMFontUtils.draw(pose, font, status, left(), 22, 0xFFFFFF99, false);
        fill(pose, left(), 62, left() + span(), height - 110, 0xC0101218);
        for (int index = 0; index < visibleLines() && scroll + index < lines.size(); index++) {
{% if features.font_formatted_text %}
            SFMFontUtils.draw(pose, font, lines.get(scroll + index), left() + 6, 65 + index * 10, 0xFFE0E0E0, false);
{% else %}
            font.draw(pose, lines.get(scroll + index), left() + 6, 65 + index * 10, 0xFFE0E0E0);
{% endif %}
        }
        fill(pose, left(), height - 102, left() + span(), height - 62, 0xFFE9E1CC);
        hLine(pose, left() + 8, left() + span() - 8, height - 70, 0xFF817763);
        if (strokes.isEmpty()) {
            String hint = key == null ? "Choose a key; signing remains off." : "Pen contact is cosmetic. Or use buttons below.";
            SFMFontUtils.draw(pose, font, font.plainSubstrByWidth(hint, span() - 12), left() + 6, height - 92, 0xFF554B3F, false);
        }
        drawStrokes(pose);
        if (ceremony.remainingTicks() > 0) SFMFontUtils.draw(pose, font, "Review delay: " + ceremony.remainingTicks(),
                left() + 6, height - 79, 0xFF554B3F, false);
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @MCVersionDependentBehaviour
    private void drawStrokes(PoseStack pose) {
        if (strokes.isEmpty()) return;
{% case minecraft_version %}
{% when "1.19.2" %}
        RenderSystem.disableTexture();
{% when "1.19.4" %}
        disableTexture();
{% endcase %}
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Point previous = null;
        for (Point point : strokes) {
            Point start = point.start() || previous == null ? new Point(point.x() + 1, point.y() + 1, true) : previous;
            buffer.vertex(pose.last().pose(), start.x(), start.y(), 0).color(40, 48, 62, 255).endVertex();
            buffer.vertex(pose.last().pose(), point.x(), point.y(), 0).color(40, 48, 62, 255).endVertex();
            previous = point;
        }
        tesselator.end();
{% case minecraft_version %}
{% when "1.19.2" %}
        RenderSystem.enableTexture();
{% when "1.19.4" %}
        enableTexture();
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2" %}
{% when "1.19.4" %}

    @MCVersionDependentBehaviour
    private static void disableTexture() {
        // RenderSystem.disableTexture(); // 1.19.2
    }

    @MCVersionDependentBehaviour
    private static void enableTexture() {
        // RenderSystem.enableTexture(); // 1.19.2
    }
{% endcase %}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (closed) return;
        closed = true;
        childOpen = false;
        clearSensitiveInput();
        controller.close();
        SFMScreenChangeHelpers.popScreen();
    }
    @Override public void removed() {
        clearSensitiveInput();
        if (!childOpen && !closed) { closed = true; controller.close(); }
    }
    public Map<String, ControlBounds> automationControls() { return Map.copyOf(controls); }
    /** Bounded public review text only; passphrase widgets and cosmetic strokes are excluded. */
    public List<String> automationReviewText() {
        updateView();
        return lines.stream().limit(128).map(line -> {
            StringBuilder text = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                if (text.length() >= 512) return false;
                text.appendCodePoint(codePoint);
                return true;
            });
            return text.toString();
        }).toList();
    }
    public Map<String, Object> automationState() {
        // Inspect the same live gate as a Sign click, never a stale last-render button state.
        updateView();
        var view = controller.view();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("state", view.state().name());
        state.put("problem", view.problem().name());
        state.put("sign_active", sign != null && sign.active);
        state.put("cancel_focused", getFocused() == cancel);
        state.put("cosmetic_acknowledged", ceremony.acknowledged());
        state.put("delay_ticks", ceremony.remainingTicks());
        state.put("fingerprint", key == null ? "" : key.identity().fingerprint());
        state.put("view", mode.name());
        state.put("server_status", view.serverStatus().map(Enum::name).orElse(""));
        state.put("ack_revision", view.review().map(ack -> ack.snapshot().revision()).orElse(-1L));
        state.put("ack_source_sha256", view.review().map(ack -> ack.snapshot().body().sourceSha256()).orElse(""));
        return Map.copyOf(state);
    }
    private Button button(String id, String label, int x, int y, int width, Consumer<Button> pressed) {
        controls.put(id, new ControlBounds(x, y, width, 20));
        return addRenderableWidget(new SFMButtonBuilder().setPosition(x, y).setSize(width, 20)
                .setText(Component.literal(label)).setOnPress(pressed::accept).build());
    }
    private int span() { return Math.max(240, Math.min(800, width - 24)); }
    private int left() { return (width - span()) / 2; }
}
