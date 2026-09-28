package ca.teamdman.sfm.client.control;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded bridge from the Minecraft Log4j stream to the live SFM control client. */
public final class SFMClientControlLogBuffer {
    private static final String APPENDER_NAME = "sfm-client-control-log-buffer";
    private static final int MAX_ENTRIES = 2_048;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static volatile boolean installed;

    private SFMClientControlLogBuffer() {
    }

    /** Installs once on the mod logger; all entries remain in memory only. */
    public static void install() {
        if (installed) return;
        synchronized (LOCK) {
            if (installed) return;
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                Configuration configuration = context.getConfiguration();
                LoggerConfig loggerConfig = configuration.getLoggerConfig("sfm");
                if (loggerConfig.getAppenders().containsKey(APPENDER_NAME)) {
                    installed = true;
                    return;
                }
                Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();
                Appender appender = new BufferAppender(APPENDER_NAME, layout);
                appender.start();
                loggerConfig.addAppender(appender, Level.ALL, null);
                context.updateLoggers();
                installed = true;
            } catch (RuntimeException failure) {
                // Logging must never prevent the client from starting. The next
                // lifecycle tick may retry installation after Log4j is ready.
                installed = false;
            }
        }
    }

    public static List<Entry> snapshot(int tail, Level minimum) {
        int boundedTail = Math.max(0, Math.min(tail, MAX_ENTRIES));
        synchronized (LOCK) {
            ArrayList<Entry> filtered = new ArrayList<>();
            for (Entry entry : ENTRIES) {
                Level entryLevel = Level.getLevel(entry.level());
                if (minimum == null || (entryLevel != null &&
                        (entryLevel.isMoreSpecificThan(minimum) || entryLevel == minimum))) {
                    filtered.add(entry);
                }
            }
            int from = Math.max(0, filtered.size() - boundedTail);
            return List.copyOf(filtered.subList(from, filtered.size()));
        }
    }

    public record Entry(long epochMillis, String level, String logger, String message) {
    }

    private static void append(LogEvent event) {
        String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
        if (!(loggerName.equals("sfm") || loggerName.startsWith("sfm."))) return;
        String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        if (event.getThrown() != null) {
            StringWriter rendered = new StringWriter();
            event.getThrown().printStackTrace(new PrintWriter(rendered));
            message = message + "\n" + rendered;
        }
        byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_MESSAGE_BYTES) {
            message = new String(bytes, 0, MAX_MESSAGE_BYTES, java.nio.charset.StandardCharsets.UTF_8)
                    + "…";
        }
        synchronized (LOCK) {
            ENTRIES.addLast(new Entry(
                    event.getTimeMillis(),
                    event.getLevel().name(),
                    loggerName,
                    message
            ));
            while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeFirst();
        }
    }

    private static final class BufferAppender extends AbstractAppender {
        private BufferAppender(String name, Layout<? extends Serializable> layout) {
            super(name, null, layout, true, null);
        }

        @Override
        public void append(LogEvent event) {
            SFMClientControlLogBuffer.append(event.toImmutable());
        }
    }
}
