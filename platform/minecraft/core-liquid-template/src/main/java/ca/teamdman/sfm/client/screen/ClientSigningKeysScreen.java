package ca.teamdman.sfm.client.screen;

{% if features.single_line_input %}
import ca.teamdman.sfm.client.input.SFMSingleLineEditBox;
{% else %}
import net.minecraft.client.gui.components.EditBox;
{% endif %}
import ca.teamdman.sfm.client.program.signing.ClientSigningKeyStore;
import ca.teamdman.sfm.client.program.signing.ClientSigningUiWorker;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMSigningPassphraseWidget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Explicit, off-by-default protected-key management. No private values enter actions or editor history. */
public final class ClientSigningKeysScreen extends Screen {
    public record Selection(Path file, ClientSigningKeyStore.LockedIdentity identity) {}
    public record ControlBounds(int x, int y, int width, int height) {}
    private enum Operation { CREATE, ROTATE, BACKUP, RESTORE }
    private record Result(List<Selection> keys, String message, Path selected) {}
    @FunctionalInterface private interface Job { Result run(char[] secret) throws Exception; }
    private final Path directory;
    private final Consumer<Selection> chosen;
    private final Map<String, ControlBounds> controls = new LinkedHashMap<>();
    private List<Selection> keys = List.of();
    private int selected;
    private Operation operation = Operation.CREATE;
{% if features.single_line_input %}
    private SFMSingleLineEditBox name;
    private SFMSingleLineEditBox external;
{% else %}
    private EditBox name;
    private EditBox external;
{% endif %}
    private SFMSigningPassphraseWidget passphrase;
    private SFMSigningPassphraseWidget confirmation;
    private Button apply, use, cancel, operationButton;
    private String message = "Signing is off until you explicitly create or restore a key.";
    private boolean busy;
    private boolean started;
    private volatile boolean closed;
    private int delay = 40;

    public ClientSigningKeysScreen(Path directory, Consumer<Selection> chosen) {
        super(Component.literal("Protected signing keys"));
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.chosen = Objects.requireNonNull(chosen);
    }

    @Override protected void init() {
        String oldName = name == null ? "default" : name.getValue();
        String oldExternal = external == null ? "" : external.getValue();
        clearSecrets();
        controls.clear();
        int left = left(), top = top(), span = span(), gap = 4;
        int cell = (span - gap * 3) / 4;
        button("previous", "Previous", left, top + 72, cell, b -> select(-1));
        button("next", "Next", left + cell + gap, top + 72, cell, b -> select(1));
        use = button("use", "Use this key", left + (cell + gap) * 2, top + 72, cell, b -> {
            Selection value = selected();
            if (busy || value == null) return;
            chosen.accept(value);
            onClose();
        });
        button("refresh", "Refresh", left + (cell + gap) * 3, top + 72, cell, b -> refresh());
        operationButton = button("operation", operation.name(), left, top + 96, cell, b -> {
            if (busy) return;
            operation = Operation.values()[(operation.ordinal() + 1) % Operation.values().length];
            delay = 40;
            clearSecrets();
            operationButton.setMessage(Component.literal(operation.name()));
        });
{% if features.single_line_input %}
        name = addRenderableWidget(new SFMSingleLineEditBox(font, left + cell + gap, top + 96,
                span - cell - gap, 20, Component.literal("New key file name"), false));
{% else %}
        name = addRenderableWidget(new EditBox(font, left + cell + gap, top + 96,
                span - cell - gap, 20, Component.literal("New key file name")));
{% endif %}
        name.setMaxLength(64);
        name.setValue(oldName);
        controls.put("name", new ControlBounds(left + cell + gap, top + 96, span - cell - gap, 20));
{% if features.single_line_input %}
        external = addRenderableWidget(new SFMSingleLineEditBox(font, left, top + 120, span, 20,
                Component.literal("Absolute encrypted backup or restore path"), false));
{% else %}
        external = addRenderableWidget(new EditBox(font, left, top + 120, span, 20,
                Component.literal("Absolute encrypted backup or restore path")));
{% endif %}
        external.setMaxLength(1024);
        external.setValue(oldExternal);
        controls.put("external_path", new ControlBounds(left, top + 120, span, 20));
        int half = (span - gap) / 2;
        passphrase = addRenderableWidget(new SFMSigningPassphraseWidget(font, left, top + 144, half,
                Component.literal("Passphrase (typing only)")));
        confirmation = addRenderableWidget(new SFMSigningPassphraseWidget(font, left + half + gap, top + 144,
                half, Component.literal("Confirm new passphrase")));
        controls.put("passphrase", new ControlBounds(left, top + 144, half, 20));
        controls.put("confirmation", new ControlBounds(left + half + gap, top + 144, half, 20));
        apply = button("apply", "Apply explicit operation", left, top + 200, span - 104, b -> apply());
        cancel = button("cancel", "Cancel", left + span - 100, top + 200, 100, b -> onClose());
        setInitialFocus(cancel);
        setFocused(cancel);
        if (!started) { started = true; refresh(); }
        updateControls();
    }

    private void select(int direction) {
        if (busy || keys.isEmpty()) return;
        selected = Math.floorMod(selected + direction, keys.size());
        clearSecrets();
        delay = 40;
    }
    private Selection selected() { return keys.isEmpty() ? null : keys.get(Math.min(selected, keys.size() - 1)); }
    private void clearSecrets() {
        if (passphrase != null) passphrase.close();
        if (confirmation != null) confirmation.close();
    }
    private void refresh() {
        if (!busy) runJob(new char[0], secret -> new Result(list(), "Locked fingerprints are unverified until unlock.", null));
    }
    private List<Selection> list() throws Exception {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Invalid key directory");
        List<Path> files;
        try (var children = Files.list(directory)) {
            files = children.filter(file -> file.getFileName().toString().endsWith(".sfmkey"))
                    .sorted().limit(65).toList();
        }
        if (files.size() > 64) throw new IllegalArgumentException("Key directory exceeds its budget");
        List<Selection> result = new ArrayList<>();
        for (Path file : files) {
            // Malformed files remain on disk and can never be overwritten by these controls.
            try { new ClientSigningKeyStore(file).inspect().ifPresent(identity -> result.add(new Selection(file, identity))); }
            catch (Exception invalid) { /* Public inspection failure does not expose private paths in diagnostics. */ }
        }
        return List.copyOf(result);
    }
    private Path namedFile() {
        String value = name.getValue();
        if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}") || value.contains("..")) {
            throw new IllegalArgumentException("Invalid key name");
        }
        if (!value.endsWith(".sfmkey")) value += ".sfmkey";
        Path resolved = directory.resolve(value).normalize();
        if (!directory.equals(resolved.getParent())) throw new IllegalArgumentException("Invalid key name");
        return resolved;
    }
    private Path externalFile() {
        Path value = Path.of(external.getValue()).normalize();
        if (!value.isAbsolute() || value.getFileName() == null) throw new IllegalArgumentException("Absolute file required");
        return value;
    }
    private void apply() {
        updateControls();
        if (!apply.active) return;
        final Operation requested = operation;
        final Selection existing = selected();
        final List<Selection> previousKeys = keys;
        final Path named, outside;
        try {
            named = requested == Operation.BACKUP ? null : namedFile();
            outside = requested == Operation.BACKUP || requested == Operation.RESTORE ? externalFile() : null;
        } catch (RuntimeException invalid) {
            message = "Use a simple new key name and an absolute backup/restore path.";
            clearSecrets();
            return;
        }
        char[] captured = passphrase.consume();
        confirmation.close();
        runJob(captured, secret -> {
            Path select = named;
            switch (requested) {
                case CREATE, ROTATE -> new ClientSigningKeyStore(named).create(secret);
                case BACKUP -> {
                    new ClientSigningKeyStore(Objects.requireNonNull(existing).file()).backupTo(outside, secret);
                    select = existing.file();
                }
                case RESTORE -> new ClientSigningKeyStore(named).restoreFrom(outside, secret);
            }
            List<Selection> refreshed;
            try { refreshed = list(); }
            catch (Exception unavailable) {
                return new Result(previousKeys, "Encrypted file operation completed; directory refresh failed. Check the destination.", select);
            }
            return new Result(refreshed, requested == Operation.ROTATE
                    ? "New identity created. Old key and existing signatures are unchanged."
                    : requested == Operation.BACKUP ? "Authenticated encrypted backup written; existing files unchanged."
                    : "Protected key written. Select it explicitly with Use this key.", select);
        });
    }
    private void runJob(char[] captured, Job job) {
        if (busy || closed) { Arrays.fill(captured, '\0'); return; }
        busy = true;
        message = "Working locally; private key operations stay off the render thread.";
        try {
            ClientSigningUiWorker.executor().execute(() -> {
                Result result = null;
                String failure = null;
                try { result = job.run(captured); }
                catch (ClientSigningKeyStore.PublicationException partial) {
                    failure = partial.published() ? "Encrypted file exists, but staging cleanup failed. Refresh; do not overwrite it."
                            : "Encrypted staging cleanup failed. Existing keys were not replaced.";
                } catch (Exception invalid) {
                    failure = "Not completed: check passphrase, key format, an absent destination and file permissions.";
                } finally { Arrays.fill(captured, '\0'); }
                Result completed = result;
                String diagnostic = failure;
                Minecraft.getInstance().execute(() -> {
                    if (closed) return;
                    busy = false;
                    delay = 40;
                    if (completed != null) {
                        keys = completed.keys();
                        selected = 0;
                        if (completed.selected() != null) {
                            for (int i = 0; i < keys.size(); i++) if (keys.get(i).file().equals(completed.selected())) selected = i;
                        }
                        message = completed.message();
                    } else message = diagnostic;
                    updateControls();
                });
            });
        } catch (RuntimeException rejected) {
            Arrays.fill(captured, '\0');
            busy = false;
            message = "The bounded key worker is busy. Try again after the current operation finishes.";
        }
    }
    @Override public void tick() {
        if (delay > 0) delay--;
        if (name != null) name.tick();
        if (external != null) external.tick();
        updateControls();
    }
    private void updateControls() {
        if (apply == null) return;
        boolean creating = operation == Operation.CREATE || operation == Operation.ROTATE;
        boolean needsSelection = operation == Operation.ROTATE || operation == Operation.BACKUP;
        apply.active = !busy && delay == 0 && passphrase.usable() && (!creating || passphrase.matches(confirmation))
                       && (!needsSelection || selected() != null);
        use.active = !busy && selected() != null;
        operationButton.active = !busy;
        name.active = !busy && operation != Operation.BACKUP;
        external.active = !busy && (operation == Operation.BACKUP || operation == Operation.RESTORE);
        passphrase.active = !busy;
        confirmation.active = !busy && creating;
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        fill(pose, 0, 0, width, height, 0xFF101218);
        int left = left(), top = top();
        SFMFontUtils.draw(pose, font, title, left, top, 0xFFFFFFFF, false);
        String file = selected() == null ? "No usable key selected." : selected().file().getFileName().toString();
        SFMFontUtils.draw(pose, font, font.plainSubstrByWidth(file, span()), left, top + 14, 0xFFB8D7FF, false);
        String fingerprint = selected() == null ? "Create or restore is explicit; no automatic key generation."
                : selected().identity().fingerprint();
        int y = top + 27;
        for (var line : font.split(Component.literal(fingerprint), span())) {
{% if features.font_formatted_text %}
            SFMFontUtils.draw(pose, font, line, left, y, 0xFFCCCCCC, false);
{% else %}
            font.draw(pose, line, left, y, 0xFFCCCCCC);
{% endif %}
            y += 10;
        }
        String help = delay > 0 ? "Review the operation; available in " + delay + " ticks."
                : "12+ characters. Typing only; Delete clears. New keys require confirmation.";
        SFMFontUtils.draw(pose, font, font.plainSubstrByWidth(help, span()), left, top + 168, 0xFFAAAAAA, false);
        int messageY = top + 178;
        for (var line : font.split(Component.literal(message), span()).stream().limit(2).toList()) {
{% if features.font_formatted_text %}
            SFMFontUtils.draw(pose, font, line, left, messageY, 0xFFFFFF99, false);
{% else %}
            font.draw(pose, line, left, messageY, 0xFFFFFF99);
{% endif %}
            messageY += 10;
        }
        SFMFontUtils.draw(pose, font, "Cancel clears input; an accepted file operation may still finish.", left, top + 224, 0xFFAAAAAA, false);
        super.render(pose, mouseX, mouseY, partialTick);
        if (external.getValue().isEmpty()) SFMFontUtils.draw(pose, font,
                font.plainSubstrByWidth("Absolute encrypted backup / restore path", span() - 12),
                left + 6, top + 126, 0xFF888888, false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (closed) return;
        closed = true;
        clearSecrets();
        SFMScreenChangeHelpers.popScreen();
    }
    @Override public void removed() { clearSecrets(); }
    public Map<String, ControlBounds> automationControls() { return Map.copyOf(controls); }
    public Map<String, Object> automationState() {
        return Map.of("busy", busy, "operation", operation.name(), "apply_active", apply != null && apply.active,
                "cancel_focused", getFocused() == cancel, "key_selected", selected() != null,
                "fingerprint", selected() == null ? "" : selected().identity().fingerprint());
    }
    private Button button(String id, String label, int x, int y, int width, Consumer<Button> pressed) {
        controls.put(id, new ControlBounds(x, y, width, 20));
        return addRenderableWidget(new SFMButtonBuilder().setPosition(x, y).setSize(width, 20)
                .setText(Component.literal(label)).setOnPress(pressed::accept).build());
    }
    private int span() { return Math.max(180, Math.min(720, width - 24)); }
    private int left() { return (width - span()) / 2; }
    private int top() { return Math.max(4, (height - 236) / 2); }
}
