package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Bounded, non-destructive packet observation query exposed as a registered client action. */
public final class SFMPacketListAction implements SFMClientAction<SFMClientActionContext> {
    public static final String RESULT_SCHEMA = "sfm.packet.list/1";
    private static final String AFTER = "after-sequence";
    private static final String LIMIT = "limit";
    private static final String SESSION = "session-id";
    private static final SimpleCommandExceptionType INVALID_SESSION = new SimpleCommandExceptionType(
            Component.literal("Packet observation session must be a UUID")
    );

    private final Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider;
    private final BiFunction<Optional<SFMPacketObservationLog.Cursor>, Integer,
            Optional<SFMPacketObservationLog.Page>> pageProvider;

    public SFMPacketListAction() {
        SFMPacketObservationRuntime runtime = SFMPacketObservationRuntime.get();
        sessionProvider = runtime::currentSessionId;
        pageProvider = runtime::page;
    }

    SFMPacketListAction(
            Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider,
            BiFunction<Optional<SFMPacketObservationLog.Cursor>, Integer,
                    Optional<SFMPacketObservationLog.Page>> pageProvider
    ) {
        this.sessionProvider = sessionProvider;
        this.pageProvider = pageProvider;
    }

    @Override
    public Component title() {
        return Component.literal("List observed packets");
    }

    @Override
    public Component description() {
        return Component.literal("Read a bounded page from this private integrated-world packet session");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(afterBranch());
        node.then(limitBranch());
        node.then(sessionBranch());
    }

    private LiteralArgumentBuilder<SFMClientActionSource> afterBranch() {
        RequiredArgumentBuilder<SFMClientActionSource, Long> value = RequiredArgumentBuilder
                .<SFMClientActionSource, Long>argument(AFTER, LongArgumentType.longArg(0))
                .executes(this::invoke);
        value.then(limitBranch());
        value.then(sessionBranch());
        return LiteralArgumentBuilder.<SFMClientActionSource>literal("after").then(value);
    }

    private LiteralArgumentBuilder<SFMClientActionSource> limitBranch() {
        RequiredArgumentBuilder<SFMClientActionSource, Integer> value = RequiredArgumentBuilder
                .<SFMClientActionSource, Integer>argument(
                        LIMIT,
                        IntegerArgumentType.integer(1, SFMPacketObservationLog.MAX_PAGE_SIZE)
                )
                .executes(this::invoke);
        value.then(sessionBranch());
        return LiteralArgumentBuilder.<SFMClientActionSource>literal("limit").then(value);
    }

    private LiteralArgumentBuilder<SFMClientActionSource> sessionBranch() {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal("session")
                .then(RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument(SESSION, StringArgumentType.word())
                        .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext ignored,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        Optional<Long> requestedAfter = optionalArgument(context, AFTER, Long.class);
        int limit = optionalArgument(context, LIMIT, Integer.class)
                .orElse(SFMPacketObservationLog.DEFAULT_PAGE_SIZE);
        Optional<SFMPacketObservationLog.SessionId> requestedSession = parseOptionalSession(context);
        Optional<SFMPacketObservationLog.SessionId> currentSession = sessionProvider.get();
        if (currentSession.isEmpty()) {
            publishNoSession(context, requestedSession, requestedAfter);
            return 1;
        }

        SFMPacketObservationLog.SessionId cursorSession = requestedSession.orElseGet(currentSession::orElseThrow);
        Optional<SFMPacketObservationLog.Cursor> cursor = requestedAfter.isPresent() || requestedSession.isPresent()
                ? Optional.of(new SFMPacketObservationLog.Cursor(cursorSession, requestedAfter.orElse(0L)))
                : Optional.empty();
        Optional<SFMPacketObservationLog.Page> page = pageProvider.apply(cursor, limit);
        if (page.isEmpty()) {
            publishNoSession(context, requestedSession, requestedAfter);
            return 1;
        }

        SFMPacketObservationLog.Page value = page.orElseThrow();
        boolean staleSession = value.continuity() == SFMPacketObservationLog.Continuity.SESSION_CHANGED;
        JsonObject result = pageMetadata(value, requestedSession, requestedAfter);
        result.addProperty("status", staleSession ? "session_changed" : "ok");
        JsonArray entries = new JsonArray();
        if (!staleSession) {
            for (SFMPacketObservationLog.Entry entry : value.entries()) {
                JsonObject encodedEntry = new JsonObject();
                encodedEntry.addProperty("sequence", entry.sequence());
                encodedEntry.addProperty("payload_bytes", entry.payloadBytes());
                encodedEntry.add("value", JsonParser.parseString(entry.canonicalJson()));
                entries.add(encodedEntry);
            }
        }
        result.add("entries", entries);
        result.addProperty(
                "next_after_sequence",
                staleSession ? Math.max(0L, value.oldestSequence() - 1L) : value.nextCursor().afterSequence()
        );
        result.addProperty("has_more", !staleSession && value.hasMore());
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(RESULT_SCHEMA, result));
        context.getSource().sendFeedback(Component.literal(staleSession
                ? "Packet observation session changed; no entries were returned"
                : "Listed " + entries.size() + " packet observation(s); next cursor is "
                  + value.nextCursor().afterSequence()));
        return 1;
    }

    private static JsonObject pageMetadata(
            SFMPacketObservationLog.Page page,
            Optional<SFMPacketObservationLog.SessionId> requestedSession,
            Optional<Long> requestedAfter
    ) {
        JsonObject result = baseResult(requestedSession, requestedAfter);
        result.addProperty("session_id_present", true);
        result.addProperty("session_id", page.sessionId().value().toString());
        result.addProperty("oldest_sequence", page.oldestSequence());
        result.addProperty("newest_sequence", page.newestSequence());
        result.addProperty("retained_entry_count", page.retainedEntryCount());
        result.addProperty("retained_payload_bytes", page.retainedPayloadBytes());
        result.addProperty("continuity", page.continuity().name());
        return result;
    }

    private static JsonObject baseResult(
            Optional<SFMPacketObservationLog.SessionId> requestedSession,
            Optional<Long> requestedAfter
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", RESULT_SCHEMA);
        result.addProperty("requested_session_id_present", requestedSession.isPresent());
        result.addProperty(
                "requested_session_id",
                requestedSession.map(session -> session.value().toString()).orElse("")
        );
        result.addProperty("requested_after_sequence_present", requestedAfter.isPresent());
        result.addProperty("requested_after_sequence", requestedAfter.orElse(0L));
        return result;
    }

    private static void publishNoSession(
            CommandContext<SFMClientActionSource> context,
            Optional<SFMPacketObservationLog.SessionId> requestedSession,
            Optional<Long> requestedAfter
    ) {
        JsonObject result = baseResult(requestedSession, requestedAfter);
        result.addProperty("status", "no_session");
        result.addProperty("session_id_present", false);
        result.addProperty("session_id", "");
        result.addProperty("oldest_sequence", 0);
        result.addProperty("newest_sequence", 0);
        result.addProperty("retained_entry_count", 0);
        result.addProperty("retained_payload_bytes", 0);
        result.addProperty("continuity", "no_session");
        result.add("entries", new JsonArray());
        result.addProperty("next_after_sequence", requestedAfter.orElse(0L));
        result.addProperty("has_more", false);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(RESULT_SCHEMA, result));
        context.getSource().sendFeedback(Component.literal(
                "No private integrated-world packet observation session is active"));
    }

    private static Optional<SFMPacketObservationLog.SessionId> parseOptionalSession(
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        Optional<String> supplied = optionalArgument(context, SESSION, String.class);
        if (supplied.isEmpty()) {
            return Optional.empty();
        }
        try {
            String text = supplied.orElseThrow();
            UUID uuid = UUID.fromString(text);
            if (text.length() != 36 || !uuid.toString().equalsIgnoreCase(text)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return Optional.of(new SFMPacketObservationLog.SessionId(uuid));
        } catch (IllegalArgumentException invalid) {
            throw INVALID_SESSION.create();
        }
    }

    private static <T> Optional<T> optionalArgument(
            CommandContext<SFMClientActionSource> context,
            String name,
            Class<T> type
    ) {
        try {
            return Optional.of(context.getArgument(name, type));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }
}
