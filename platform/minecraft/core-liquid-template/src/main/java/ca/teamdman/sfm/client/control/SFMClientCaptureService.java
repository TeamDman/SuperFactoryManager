package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
{% if targets.mc_26_1_2 %}
import net.minecraft.util.Util;
{% else %}
import net.minecraft.Util;
{% endif %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
{% else %}
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
{% endcase %}

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fresh framebuffer readback, using the same Minecraft screenshot path as puppet capture. */
public final class SFMClientCaptureService {
    private static final Map<String, Capture> CAPTURES = new LinkedHashMap<>();
    private static long completedFrames;
    private static long contentRevision;
    private static Object renderedScreen;
    private static boolean renderedWorld;

    private SFMClientCaptureService() { }

    public static synchronized JsonObject request(String owner) {
        if (CAPTURES.values().stream().filter(value -> !value.done()).count() >= 8) {
            throw new IllegalStateException("capacity_exceeded: eight captures are already pending");
        }
        while (CAPTURES.size() >= 32) {
            String oldest = CAPTURES.entrySet().stream().filter(entry -> entry.getValue().done())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            CAPTURES.remove(oldest);
        }
        Minecraft minecraft = Minecraft.getInstance();
        String id = UUID.randomUUID().toString();
        Path output = minecraft.gameDirectory.toPath().resolve("screenshots/sfm-control")
                .resolve(SFMClientControlLifecycle.runtime().instanceId()).resolve(id + ".png");
        Capture capture = new Capture(id, owner, minecraft.screen, minecraft.getMainRenderTarget().width,
                minecraft.getMainRenderTarget().height, completedFrames, System.nanoTime() + 10_000_000_000L, output);
        CAPTURES.put(id, capture);
        return describe(capture);
    }

    public static synchronized JsonObject status(String id) {
        Capture capture = CAPTURES.get(id);
        if (capture == null) throw new IllegalArgumentException("Unknown or expired capture id");
        return describe(capture);
    }

    static synchronized void tick() {
        long now = System.nanoTime();
        for (Capture capture : CAPTURES.values()) {
            if (!capture.done() && now - capture.deadline >= 0) {
                capture.failure = capture.requested ? "capture_write_timeout" : "render_unavailable: no fresh frame before deadline";
            }
        }
    }

    static synchronized void cancelOwner(String owner) {
        for (Capture capture : CAPTURES.values()) {
            if (capture.owner.equals(owner) && !capture.done()) capture.failure = "cancelled: controller disabled";
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static synchronized void screenRendered(ScreenEvent.Render.Post event) {
        renderedScreen = event.getScreen();
        contentRevision++;
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    @MCVersionDependentBehaviour
{% if targets.mc_26_1_2 %}
    public static synchronized void worldRendered(RenderLevelStageEvent.AfterTranslucentParticles event) {
{% else %}
    public static synchronized void worldRendered(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
{% endif %}
        renderedWorld = true;
        contentRevision++;
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void afterRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
{% else %}
    public static void afterRender(RenderFrameEvent.Post event) {
{% endcase %}
        captureCompletedFrame();
    }

    private static synchronized void captureCompletedFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        // A render-loop end alone may run while drawing is suppressed (e.g. a locked desktop).
        boolean produced = minecraft.screen == null ? renderedWorld : renderedScreen == minecraft.screen;
        renderedScreen = null;
        renderedWorld = false;
        if (!produced) return;
        completedFrames++;
        for (Capture capture : CAPTURES.values()) {
            if (capture.done() || capture.requested || completedFrames <= capture.beforeFrame
                    || contentRevision <= capture.beforeContent) continue;
            if (capture.sceneRevision != SFMClientControlLifecycle.runtime().sceneRevision()
                    || capture.screen != minecraft.screen || capture.width != minecraft.getMainRenderTarget().width
                    || capture.height != minecraft.getMainRenderTarget().height) {
                capture.failure = "stale_target: screen or framebuffer changed before capture";
                continue;
            }
            if (capture.width <= 0 || capture.height <= 0) {
                capture.failure = "render_unavailable: empty framebuffer";
                continue;
            }
            capture.requested = true;
            capture.capturedFrame = completedFrames;
            try { readFrame(minecraft, capture); }
            catch (Throwable failure) { capture.failure = failure.toString(); }
        }
    }

    @MCVersionDependentBehaviour
    private static void readFrame(Minecraft minecraft, Capture capture) {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("Framebuffer capture requires render thread");
{% case minecraft_version %}
{% when "26.1.2" %}
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> writeImage(capture, image));
{% else %}
        writeImage(capture, Screenshot.takeScreenshot(minecraft.getMainRenderTarget()));
{% endcase %}
    }

    private static void writeImage(Capture capture, NativeImage image) {
        if (capture.failure != null) { image.close(); return; }
        try {
            capture.write = CompletableFuture.runAsync(() -> {
            Path temporary = capture.output.resolveSibling(capture.output.getFileName() + ".tmp");
            try (image) {
                Files.createDirectories(capture.output.getParent());
                image.writeToFile(temporary);
                synchronized (SFMClientCaptureService.class) {
                    if (capture.failure != null) { Files.deleteIfExists(temporary); return; }
                    Files.move(temporary, capture.output, StandardCopyOption.ATOMIC_MOVE);
                    capture.written = true;
                }
            } catch (Throwable failure) {
                capture.failure = failure.toString();
                try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) { }
            }
            }, Util.ioPool());
        } catch (RuntimeException failure) {
            image.close();
            capture.failure = "capture_write_rejected: " + failure;
        }
    }

    private static JsonObject describe(Capture capture) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "sfm.client-capture/1");
        result.addProperty("capture_id", capture.id);
        result.addProperty("instance_id", SFMClientControlLifecycle.runtime().instanceId());
        result.addProperty("scene_revision", capture.sceneRevision);
        result.addProperty("status", capture.failure != null ? "failed" : capture.written ? "completed" : "pending");
        result.addProperty("error", capture.failure == null ? "" : capture.failure);
        result.addProperty("path", capture.written ? capture.output.toString() : "");
        result.addProperty("frame_before_request", capture.beforeFrame);
        result.addProperty("captured_frame", capture.capturedFrame);
        result.addProperty("width", capture.width);
        result.addProperty("height", capture.height);
        result.addProperty("screen", capture.screen == null ? "world" : capture.screen.getClass().getName());
        return result;
    }

    private static final class Capture {
        final String id, owner;
        final Object screen;
        final int width, height;
        final long beforeFrame, deadline;
        final long beforeContent = contentRevision;
        final long sceneRevision = SFMClientControlLifecycle.runtime().sceneRevision();
        final Path output;
        volatile boolean requested, written;
        volatile String failure;
        volatile long capturedFrame;
        volatile CompletableFuture<Void> write;
        Capture(String id, String owner, Object screen, int width, int height, long frame, long deadline, Path output) {
            this.id = id; this.owner = owner; this.screen = screen; this.width = width; this.height = height;
            this.beforeFrame = frame; this.deadline = deadline; this.output = output;
        }
        boolean done() { return written || failure != null; }
    }
}
