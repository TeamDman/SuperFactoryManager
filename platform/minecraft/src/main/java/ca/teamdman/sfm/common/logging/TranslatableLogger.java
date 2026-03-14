package ca.teamdman.sfm.common.logging;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.ManagerBlock;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.*;
import java.util.function.Consumer;

public class TranslatableLogger {
    private static final LoggerContext CONTEXT = new LoggerContext(SFM.MOD_ID);

    private static final HashSet<String> SEEN_NAMES = new HashSet<>();

    private final Logger logger;

    private Level logLevel = Level.OFF;

    /// Logger names MUST be unique for the same {@link ManagerBlockEntity} between the logical client and server.
    ///
    /// If the name is not unique, then when running in a single player scenario,
    /// the changes to the logger by a client-side {@link ManagerBlockEntity} can infect the logger used by the server.
    ///
    /// This happens in game tests that call {@link #setLogLevel(Level)};
    /// 1. The server constructs a {@link ManagerBlockEntity} for a new {@link ManagerBlock}.
    /// 2. The server calls {@link #setLogLevel(Level)}.
    /// 3. The client is informed about the new {@link ManagerBlock}.
    /// 4. The client constructs a {@link ManagerBlockEntity}.
    /// 5. The client constructor clobbers the log level if the name is not unique.
    ///
    /// See the {@link #createName(BlockPos, ManagerBlockEntity)} convenience helper.
    public TranslatableLogger(String name) {

        if (SFMEnvironmentUtils.isInIDE()) {
            boolean fresh = SEEN_NAMES.add(name);
            if (!fresh) {
                throw new IllegalStateException("Cannot create multiple loggers with the same name: " + name);
            }
        }

        // Create logger
        this.logger = CONTEXT.getLogger(name);

        // Register the config to the logger
        Configuration configuration = CONTEXT.getConfiguration();
        configuration.removeLogger(name);
        LoggerConfig config = new LoggerConfig(name, logLevel, false);
        configuration.addLogger(name, config);

        // Create appender
        TranslatableAppender appender = TranslatableAppender.createAppender(name);

        // Unregister any previous appenders that may have been created for this location
        config.removeAppender(name);

        // Attach the appender to the logger
        config.addAppender(appender, Level.TRACE, null);

        // Start the appender
        appender.start();
    }

    public static String createName(BlockPos pos, ManagerBlockEntity managerBlockEntity) {
        return SFM.MOD_ID
               + ":manager@"
               + pos.toShortString() + "@" + Integer.toHexString(System.identityHashCode(managerBlockEntity));
    }

    public Level getLogLevel() {

        return CONTEXT.getConfiguration().getLoggerConfig(logger.getName()).getLevel();
    }

    public void setLogLevel(Level level) {

        LoggerConfig found = CONTEXT.getConfiguration().getLoggerConfig(logger.getName());
        found.setLevel(level);
        this.logLevel = level;
        CONTEXT.updateLoggers();
    }

    public void info(TranslatableContents contents) {

        if (this.logLevel.isLessSpecificThan(Level.INFO)) {
            logger.info(contents.getKey(), contents.getArgs());
        }
    }

    public void info(Consumer<Consumer<TranslatableContents>> logger) {

        if (this.logLevel.isLessSpecificThan(Level.INFO)) {
//            logger.accept(contents -> this.logger.info(contents.getKey(), contents.getArgs()));
            logger.accept(this::info);
        }
    }

    public void warn(TranslatableContents contents) {

        if (this.logLevel.isLessSpecificThan(Level.WARN)) {
            logger.warn(contents.getKey(), contents.getArgs());
        }
    }

    public void warn(Consumer<Consumer<TranslatableContents>> logger) {

        if (this.logLevel.isLessSpecificThan(Level.WARN)) {
//            logger.accept(contents -> this.logger.warn(contents.getKey(), contents.getArgs()));
            logger.accept(this::warn);
        }
    }

    public void error(TranslatableContents contents) {

        if (this.logLevel.isLessSpecificThan(Level.ERROR)) {
            logger.error(contents.getKey(), contents.getArgs());
        }
    }

    public void error(Consumer<Consumer<TranslatableContents>> logger) {

        if (this.logLevel.isLessSpecificThan(Level.ERROR)) {
//            logger.accept(contents -> this.logger.error(contents.getKey(), contents.getArgs()));
            logger.accept(this::error);
        }
    }

    public void debug(TranslatableContents contents) {

        if (this.logLevel.isLessSpecificThan(Level.DEBUG)) {
            logger.debug(contents.getKey(), contents.getArgs());
        }
    }

    // TODO v5: add a Consumer<TranslatableContents> overload
    public void debug(Consumer<Consumer<TranslatableContents>> logger) {

        if (this.logLevel.isLessSpecificThan(Level.DEBUG)) {
//            logger.accept(contents -> this.logger.debug(contents.getKey(), contents.getArgs()));
            logger.accept(this::debug);
        }
    }

    public void trace(TranslatableContents contents) {

        if (this.logLevel.isLessSpecificThan(Level.TRACE)) {
            logger.trace(contents.getKey(), contents.getArgs());
        }
    }

    public void trace(Consumer<Consumer<TranslatableContents>> logger) {

        if (this.logLevel.isLessSpecificThan(Level.TRACE)) {
//            logger.accept(contents -> this.logger.trace(contents.getKey(), contents.getArgs()));
            logger.accept(this::trace);
        }
    }

    public ArrayDeque<TranslatableLogEvent> getLogs() {

        return new ArrayDeque<>(getContents());
    }

    public void clear() {

        getContents().clear();
    }

    public static ArrayDeque<TranslatableLogEvent> decode(FriendlyByteBuf buf) {

        int size = buf.readVarInt();
        ArrayDeque<TranslatableLogEvent> contents = new ArrayDeque<>(size);
        for (int i = 0; i < size; i++) {
            contents.add(TranslatableLogEvent.decode(buf));
        }
        return contents;
    }

    /**
     * Writes logs to the buffer.
     * Will safely stop writing once the buffer is full.
     * Will remove from the list the logs that were written.
     *
     * @see NetworkHooks#openScreen(ServerPlayer, MenuProvider, Consumer) the byte limit
     */
    public static void encodeAndDrain(
            Collection<TranslatableLogEvent> logs,
            FriendlyByteBuf buf
    ) {

        int maxReadableBytes = 32600;
        FriendlyByteBuf chunk = new FriendlyByteBuf(Unpooled.buffer());
        int count = 0;
        for (Iterator<TranslatableLogEvent> iterator = logs.iterator(); iterator.hasNext(); ) {
            TranslatableLogEvent entry = iterator.next();
            FriendlyByteBuf check = new FriendlyByteBuf(Unpooled.buffer());
            entry.encode(check);
            if (check.readableBytes() + chunk.readableBytes() + buf.readableBytes() >= maxReadableBytes) {
                break;
            }
            chunk.writeBytes(check);
            iterator.remove();
            count += 1;
        }

        buf.writeVarInt(count);
        buf.writeBytes(chunk);
    }

    public ArrayDeque<TranslatableLogEvent> getLogsAfter(SFMEpochInstant instant) {

        List<TranslatableLogEvent> contents = getContents();
        ArrayDeque<TranslatableLogEvent> toSend = new ArrayDeque<>();
        // Add from tail until we reach the last sent marker
        var iter = contents.listIterator(contents.size());
        while (iter.hasPrevious()) {
            var entry = iter.previous();
            if (entry.instant().compareTo(instant) > 0) {
                toSend.addFirst(entry);
            } else {
                break;
            }
        }
        return toSend;
    }

    public void pruneSoWeDontEatAllTheRam() {

        List<TranslatableLogEvent> contents = getContents();
        if (contents.size() > 10_000) {
            int overage = contents.size() - 10_000;
            int to_prune = overage + 500;
            contents.subList(0, to_prune).clear();
        }
    }

    private LinkedList<TranslatableLogEvent> getContents() {

        var appenders = CONTEXT.getConfiguration().getLoggerConfig(logger.getName()).getAppenders();
        if (appenders.containsKey(logger.getName())) {
            var appender = appenders.get(logger.getName());
            if (appender instanceof TranslatableAppender ta) {
                return ta.contents;
            }
        }
        return new LinkedList<>();
    }

}
