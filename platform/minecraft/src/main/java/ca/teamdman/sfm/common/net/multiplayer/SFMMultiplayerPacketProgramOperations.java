package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/**
 * Common-side, non-executing extraction of network scopes from the actual stored client program.
 * No client registry/classes are loaded. This is an additional server restriction, not client
 * consent or proof that a claimed program executed. Operator ACLs remain independently required.
 */
public final class SFMMultiplayerPacketProgramOperations {
    public static final int MAX_TOKENS = 4_096;
    public static final int MAX_VARIABLES = 64;
    public static final int MAX_TRIGGERS = 32;
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
    private static final ResourceLocation READ = new ResourceLocation("sfm", "client_inbox/read");

    private SFMMultiplayerPacketProgramOperations() {}

    /** The dimension comes from the server's manager block, never an inbox argument or wire claim. */
    public static Set<ProgramOperation> extract(String source, ResourceLocation executionDimension) {
        Objects.requireNonNull(executionDimension);
        if (!permitsSource(source)) throw new IllegalArgumentException("Client program exceeds extraction source bounds");
        var result = new ProgramBuilder(source).useCache(false).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        if (!result.isBuildSuccessful() || result.program() == null) {
            throw new IllegalArgumentException("Cannot extract operations from an invalid client program");
        }
        Program program = result.program();
        if (program.triggers().isEmpty() || program.triggers().size() > MAX_TRIGGERS) {
            throw new IllegalArgumentException("Client program trigger count is out of bounds");
        }
        Set<ProgramOperation> operations = new HashSet<>();
        for (Trigger trigger : program.triggers()) {
            if (!(trigger instanceof FrameTrigger frame)) throw new IllegalArgumentException("Expected client frame triggers");
            walk(frame.block(), executionDimension, new HashMap<>(), operations, 0);
        }
        return Set.copyOf(operations);
    }

    /** Iterative pre-parser bounds, deliberately conservative for keyword-shaped label names. */
    public static boolean permitsSource(String source) {
        if (source == null || source.length() > Program.MAX_PROGRAM_LENGTH) return false;
        try {
            if (ProgramSignatureDescriptor.normalizedSourceBytes(source).length > ClientManagerProgramProjection.MAX_SOURCE_BYTES) return false;
        } catch (IllegalArgumentException invalidUnicode) { return false; }
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        boolean[] invalid = {false};
        lexer.addErrorListener(new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line,
                                              int column, String message, RecognitionException exception) {
                invalid[0] = true;
            }
        });
        int count = 0;
        int scopes = 0;
        int parentheses = 0;
        int booleanOperators = 0;
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (invalid[0]) return false;
            if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
            if (++count > MAX_TOKENS) return false;
            switch (token.getType()) {
                case SFMLLexer.IF, SFMLLexer.EVERY -> scopes++;
                case SFMLLexer.END -> { scopes = Math.max(0, scopes - 1); booleanOperators = 0; }
                case SFMLLexer.LPAREN -> parentheses++;
                case SFMLLexer.RPAREN -> parentheses = Math.max(0, parentheses - 1);
                case SFMLLexer.NOT, SFMLLexer.AND, SFMLLexer.OR -> booleanOperators++;
                case SFMLLexer.THEN, SFMLLexer.DO -> booleanOperators = 0;
                default -> { }
            }
            if (scopes > FrameTrigger.MAX_NESTING || parentheses > FrameTrigger.MAX_NESTING
                || booleanOperators > FrameTrigger.MAX_NESTING) return false;
        }
        return !invalid[0];
    }

    private static void walk(Block block, ResourceLocation dimension, Map<String, Optional<SFMValue>> constants,
                             Set<ProgramOperation> operations, int depth) {
        if (depth > FrameTrigger.MAX_NESTING) throw new IllegalArgumentException("Operation extraction nesting exceeded");
        for (Statement statement : block.statements()) {
            if (statement instanceof LetStatement let) {
                Optional<SFMValue> value;
                if (let.expression() instanceof ClientValueExpression.JsonLiteral literal) {
                    value = Optional.of(literal.value());
                } else if (let.expression() instanceof ClientValueExpression.Field field) {
                    value = require(constants, field.variable()).map(source -> {
                        if (!(source instanceof SFMValue.ObjectValue object) || !object.fields().containsKey(field.field())) {
                            throw new IllegalArgumentException("Field absent from constant client value");
                        }
                        return object.fields().get(field.field());
                    });
                } else if (let.expression() instanceof ClientValueExpression.Invoke invoke) {
                    Optional<SFMValue> input = require(constants, invoke.argument());
                    if (invoke.action().equals(SEND) || invoke.action().equals(READ)) {
                        SFMValue argument = input.orElseThrow(() -> new IllegalArgumentException("Network action target is not statically resolvable"));
                        operations.add(invoke.action().equals(SEND) ? send(argument) : read(argument, dimension));
                        if (operations.size() > MAX_PROGRAM_SCOPES) throw new IllegalArgumentException("Too many network scopes");
                    }
                    // Unknown/other actions cannot create network authority or a known return value.
                    value = Optional.empty();
                } else throw new IllegalArgumentException("Unsupported client expression");
                constants.put(key(let.variableName()), value);
                if (constants.size() > MAX_VARIABLES) throw new IllegalArgumentException("Too many client bindings");
            } else if (statement instanceof IfStatement branch) {
                validateCondition(branch.condition(), constants);
                // Both paths matter regardless of the current condition; bindings remain branch-local.
                walk(branch.trueBlock(), dimension, new HashMap<>(constants), operations, depth + 1);
                walk(branch.falseBlock(), dimension, new HashMap<>(constants), operations, depth + 1);
            } else if (!(statement instanceof RenderImageStatement)) {
                throw new IllegalArgumentException("Unsupported client statement");
            }
        }
    }

    private static ProgramOperation send(SFMValue input) {
        Map<String, SFMValue> fields = object(input, Set.of("dimension", "x", "y", "z", "value"), Set.of("side"));
        String dimension = string(fields.get("dimension"));
        ResourceLocation parsed = identifier(dimension);
        if (dimension.indexOf(':') <= 0 || !parsed.toString().equals(dimension)) {
            throw new IllegalArgumentException("Packet dimension must be canonical");
        }
        Optional<Direction> side = Optional.empty();
        SFMValue rawSide = fields.getOrDefault("side", SFMValue.nullValue());
        if (!(rawSide instanceof SFMValue.NullValue)) {
            String name = string(rawSide);
            if (!Set.of("down", "up", "north", "south", "west", "east").contains(name)) {
                throw new IllegalArgumentException("Invalid inventory side");
            }
            side = Optional.of(Direction.valueOf(name.toUpperCase(Locale.ROOT)));
        }
        // The argument envelope is larger than a packet value; enforce the actual packet limit.
        SFMValueJsonCodec.encode(fields.get("value"));
        var target = new SFMPacketInventoryAddress(parsed, new BlockPos(integer(fields.get("x")),
                integer(fields.get("y")), integer(fields.get("z"))), side);
        return new ProgramOperation(Action.PACKET_SEND, new InventoryScope(target));
    }

    private static ProgramOperation read(SFMValue input, ResourceLocation dimension) {
        Map<String, SFMValue> fields = object(input, Set.of("channel", "mode"), Set.of());
        if (!string(fields.get("mode")).equals("latest")) throw new IllegalArgumentException("Unsupported inbox read mode");
        // Match the existing action: a bare channel canonicalizes to Minecraft's default namespace.
        return new ProgramOperation(Action.INBOX_SUBSCRIBE, new InboxScope(dimension, identifier(string(fields.get("channel")))));
    }

    private static Map<String, SFMValue> object(SFMValue value, Set<String> required, Set<String> optional) {
        if (!(value instanceof SFMValue.ObjectValue object) || !object.fields().keySet().containsAll(required)
            || object.fields().keySet().stream().anyMatch(name -> !required.contains(name) && !optional.contains(name))) {
            throw new IllegalArgumentException("Invalid network action argument fields");
        }
        return object.fields();
    }

    private static String string(SFMValue value) {
        if (!(value instanceof SFMValue.StringValue string) || string.value().isEmpty()
            || string.value().length() > MAX_IDENTIFIER_CHARACTERS) throw new IllegalArgumentException("Expected bounded string");
        return string.value();
    }

    private static ResourceLocation identifier(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null || parsed.toString().length() > MAX_IDENTIFIER_CHARACTERS) throw new IllegalArgumentException("Invalid identifier");
        return parsed;
    }

    private static int integer(SFMValue value) {
        if (!(value instanceof SFMValue.LongValue integer)
            || integer.value() < Integer.MIN_VALUE || integer.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected exact 32-bit integer coordinate");
        }
        return (int) integer.value();
    }

    private static Optional<SFMValue> require(Map<String, Optional<SFMValue>> constants, String name) {
        Optional<SFMValue> value = constants.get(key(name));
        if (value == null) throw new IllegalArgumentException("Unbound client value");
        return value;
    }

    private static void validateCondition(BoolExpr condition, Map<String, Optional<SFMValue>> values) {
        if (condition instanceof BoolClientValueEquals comparison) require(values, comparison.variable());
        else if (condition instanceof BoolParen paren) validateCondition(paren.inner(), values);
        else if (condition instanceof BoolNegation not) validateCondition(not.inner(), values);
        else if (condition instanceof BoolConjunction both) { validateCondition(both.left(), values); validateCondition(both.right(), values); }
        else if (condition instanceof BoolDisjunction either) { validateCondition(either.left(), values); validateCondition(either.right(), values); }
    }

    private static String key(String variable) { return variable.toLowerCase(Locale.ROOT); }
}
