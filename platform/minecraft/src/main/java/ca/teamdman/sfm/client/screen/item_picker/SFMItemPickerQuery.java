package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.ResourceLimit;
import ca.teamdman.sfml.ast.With;
import ca.teamdman.sfml.ast.WithAlwaysTrue;
import ca.teamdman.sfml.ast.WithClause;
import ca.teamdman.sfml.ast.WithConjunction;
import ca.teamdman.sfml.ast.WithDisjunction;
import ca.teamdman.sfml.ast.WithNegation;
import ca.teamdman.sfml.ast.WithParen;
import ca.teamdman.sfml.ast.WithTag;
import ca.teamdman.sfml.program_builder.ListErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A picker query parsed from SFML's resource-limit grammar entry point. */
public final class SFMItemPickerQuery {
    private final ResourceLimit matcher;

    private SFMItemPickerQuery(ResourceLimit matcher) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public static boolean usesSFMLSyntax(String input) {
        String query = input.strip();
        if (query.isEmpty()) return false;
        if (query.matches(".*[\\*#\"].*")) return true;
        return query.matches(".*\\b(OR|WITH|WITHOUT|TAG|NOT|AND)\\b.*");
    }

    public static ParseResult parse(String input) {
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SFMLParser parser = new SFMLParser(tokens);
        List<String> errors = new ArrayList<>();
        ListErrorListener listener = new ListErrorListener(errors);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);
        SFMLParser.ResourceLimitContext context = parser.resourceLimit();
        if (parser.getCurrentToken().getType() != Token.EOF) {
            errors.add("Unexpected input near '" + parser.getCurrentToken().getText() + "'");
        }
        if (!errors.isEmpty()) return ParseResult.failure(String.join("; ", errors));
        try {
            return ParseResult.success(new SFMItemPickerQuery(new ASTBuilder().visitResourceLimit(context)));
        } catch (RuntimeException | AssertionError exception) {
            String message = exception.getMessage();
            return ParseResult.failure(message == null ? exception.getClass().getSimpleName() : message);
        }
    }

    public boolean matches(SFMItemPickerEntry entry) {
        return matcher.resourceIds().stream().anyMatch(resource ->
                        resource.resourceTypeNamespace.equals(SFM.MOD_ID)
                                && resource.resourceTypeName.equals("item")
                                && resource.matchesResourceLocation(entry.itemId()))
                && matches(matcher.with(), entry.tags());
    }

    public boolean usesTags() {
        return usesTags(matcher.with());
    }

    private static boolean usesTags(WithClause clause) {
        if (clause instanceof With with) return usesTags(with.condition());
        if (clause instanceof WithTag) return true;
        if (clause instanceof WithConjunction both) return usesTags(both.left()) || usesTags(both.right());
        if (clause instanceof WithDisjunction either) return usesTags(either.left()) || usesTags(either.right());
        if (clause instanceof WithNegation negated) return usesTags(negated.inner());
        if (clause instanceof WithParen parenthesized) return usesTags(parenthesized.inner());
        return false;
    }

    private static boolean matches(WithClause clause, List<net.minecraft.resources.ResourceLocation> tags) {
        if (clause instanceof With with) {
            boolean matched = matches(with.condition(), tags);
            return with.mode() == With.WithMode.WITH ? matched : !matched;
        }
        if (clause instanceof WithAlwaysTrue) return true;
        if (clause instanceof WithTag tag) return tags.stream().anyMatch(tag.tagMatcher()::testResourceLocation);
        if (clause instanceof WithConjunction both) {
            return matches(both.left(), tags) && matches(both.right(), tags);
        }
        if (clause instanceof WithDisjunction either) {
            return matches(either.left(), tags) || matches(either.right(), tags);
        }
        if (clause instanceof WithNegation negated) return !matches(negated.inner(), tags);
        if (clause instanceof WithParen parenthesized) return matches(parenthesized.inner(), tags);
        return false;
    }

    public record ParseResult(SFMItemPickerQuery query, String diagnostic) {
        private static ParseResult success(SFMItemPickerQuery query) { return new ParseResult(query, ""); }
        private static ParseResult failure(String diagnostic) { return new ParseResult(null, diagnostic); }
        public boolean valid() { return query != null; }
    }
}
