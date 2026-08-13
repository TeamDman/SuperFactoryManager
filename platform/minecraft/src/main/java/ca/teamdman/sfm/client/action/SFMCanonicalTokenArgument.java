package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

/**
 * One non-whitespace canonical token.
 *
 * <p>Brigadier's {@code word()} intentionally excludes URI and expression
 * punctuation. SFM canonical selectors and paths percent-encode whitespace,
 * so consuming every non-whitespace character is both unambiguous and avoids
 * wrapping the actual canonical value in a presentation-only quoted string.</p>
 */
public final class SFMCanonicalTokenArgument implements ArgumentType<String> {
    private static final SFMCanonicalTokenArgument INSTANCE = new SFMCanonicalTokenArgument();
    private static final SimpleCommandExceptionType EXPECTED = new SimpleCommandExceptionType(
            Component.literal("Expected one non-whitespace canonical token")
    );

    private SFMCanonicalTokenArgument() {
    }

    public static SFMCanonicalTokenArgument token() {
        return INSTANCE;
    }

    public static String get(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        if (reader.getCursor() == start) throw EXPECTED.createWithContext(reader);
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public Collection<String> getExamples() {
        return List.of(
                "id(explorer-1)",
                "file:///C:/project",
                "members(id(explorer-1-location))"
        );
    }
}
