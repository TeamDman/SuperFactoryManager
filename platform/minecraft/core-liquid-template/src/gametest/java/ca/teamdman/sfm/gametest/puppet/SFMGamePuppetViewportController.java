package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

final class SFMGamePuppetViewportController {
    private static final int REQUIRED_STABLE_TICKS = 3;
    private static final int TIMEOUT_TICKS = 200;
    private static SFMGamePuppetViewportVariant original;
    private static SFMGamePuppetViewportVariant restorationTarget;
    private static SFMGamePuppetViewportObservation restorationPrevious;
    private static int restorationStableTicks;
    private static int restorationTicks;
    private final SFMGamePuppetViewportVariant requested;
    private SFMGamePuppetViewportObservation previous;
    private int stableTicks;
    private int ticks;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private boolean requestedSize;
    private boolean requestedScale;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    private boolean requestedChange;
{% endcase %}

    SFMGamePuppetViewportController(SFMGamePuppetViewportVariant requested) {
        this.requested = requested;
    }

    boolean tick(Minecraft minecraft, ActivePuppet active) {
        if (original == null) {
            SFMGamePuppetViewportObservation observation = observe(minecraft);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            // A minimized, locked, or not-yet-presented GLFW window may briefly report 0x0.
            // That is not a restorable viewport, but it must not prevent the puppet from
            // requesting its deterministic size and continuing without touching the OS cursor.
            if (observation.windowWidth() >= 320 && observation.windowHeight() >= 320) {
                original = new SFMGamePuppetViewportVariant(
                        observation.windowWidth(), observation.windowHeight(), minecraft.options.guiScale().get());
            }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            original = new SFMGamePuppetViewportVariant(observation.windowWidth(), observation.windowHeight(), minecraft.options.guiScale().get());
{% endcase %}
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (!requestedSize) {
            requestedSize = true;
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), requested.width(), requested.height());
            minecraft.resizeDisplay();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (!requestedChange) {
            requestedChange = true;
            minecraft.options.guiScale().set(requested.guiScale());
            GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), requested.width(), requested.height());
            minecraft.resizeDisplay();
{% when "26.1.2" %}
        if (!requestedChange) {
            requestedChange = true;
            minecraft.options.guiScale().set(requested.guiScale());
            GLFW.glfwSetWindowSize(minecraft.getWindow().handle(), requested.width(), requested.height());
            minecraft.resizeGui();
{% endcase %}
            SFM.LOGGER.info("SFM_GAME_PUPPET_VIEWPORT_REQUESTED puppet={} variant={} requested_width={} requested_height={} requested_gui_scale={}",
                    active.definition.puppetName(), requested.id(), requested.width(), requested.height(), requested.requestedScaleName());
            return false;
        }
        SFMGamePuppetViewportObservation observation = observe(minecraft);
        active.viewportObservation = observation;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (!requestedScale) {
            if (observation.windowWidth() != requested.width() || observation.windowHeight() != requested.height()) {
                failIfTimedOut(observation);
                return false;
            }
            requestedScale = true;
            minecraft.options.guiScale().set(requested.guiScale());
            minecraft.resizeDisplay();
            previous = null;
            stableTicks = 0;
            return false;
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        if (requested.guiScale() > 0 && observation.effectiveGuiScale() != requested.guiScale()) {
            throw new IllegalStateException("Explicit GUI scale " + requested.guiScale() + " was clamped to " + observation.effectiveGuiScale());
        }
        if (observation.equals(previous) && observation.windowWidth() == requested.width() && observation.windowHeight() == requested.height()) stableTicks++;
        else stableTicks = 0;
        previous = observation;
        if (stableTicks >= REQUIRED_STABLE_TICKS) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_VIEWPORT_READY puppet={} variant={} actual_width={} actual_height={} framebuffer_width={} framebuffer_height={} requested_gui_scale={} effective_gui_scale={} logical_width={} logical_height={}",
                    active.definition.puppetName(), requested.id(), observation.windowWidth(), observation.windowHeight(), observation.framebufferWidth(), observation.framebufferHeight(), requested.requestedScaleName(), observation.effectiveGuiScale(), observation.logicalWidth(), observation.logicalHeight());
            return true;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        failIfTimedOut(observation);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (++ticks > TIMEOUT_TICKS) throw new IllegalStateException("Viewport " + requested.id() + " did not stabilize; last observation=" + observation);
{% endcase %}
        return false;
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void failIfTimedOut(SFMGamePuppetViewportObservation observation) {
        if (++ticks > TIMEOUT_TICKS) {
            throw new IllegalStateException("Viewport " + requested.id() + " did not stabilize; last observation=" + observation);
        }
    }

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    static void requestRestore(Minecraft minecraft) {
        if (original == null) return;
        restorationTarget = original;
        minecraft.options.guiScale().set(restorationTarget.guiScale());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), restorationTarget.width(), restorationTarget.height());
        minecraft.resizeDisplay();
{% when "26.1.2" %}
        GLFW.glfwSetWindowSize(minecraft.getWindow().handle(), restorationTarget.width(), restorationTarget.height());
        minecraft.resizeGui();
{% endcase %}
        SFM.LOGGER.info("SFM_GAME_PUPPET_VIEWPORT_RESTORE_REQUESTED requested_width={} requested_height={} requested_gui_scale={}", restorationTarget.width(), restorationTarget.height(), restorationTarget.requestedScaleName());
    }

    static boolean tickRestore(Minecraft minecraft) {
        if (restorationTarget == null) return true;
        SFMGamePuppetViewportObservation observation = observe(minecraft);
        if (observation.equals(restorationPrevious)
            && observation.windowWidth() == restorationTarget.width()
            && observation.windowHeight() == restorationTarget.height()) restorationStableTicks++;
        else restorationStableTicks = 0;
        restorationPrevious = observation;
        if (restorationStableTicks >= REQUIRED_STABLE_TICKS) {
            SFM.LOGGER.info("SFM_GAME_PUPPET_VIEWPORT_RESTORED actual_width={} actual_height={} framebuffer_width={} framebuffer_height={} effective_gui_scale={} logical_width={} logical_height={}", observation.windowWidth(), observation.windowHeight(), observation.framebufferWidth(), observation.framebufferHeight(), observation.effectiveGuiScale(), observation.logicalWidth(), observation.logicalHeight());
            restorationTarget = null;
            original = null;
            restorationPrevious = null;
            restorationStableTicks = 0;
            restorationTicks = 0;
            return true;
        }
        if (++restorationTicks > TIMEOUT_TICKS) throw new IllegalStateException("Original viewport did not stabilize during restoration; last observation=" + observation);
        return false;
    }

    static int maximumScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
    }

    private static SFMGamePuppetViewportObservation observe(Minecraft minecraft) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        long handle = minecraft.getWindow().getWindow();
{% when "26.1.2" %}
        long handle = minecraft.getWindow().handle();
{% endcase %}
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer windowWidth = stack.mallocInt(1);
            IntBuffer windowHeight = stack.mallocInt(1);
            IntBuffer framebufferWidth = stack.mallocInt(1);
            IntBuffer framebufferHeight = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(handle, windowWidth, windowHeight);
            GLFW.glfwGetFramebufferSize(handle, framebufferWidth, framebufferHeight);
            return new SFMGamePuppetViewportObservation(
                    windowWidth.get(0), windowHeight.get(0), framebufferWidth.get(0), framebufferHeight.get(0),
                    (int) Math.round(minecraft.getWindow().getGuiScale()), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight()
            );
        }
    }
}
