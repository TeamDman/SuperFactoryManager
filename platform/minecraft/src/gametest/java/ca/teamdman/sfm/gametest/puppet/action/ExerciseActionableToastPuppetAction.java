package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastLayout;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Live pointer/palette proof for the exact-instance actionable-toast contract. */
public final class ExerciseActionableToastPuppetAction implements SFMPuppetAction {
    private static final int TIMEOUT_TICKS = 20 * 20;
    private static final int PIN_WITNESS_TICKS = 8;
    private static final int RESUME_WITNESS_TICKS = 8;

    private final String artifactName;
    private Phase phase = Phase.WAIT_FOR_FAILURE;
    private int elapsedTicks;
    private int phaseTicks;
    private SFMWorkspaceToastQueue.ToastId firstId;
    private String firstText;
    private SFMWorkspaceToastQueue.ToastId copyConfirmationId;
    private String copyConfirmationText;
    private long pinnedRemainingNanos;

    public ExerciseActionableToastPuppetAction(String artifactName) {
        this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
    }

    @Override
    public String description() {
        return "exercise actionable workspace toast through pointer and constrained palette";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++elapsedTicks > TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out during actionable-toast phase " + phase);
        }
        return switch (phase) {
            case WAIT_FOR_FAILURE -> waitForFailure(runtime);
            case PIN_CHOICE -> choosePin(runtime);
            case PIN_WITNESS -> witnessPin(runtime);
            case RESUME_CHOICE -> chooseResume(runtime);
            case RESUME_WITNESS -> witnessResume(runtime);
            case DISMISS_CHOICE -> chooseDismiss(runtime);
            case DISMISS_WITNESS -> witnessDismiss(runtime);
            case WAIT_FOR_REPLACEMENT -> waitForReplacement(runtime);
        };
    }

    private boolean waitForFailure(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        Optional<SFMWorkspaceToastQueue.Snapshot> candidate = workspace.latestWorkspaceToast();
        if (candidate.isEmpty()) return false;
        SFMWorkspaceToastQueue.Snapshot snapshot = candidate.orElseThrow();
        if (snapshot.text().equals("Looking up definition...")) return false;
        if (!isDefinitionFailure(snapshot.text())) {
            throw new IllegalStateException("Expected failed definition lookup toast, found: " + snapshot.text());
        }
        Optional<SFMWorkspaceToastLayout.Bounds> bounds = workspace.workspaceToastBounds(snapshot.id());
        if (bounds.isEmpty()) return false;

        firstId = snapshot.id();
        firstText = snapshot.text();
        if (snapshot.remainingFraction() < 0.5D) {
            throw new IllegalStateException("Definition failure toast did not leave a readable lifetime");
        }

        SFMWorkspaceToastLayout.Bounds hit = bounds.orElseThrow();
        double x = hit.x() + hit.width() / 2.0D;
        double y = hit.y() + hit.height() / 2.0D;
        if (!workspace.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Left click did not claim the actionable toast");
        }
        if (!Minecraft.getInstance().keyboardHandler.getClipboard().equals(firstText)) {
            throw new IllegalStateException("Left click did not copy the exact toast text");
        }
        SFMWorkspaceToastQueue.Snapshot retained = requiredFirst(workspace);
        if (!retained.text().equals(firstText)) {
            throw new IllegalStateException("Copying mutated the source toast text");
        }
        SFMWorkspaceToastQueue.Snapshot confirmation = workspace.latestWorkspaceToast()
                .filter(candidateSnapshot -> !candidateSnapshot.id().equals(firstId))
                .orElseThrow(() -> new IllegalStateException(
                        "Left click did not publish a distinct clipboard confirmation toast"));
        copyConfirmationId = confirmation.id();
        copyConfirmationText = confirmation.text();
        String expectedConfirmation = "Copied notification " + firstId.value() + " to the clipboard";
        if (!copyConfirmationText.equals(expectedConfirmation)) {
            throw new IllegalStateException("Unexpected clipboard confirmation: " + copyConfirmationText);
        }
        openChoices(workspace, firstId);
        transition(Phase.PIN_CHOICE);
        return false;
    }

    private boolean choosePin(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)) return false;
        List<String> choices = choices("sfm:toast/timer/stop");
        runtime.assertActionChoice(choices);
        runtime.clickActionChoice(choices.get(1));
        transition(Phase.PIN_WITNESS);
        return false;
    }

    private boolean witnessPin(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        SFMWorkspaceToastQueue.Snapshot snapshot = requiredFirst(workspace);
        if (!snapshot.pinned()) throw new IllegalStateException("Stop Timer Forever did not pin the toast");
        if (phaseTicks++ == 0) pinnedRemainingNanos = snapshot.remainingNanos();
        if (phaseTicks <= PIN_WITNESS_TICKS) return false;
        snapshot = requiredFirst(workspace);
        if (snapshot.remainingNanos() != pinnedRemainingNanos) {
            throw new IllegalStateException("Pinned toast lifetime changed from "
                    + pinnedRemainingNanos + " to " + snapshot.remainingNanos());
        }
        openChoices(workspace, snapshot.id());
        transition(Phase.RESUME_CHOICE);
        return false;
    }

    private boolean chooseResume(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)) return false;
        List<String> choices = choices("sfm:toast/timer/resume");
        runtime.assertActionChoice(choices);
        runtime.clickActionChoice(choices.get(1));
        transition(Phase.RESUME_WITNESS);
        return false;
    }

    private boolean witnessResume(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        SFMWorkspaceToastQueue.Snapshot snapshot = requiredFirst(workspace);
        if (snapshot.pinned()) throw new IllegalStateException("Resume Timer left the toast pinned");
        if (phaseTicks++ <= RESUME_WITNESS_TICKS) return false;
        snapshot = requiredFirst(workspace);
        if (snapshot.remainingNanos() >= pinnedRemainingNanos) {
            throw new IllegalStateException("Resumed toast timer did not continue from its remaining lifetime");
        }
        openChoices(workspace, snapshot.id());
        transition(Phase.DISMISS_CHOICE);
        return false;
    }

    private boolean chooseDismiss(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)) return false;
        List<String> choices = choices("sfm:toast/timer/stop");
        runtime.assertActionChoice(choices);
        runtime.clickActionChoice(choices.get(2));
        transition(Phase.DISMISS_WITNESS);
        return false;
    }

    private boolean witnessDismiss(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        if (workspace.workspaceToastSnapshot(firstId).isPresent()) {
            throw new IllegalStateException("Dismiss left the exact toast in the workspace queue");
        }
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        transition(Phase.WAIT_FOR_REPLACEMENT);
        return false;
    }

    private boolean waitForReplacement(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspaceOrNull();
        if (workspace == null) return false;
        Optional<SFMWorkspaceToastQueue.Snapshot> candidate = workspace.latestWorkspaceToast();
        if (candidate.isEmpty() || candidate.orElseThrow().id().equals(firstId)) return false;
        SFMWorkspaceToastQueue.Snapshot replacement = candidate.orElseThrow();
        if (replacement.text().equals("Looking up definition...")) return false;
        if (!isDefinitionFailure(replacement.text())) {
            throw new IllegalStateException("Later definition failure had unexpected text: " + replacement.text());
        }
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.actionable-toast-puppet/1");
        evidence.addProperty("first_toast_id", firstId.value());
        evidence.addProperty("first_text", firstText);
        evidence.addProperty("copied_exact_text", true);
        evidence.addProperty("copy_confirmation_id", copyConfirmationId.value());
        evidence.addProperty("copy_confirmation_text", copyConfirmationText);
        evidence.addProperty("copy_confirmation_visible", true);
        evidence.addProperty("copy_preserved_source", true);
        evidence.addProperty("pin_preserved_remaining_nanos", pinnedRemainingNanos);
        evidence.addProperty("dismissed_exact_instance", true);
        evidence.addProperty("later_toast_id", replacement.id().value());
        evidence.addProperty("later_text", replacement.text());
        evidence.addProperty("later_message_visible", true);
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, evidence.toString());
        return true;
    }

    private void openChoices(
            SFMScreenMultiplexer workspace,
            SFMWorkspaceToastQueue.ToastId id
    ) {
        SFMWorkspaceToastLayout.Bounds hit = workspace.workspaceToastBounds(id)
                .orElseThrow(() -> new IllegalStateException("Live toast has no painted pointer bounds"));
        if (!workspace.mouseClicked(
                hit.x() + hit.width() / 2.0D,
                hit.y() + hit.height() / 2.0D,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT
        )) {
            throw new IllegalStateException("Right click did not reopen actionable-toast choices");
        }
    }

    private SFMWorkspaceToastQueue.Snapshot requiredFirst(SFMScreenMultiplexer workspace) {
        return workspace.workspaceToastSnapshot(firstId)
                .orElseThrow(() -> new IllegalStateException("The first toast expired during its leased interaction"));
    }

    private List<String> choices(String timerAction) {
        String id = firstId.commandArgument();
        return List.of(
                "sfm action invoke sfm:toast/copy " + id,
                "sfm action invoke " + timerAction + " " + id,
                "sfm action invoke sfm:toast/dismiss " + id
        );
    }

    private static SFMScreenMultiplexer workspaceOrNull() {
        return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace ? workspace : null;
    }

    private static boolean isDefinitionFailure(String text) {
        return text.contains("No symbol") || text.startsWith("Jump to definition unavailable:");
    }

    private void transition(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private enum Phase {
        WAIT_FOR_FAILURE,
        PIN_CHOICE,
        PIN_WITNESS,
        RESUME_CHOICE,
        RESUME_WITNESS,
        DISMISS_CHOICE,
        DISMISS_WITNESS,
        WAIT_FOR_REPLACEMENT
    }
}
