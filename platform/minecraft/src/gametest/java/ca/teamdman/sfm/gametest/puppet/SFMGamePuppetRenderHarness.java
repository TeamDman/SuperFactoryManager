package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.Objects;

/**
 * Schedules a test interaction immediately before one real render of an exact
 * screen instance. This keeps EditorV3 input-to-frame telemetry independent of
 * time spent waiting for the automation desktop to begin another outer frame.
 */
public final class SFMGamePuppetRenderHarness {
    public record Ticket(long id) {
    }

    private record Pending(Ticket ticket, Screen target, Runnable task) {
    }

    private record Completion(Ticket ticket, Throwable failure) {
    }

    private static long nextTicketId;
    private static Pending pending;
    private static Completion completion;

    private SFMGamePuppetRenderHarness() {
    }

    public static synchronized Ticket beforeNextFrame(Screen target, Runnable task) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(task, "task");
        if (pending != null || completion != null) {
            throw new IllegalStateException("A game-puppet render task is already outstanding");
        }
        Ticket ticket = new Ticket(++nextTicketId);
        pending = new Pending(ticket, target, task);
        return ticket;
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onBeforeScreenRender(ScreenEvent.Render.Pre event) {
        Pending scheduled;
        synchronized (SFMGamePuppetRenderHarness.class) {
            scheduled = pending;
            if (scheduled == null || scheduled.target() != event.getScreen()) return;
            pending = null;
        }

        Throwable failure = null;
        try {
            scheduled.task().run();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        synchronized (SFMGamePuppetRenderHarness.class) {
            completion = new Completion(scheduled.ticket(), failure);
        }
    }

    public static synchronized boolean await(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        if (pending != null && pending.ticket().equals(ticket)) return false;
        if (completion == null) return false;
        if (!completion.ticket().equals(ticket)) {
            throw new IllegalStateException(
                    "Unexpected game-puppet render completion " + completion.ticket().id()
                            + " while awaiting " + ticket.id()
            );
        }
        Throwable failure = completion.failure();
        completion = null;
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Game-puppet render task failed", failure);
        return true;
    }

    public static synchronized void clear() {
        pending = null;
        completion = null;
    }
}
