package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.net.SFMClientPacketTransport;
import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Strict exact-address packet insertion request for private integrated worlds. */
public final class SFMPacketSendAction implements SFMClientAction<SFMClientActionContext> {
    public static final String RESULT_SCHEMA = "sfm.packet.send/1";
    private static final String DIMENSION = "dimension";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String SIDE = "side";
    private static final String SESSION = "session-id";
    private static final String VALUE = "value-json";
    private static final SimpleCommandExceptionType INVALID_SESSION = new SimpleCommandExceptionType(
            Component.literal("Packet observation session must be a UUID")
    );
    private static final SimpleCommandExceptionType INVALID_DIMENSION = new SimpleCommandExceptionType(
            Component.literal("Packet target dimension must be a resource location")
    );
    private static final SimpleCommandExceptionType INVALID_SIDE = new SimpleCommandExceptionType(
            Component.literal("Packet target side must be down, up, north, south, west, or east")
    );
    private static final SimpleCommandExceptionType INVALID_VALUE = new SimpleCommandExceptionType(
            Component.literal("Packet value must be strict bounded SFM value JSON")
    );
    private static final ArgumentType<String> DIMENSION_ARGUMENT = SFMPacketSendAction::parseDimension;

    private final Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider;
    private final BiFunction<SFMPacketInventoryAddress, SFMValue, Boolean> sender;

    public SFMPacketSendAction() {
        sessionProvider = SFMPacketObservationRuntime.get()::currentSessionId;
        sender = SFMClientPacketTransport::sendInsertion;
    }

    SFMPacketSendAction(
            Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider,
            BiFunction<SFMPacketInventoryAddress, SFMValue, Boolean> sender
    ) {
        this.sessionProvider = sessionProvider;
        this.sender = sender;
    }

    @Override
    public Component title() {
        return Component.literal("Send packet value");
    }

    @Override
    public Component description() {
        return Component.literal("Attempt one exact-address packet insertion in this private integrated world");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, Integer> z = RequiredArgumentBuilder
                .<SFMClientActionSource, Integer>argument(Z, IntegerArgumentType.integer());
        z.then(valueBranch());
        z.then(sideBranch());
        z.then(sessionBranch());
        node.then(RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(DIMENSION, DIMENSION_ARGUMENT)
                .then(RequiredArgumentBuilder
                        .<SFMClientActionSource, Integer>argument(X, IntegerArgumentType.integer())
                        .then(RequiredArgumentBuilder
                                .<SFMClientActionSource, Integer>argument(Y, IntegerArgumentType.integer())
                                .then(z))));
    }

    private ArgumentBuilder<SFMClientActionSource, ?> valueBranch() {
        return RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(VALUE, StringArgumentType.string())
                .executes(this::invoke);
    }

    private LiteralArgumentBuilder<SFMClientActionSource> sideBranch() {
        RequiredArgumentBuilder<SFMClientActionSource, String> side = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(SIDE, StringArgumentType.word());
        side.then(valueBranch());
        side.then(sessionBranch());
        return LiteralArgumentBuilder.<SFMClientActionSource>literal("side").then(side);
    }

    private LiteralArgumentBuilder<SFMClientActionSource> sessionBranch() {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal("session")
                .then(RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument(SESSION, StringArgumentType.word())
                        .then(valueBranch()));
    }

    @Override
    public int execute(
            SFMClientActionContext ignored,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        SFMPacketInventoryAddress target = parseAddress(context);
        SFMValue value = parseValue(StringArgumentType.getString(context, VALUE));
        String canonicalValue = SFMValueJsonCodec.encode(value);
        Optional<SFMPacketObservationLog.SessionId> requestedSession = parseOptionalSession(context);
        Optional<SFMPacketObservationLog.SessionId> currentSession = sessionProvider.get();

        String status;
        boolean locallyAccepted = false;
        if (currentSession.isEmpty()) {
            status = "no_session";
        } else if (requestedSession.isPresent()
                   && !requestedSession.orElseThrow().equals(currentSession.orElseThrow())) {
            status = "session_changed";
        } else {
            locallyAccepted = sender.apply(target, value);
            status = locallyAccepted ? "send_attempted" : "effects_disabled";
        }

        JsonObject result = new JsonObject();
        result.addProperty("schema", RESULT_SCHEMA);
        result.addProperty("status", status);
        result.addProperty("session_id_present", currentSession.isPresent());
        result.addProperty(
                "session_id",
                currentSession.map(session -> session.value().toString()).orElse("")
        );
        result.addProperty("requested_session_id_present", requestedSession.isPresent());
        result.addProperty(
                "requested_session_id",
                requestedSession.map(session -> session.value().toString()).orElse("")
        );
        result.addProperty("dimension", target.dimension().toString());
        result.addProperty("x", target.position().getX());
        result.addProperty("y", target.position().getY());
        result.addProperty("z", target.position().getZ());
        result.addProperty("side_present", target.side().isPresent());
        result.addProperty("side", target.side().map(side -> side.getName().toLowerCase(Locale.ROOT)).orElse(""));
        result.add("value", JsonParser.parseString(canonicalValue));
        result.addProperty("local_transport_accepted", locallyAccepted);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(RESULT_SCHEMA, result));
        context.getSource().sendFeedback(Component.literal(feedbackFor(status)));
        return 1;
    }

    private static String feedbackFor(String status) {
        return switch (status) {
            case "send_attempted" -> "Packet send was accepted by the local client transport; delivery is not acknowledged";
            case "effects_disabled" -> "Packet send was not attempted because private integrated-world effects are disabled";
            case "session_changed" -> "Packet send was not attempted because the world session changed";
            case "no_session" -> "Packet send was not attempted because no packet observation session is active";
            default -> throw new IllegalStateException("Unknown packet send status " + status);
        };
    }

    private static SFMPacketInventoryAddress parseAddress(
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        ResourceLocation dimension;
        try {
            dimension = new ResourceLocation(StringArgumentType.getString(context, DIMENSION));
        } catch (RuntimeException invalid) {
            throw INVALID_DIMENSION.create();
        }
        Optional<Direction> side = optionalArgument(context, SIDE, String.class).map(name -> {
            try {
                return Direction.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        });
        if (optionalArgument(context, SIDE, String.class).isPresent() && side.isEmpty()) {
            throw INVALID_SIDE.create();
        }
        return new SFMPacketInventoryAddress(
                dimension,
                new BlockPos(
                        IntegerArgumentType.getInteger(context, X),
                        IntegerArgumentType.getInteger(context, Y),
                        IntegerArgumentType.getInteger(context, Z)
                ),
                side
        );
    }

    private static String parseDimension(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        String token = reader.getString().substring(start, reader.getCursor());
        try {
            ResourceLocation parsed = new ResourceLocation(token);
            if (token.indexOf(':') <= 0 || !parsed.toString().equals(token)) {
                throw new IllegalArgumentException("non-canonical or implicit resource location");
            }
            return token;
        } catch (RuntimeException invalid) {
            reader.setCursor(start);
            throw INVALID_DIMENSION.createWithContext(reader);
        }
    }

    private static SFMValue parseValue(String json) throws CommandSyntaxException {
        try {
            return SFMValueJsonCodec.decode(json);
        } catch (IllegalArgumentException invalid) {
            throw INVALID_VALUE.create();
        }
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
