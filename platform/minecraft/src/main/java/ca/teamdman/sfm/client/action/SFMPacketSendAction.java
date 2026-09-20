package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.net.SFMClientPacketTransport;
import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.client.net.SFMMultiplayerClientRuntime;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.common.value.SFMValueSchema;
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
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Strict exact-address packet insertion request for private integrated worlds. */
public final class SFMPacketSendAction implements SFMClientAction<SFMClientActionContext> {
    public static final String RESULT_SCHEMA = "sfm.packet.send/1";
    private static final ResourceLocation PROGRAMMATIC_ID = new ResourceLocation("sfm", "packet/send");
    private static final SFMClientActionDescriptor PROGRAMMATIC_DESCRIPTOR = createProgrammaticDescriptor();
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
    @FunctionalInterface
    interface CallerSender {
        boolean send(SFMPacketInventoryAddress target, SFMValue value, Optional<ClientProgramIdentity> caller);
    }
    private final CallerSender callerSender;
    private final SFMClientActionAuthorizationService authorization;
    private final BooleanSupplier effectsAvailable;

    public SFMPacketSendAction() {
        this(() -> SFMMultiplayerClientRuntime.session().map(SFMPacketObservationLog.SessionId::new)
                        .or(SFMPacketObservationRuntime.get()::currentSessionId),
                SFMClientPacketTransport::sendInsertion,
                SFMClientActionAuthorizationService.shared(),
                SFMClientPacketTransport::effectsAllowedNow, SFMClientPacketTransport::sendInsertion);
    }

    SFMPacketSendAction(
            Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider,
            BiFunction<SFMPacketInventoryAddress, SFMValue, Boolean> sender
    ) {
        this(sessionProvider, sender, new SFMClientActionAuthorizationService(
                ignored -> Optional.of(PROGRAMMATIC_DESCRIPTOR),
                new ca.teamdman.sfm.common.net.SFMBoundedEffectBudget(32, 64 * 1024),
                () -> System.nanoTime() / 1_000_000_000L), () -> true);
    }

    SFMPacketSendAction(
            Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider,
            BiFunction<SFMPacketInventoryAddress, SFMValue, Boolean> sender,
            SFMClientActionAuthorizationService authorization,
            BooleanSupplier effectsAvailable
    ) {
        this(sessionProvider, sender, authorization, effectsAvailable, (target, value, caller) -> sender.apply(target, value));
    }

    SFMPacketSendAction(Supplier<Optional<SFMPacketObservationLog.SessionId>> sessionProvider,
                       BiFunction<SFMPacketInventoryAddress, SFMValue, Boolean> sender,
                       SFMClientActionAuthorizationService authorization, BooleanSupplier effectsAvailable,
                       CallerSender callerSender) {
        this.sessionProvider = sessionProvider;
        this.sender = sender;
        this.authorization = authorization;
        this.effectsAvailable = effectsAvailable;
        this.callerSender = callerSender;
    }

    @Override
    public Component title() {
        return Component.literal("Send packet value");
    }

    @Override
    public Component description() {
        return Component.literal("Attempt one exact-address insertion; remote worlds require a negotiated session and exact server grant");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public Optional<SFMClientActionDescriptor> programmaticDescriptor() {
        return Optional.of(PROGRAMMATIC_DESCRIPTOR);
    }

    @Override
    public Optional<SFMClientActionProgrammaticHandler> programmaticHandler() {
        return Optional.of((input, context) -> {
            Map<String, SFMValue> fields = ((SFMValue.ObjectValue) input).fields();
            SFMValue side = fields.getOrDefault("side", SFMValue.nullValue());
            SFMPacketInventoryAddress target = new SFMPacketInventoryAddress(
                    new ResourceLocation(((SFMValue.StringValue) fields.get("dimension")).value()),
                    new BlockPos((int) ((SFMValue.LongValue) fields.get("x")).value(),
                            (int) ((SFMValue.LongValue) fields.get("y")).value(),
                            (int) ((SFMValue.LongValue) fields.get("z")).value()),
                    side instanceof SFMValue.StringValue name
                            ? Optional.of(Direction.valueOf(name.value().toUpperCase(Locale.ROOT))) : Optional.empty());
            boolean accepted = effectsAvailable.getAsBoolean() && callerSender.send(target, fields.get("value"), context.caller());
            return SFMValue.object(Map.of("status", SFMValue.of(accepted ? "send_attempted" : "effects_disabled"),
                    "local_transport_accepted", SFMValue.of(accepted)));
        });
    }

    /** A typed program contract; the existing human Brigadier grammar is unchanged. */
    private static SFMClientActionDescriptor createProgrammaticDescriptor() {
        SFMValueSchema direction = SFMValueSchema.union(List.of(
                SFMValueSchema.literal(SFMValue.of("down")),
                SFMValueSchema.literal(SFMValue.of("up")),
                SFMValueSchema.literal(SFMValue.of("north")),
                SFMValueSchema.literal(SFMValue.of("south")),
                SFMValueSchema.literal(SFMValue.of("west")),
                SFMValueSchema.literal(SFMValue.of("east"))
        ));
        SFMValueSchema input = SFMValueSchema.object(Map.of(
                "dimension", SFMValueSchema.Field.required(SFMValueSchema.string(1, 256)),
                "x", SFMValueSchema.Field.required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
                "y", SFMValueSchema.Field.required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
                "z", SFMValueSchema.Field.required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
                "side", SFMValueSchema.Field.optional(SFMValueSchema.optional(direction)),
                "value", SFMValueSchema.Field.required(SFMValueSchema.any())
        ), false);
        SFMValueSchema status = SFMValueSchema.union(List.of(
                SFMValueSchema.literal(SFMValue.of("send_attempted")),
                SFMValueSchema.literal(SFMValue.of("effects_disabled")),
                SFMValueSchema.literal(SFMValue.of("session_changed")),
                SFMValueSchema.literal(SFMValue.of("no_session")),
                SFMValueSchema.literal(SFMValue.of("rate_limited")),
                SFMValueSchema.literal(SFMValue.of("target_unauthorized")),
                SFMValueSchema.literal(SFMValue.of("invalid_input"))
        ));
        SFMValueSchema result = SFMValueSchema.object(Map.of(
                "status", SFMValueSchema.Field.required(status),
                "local_transport_accepted", SFMValueSchema.Field.required(SFMValueSchema.bool())
        ), true);
        return new SFMClientActionDescriptor(
                PROGRAMMATIC_ID,
                input,
                result,
                SFMClientActionDescriptor.ExecutionSide.CLIENT,
                PROGRAMMATIC_ID,
                SFMPacketSendAction::resolveProgrammaticScope,
                SFMClientActionDescriptor.CostClass.SERVER_EFFECT,
                SFMClientActionDescriptor.Acknowledgement.LOCAL_TRANSPORT_ATTEMPT_ONLY,
                Map.of(
                        "send_attempted", SFMClientActionDescriptor.StatusKind.ATTEMPTED,
                        "effects_disabled", SFMClientActionDescriptor.StatusKind.REJECTED,
                        "session_changed", SFMClientActionDescriptor.StatusKind.REJECTED,
                        "no_session", SFMClientActionDescriptor.StatusKind.REJECTED,
                        "rate_limited", SFMClientActionDescriptor.StatusKind.REJECTED,
                        "target_unauthorized", SFMClientActionDescriptor.StatusKind.REJECTED,
                        "invalid_input", SFMClientActionDescriptor.StatusKind.REJECTED
                )
        );
    }

    private static SFMClientActionDescriptor.InputCheck resolveProgrammaticScope(SFMValue input) {
        Map<String, SFMValue> fields = ((SFMValue.ObjectValue) input).fields();
        try {
            SFMValueJsonCodec.encode(fields.get("value"));
        } catch (IllegalArgumentException invalid) {
            return new SFMClientActionDescriptor.InputCheck.Rejected(
                    new SFMValueSchema.Failure("packet_value_out_of_bounds", "/value")
            );
        }
        String dimension = ((SFMValue.StringValue) fields.get("dimension")).value();
        try {
            ResourceLocation parsed = new ResourceLocation(dimension);
            if (dimension.indexOf(':') <= 0 || !parsed.toString().equals(dimension)) {
                throw new IllegalArgumentException("non-canonical dimension");
            }
        } catch (RuntimeException invalid) {
            return new SFMClientActionDescriptor.InputCheck.Rejected(
                    new SFMValueSchema.Failure("invalid_identifier", "/dimension")
            );
        }
        SFMValue subject = SFMValue.object(Map.of(
                "dimension", fields.get("dimension"),
                "x", fields.get("x"),
                "y", fields.get("y"),
                "z", fields.get("z"),
                "side", fields.getOrDefault("side", SFMValue.nullValue())
        ));
        return new SFMClientActionDescriptor.InputCheck.Accepted(List.of(
                new SFMClientActionDescriptor.DataScope(PROGRAMMATIC_ID, subject)
        ));
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
            SFMClientActionAuthorizationService.EffectAttempt attempt = authorization.performHuman(
                    PROGRAMMATIC_DESCRIPTOR,
                    programmaticInput(target, value),
                    effectsAvailable,
                    validated -> sender.apply(target, value)
            );
            locallyAccepted = attempt.localTransportAccepted();
            status = switch (attempt.authorization().status()) {
                case ALLOWED -> locallyAccepted ? "send_attempted" : "effects_disabled";
                case RATE_LIMITED -> "rate_limited";
                case TARGET_UNAUTHORIZED, CAPABILITY_UNDECLARED, ACTION_UNDESCRIBED,
                     WRONG_EXECUTION_SIDE, STALE_PROGRAM_CONTEXT, AWAITING_CONSENT, DENIED_BY_USER, BLOCKED_BY_POLICY -> "target_unauthorized";
                case INVALID_INPUT -> "invalid_input";
                case EFFECTS_DISABLED -> "effects_disabled";
            };
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
            case "send_attempted" -> "Packet send accepted locally; downstream processing is not acknowledged. Remote receipts: packet/remote_status";
            case "effects_disabled" -> "Packet send was not attempted because the required private or negotiated remote transport is unavailable";
            case "session_changed" -> "Packet send was not attempted because the world session changed";
            case "no_session" -> "Packet send was not attempted because no packet observation session is active";
            case "rate_limited" -> "Packet send was not attempted because the local effect rate limit was reached";
            case "target_unauthorized" -> "Packet send was not attempted because the target is not authorized";
            case "invalid_input" -> "Packet send was not attempted because its typed input is invalid";
            default -> throw new IllegalStateException("Unknown packet send status " + status);
        };
    }

    private static SFMValue programmaticInput(SFMPacketInventoryAddress target, SFMValue value) {
        Map<String, SFMValue> fields = new HashMap<>(Map.of(
                "dimension", SFMValue.of(target.dimension().toString()),
                "x", SFMValue.of(target.position().getX()),
                "y", SFMValue.of(target.position().getY()),
                "z", SFMValue.of(target.position().getZ()),
                "value", value
        ));
        target.side().ifPresent(side -> fields.put("side", SFMValue.of(side.getName().toLowerCase(Locale.ROOT))));
        return SFMValue.object(fields);
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
