package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.gametest.puppet.action.*;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declarative action builder for one annotated game puppet definition.
 */
public final class SFMGamePuppetHelper {
    public static final int SCREEN_TIMEOUT_TICKS = 200;
    public static final int RENDER_SETTLE_TICKS = 6;
    private final List<SFMPuppetAction> actions = new ArrayList<>();
    private int currentAction = 0;

    public void createFreshFlatWorld() {
        add(new CreateFreshWorldPuppetAction());
    }

    public void runGameTest(String testName) {
        if (testName == null || testName.isBlank()) {
            throw new IllegalArgumentException("Game puppet GameTest name must not be blank");
        }
        add(new RunGameTestPuppetAction(testName));
    }

    public void captureOrbit(
            String capturePrefix,
            BlockPos localTarget,
            int count,
            double radius,
            double height,
            Component caption
    ) {
        if (count < 1) {
            throw new IllegalArgumentException("Orbit capture count must be at least one");
        }
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2D * index / count;
            add(new PositionOrbitCameraPuppetAction(localTarget, radius, height, angle));
            capture(String.format("%s-%02d", capturePrefix, index), caption);
        }
    }

    /**
     * Captures the completed GameTest around the center of its actual structure bounds.
     */
    public void captureGameTestOrbit(
            String capturePrefix,
            int count,
            Component caption
    ) {
        if (count < 1) {
            throw new IllegalArgumentException("Orbit capture count must be at least one");
        }
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2D * index / count;
            add(new PositionGameTestOrbitCameraPuppetAction(angle));
            capture(String.format("%s-%02d", capturePrefix, index), caption);
        }
    }

    public void captureContainerAt(String captureName, BlockPos localTarget, Component caption) {
        captureBlockScreen(captureName, localTarget, ContainerScreen.class, true, caption);
    }

    public void captureManagerAt(String captureName, BlockPos localTarget, Component caption) {
        captureBlockScreen(captureName, localTarget, ManagerScreen.class, false, caption);
    }

    public void captureManagerProgramEditor(String captureName, Component caption) {
        add(new OpenManagerProgramEditorPuppetAction());
        add(new WaitForScreenPuppetAction(ISFMTextEditScreen.class));
        capture(captureName, caption);
        add(new CloseScreenPuppetAction());
    }

    /**
     * Waits until a specific overlay type is absent without suppressing unrelated overlays.
     */
    public void waitForOverlayToNotBePresent(Class<? extends Overlay> overlayType) {
        add(new WaitForOverlayToNotBePresentPuppetAction(overlayType));
    }

    /**
     * Waits until a specific overlay type is present without constraining unrelated overlays.
     */
    public void waitForOverlayToBePresent(Class<? extends Overlay> overlayType) {
        add(new WaitForOverlayToBePresentPuppetAction(overlayType));
    }

    /**
     * Waits for a fixed number of client ticks before continuing the puppet.
     */
    public void waitTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("Wait ticks must not be negative");
        }
        if (ticks > 0) {
            add(new WaitTicksPuppetAction(ticks));
        }
    }

    /**
     * Captures the currently rendered client frame with a numbered, styled caption.
     */
    public void capture(String captureName, Component caption) {
        add(new CapturePuppetAction(captureName, Objects.requireNonNull(caption, "caption").copy()));
    }

    public boolean isComplete() {
        return currentAction >= actions.size();
    }

    public String currentActionDescription() {
        return isComplete() ? "complete" : actions.get(currentAction).description();
    }

    public void validate() {
        if (actions.isEmpty()) {
            throw new IllegalStateException("SFM game puppet declared no actions");
        }
    }

    boolean tick(ISFMGamePuppetRuntime runtime) {
        if (isComplete()) {
            return true;
        }
        if (actions.get(currentAction).tick(runtime)) {
            currentAction++;
        }
        return isComplete();
    }

    private void captureBlockScreen(
            String captureName,
            BlockPos localTarget,
            Class<?> expectedScreen,
            boolean closeAfterCapture,
            Component caption
    ) {
        add(new UseBlockPuppetAction(localTarget));
        add(new WaitForScreenPuppetAction(expectedScreen));
        capture(captureName, caption);
        if (closeAfterCapture) {
            add(new CloseScreenPuppetAction());
        }
    }

    private void add(SFMPuppetAction action) {
        if (currentAction != 0) {
            throw new IllegalStateException("Cannot add game puppet actions after execution has begun");
        }
        actions.add(action);
    }

}
