package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientUiFreezeWatchdogHandler {
    private static final long UI_STALL_THRESHOLD_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long WATCHDOG_POLL_INTERVAL_MS = 500L;

    private static final AtomicBoolean watchdogStarted = new AtomicBoolean(false);
    private static final AtomicBoolean stallReported = new AtomicBoolean(false);
    private static final AtomicLong lastUiTickNanos = new AtomicLong(System.nanoTime());
    private static final AtomicReference<Thread> uiThread = new AtomicReference<>();

    private ClientUiFreezeWatchdogHandler() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!SFMEnvironmentUtils.isInIDE()) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        uiThread.compareAndSet(null, Thread.currentThread());
        ensureWatchdogStarted();

        long now = System.nanoTime();
        long previousTick = lastUiTickNanos.getAndSet(now);
        if (stallReported.getAndSet(false)) {
            SFM.LOGGER.warn(
                    "SFM UI freeze watchdog observed the render thread recover after {} ms without a client tick.",
                    TimeUnit.NANOSECONDS.toMillis(now - previousTick)
            );
        }
    }

    private static void ensureWatchdogStarted() {
        if (!watchdogStarted.compareAndSet(false, true)) {
            return;
        }

        Thread watchdogThread = new Thread(ClientUiFreezeWatchdogHandler::watchdogLoop, "SFM UI Freeze Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    private static void watchdogLoop() {
        while (true) {
            try {
                Thread.sleep(WATCHDOG_POLL_INTERVAL_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            Thread renderThread = uiThread.get();
            if (renderThread == null) {
                continue;
            }

            long stalledNanos = System.nanoTime() - lastUiTickNanos.get();
            if (stalledNanos < UI_STALL_THRESHOLD_NANOS) {
                continue;
            }
            if (!stallReported.compareAndSet(false, true)) {
                continue;
            }

            logUiThreadStall(renderThread, stalledNanos);
        }
    }

    private static void logUiThreadStall(
            Thread renderThread,
            long stalledNanos
    ) {
        SFM.LOGGER.error(
                "SFM UI freeze watchdog detected {} ms without a client tick. Render thread='{}' state={} screen={}",
                TimeUnit.NANOSECONDS.toMillis(stalledNanos),
                renderThread.getName(),
                renderThread.getState(),
                currentScreenDescription()
        );

        StackTraceElement[] stackTrace = renderThread.getStackTrace();
        if (stackTrace.length == 0) {
            SFM.LOGGER.error("SFM UI freeze watchdog could not capture a render-thread stack trace.");
            return;
        }

        for (StackTraceElement stackTraceElement : stackTrace) {
            SFM.LOGGER.error("    at {}", stackTraceElement);
        }
    }

    private static String currentScreenDescription() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.screen == null ? "<none>" : minecraft.screen.getClass().getName();
        } catch (Exception exception) {
            return "<unavailable:" + exception.getClass().getSimpleName() + ">";
        }
    }
}