package ca.teamdman.sfml.test;

import ca.teamdman.sfml.ast.SFMLLiteralGlob;
import ca.teamdman.sfml.ast.TagMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMLLiteralGlobTests {
    @Test
    void dottedSuffixIsLiteralRatherThanRegexAnyCharacter() {
        Pattern matcher = Pattern.compile(SFMLLiteralGlob.toRegex("*.java"));

        assertTrue(matcher.asMatchPredicate().test("Example.java"));
        assertFalse(matcher.asMatchPredicate().test("Examplexjava"));
    }

    @Test
    void legalResourceLocationPunctuationRemainsLiteral() {
        Pattern matcher = Pattern.compile(SFMLLiteralGlob.toRegex("mod.id/path-name_*"));

        assertTrue(matcher.asMatchPredicate().test("mod.id/path-name_value"));
        assertFalse(matcher.asMatchPredicate().test("modXid/path-name_value"));
        assertFalse(matcher.asMatchPredicate().test("mod.idXpath-name_value"));
    }

    @Test
    void starAndCompleteDoubleStarKeepTheirWildcardForms() {
        assertEquals(".*", SFMLLiteralGlob.toRegex("*"));
        assertEquals(".*.*", SFMLLiteralGlob.toRegex("**"));
        assertTrue(Pattern.compile(SFMLLiteralGlob.toRegex("prefix*suffix"))
                .asMatchPredicate().test("prefix-anything-suffix"));
    }

    @Test
    void tagMatcherConsumesLiteralSafeComponentsAndKeepsDeepMatch() {
        TagMatcher dotted = TagMatcher.fromNamespaceAndPath("forge", List.of(
                "source",
                SFMLLiteralGlob.toRegex("*.java")
        ));
        assertTrue(dotted.test("forge:source/Example.java"));
        assertFalse(dotted.test("forge:source/Examplexjava"));

        TagMatcher deep = TagMatcher.fromNamespaceAndPath("forge", List.of(
                "ingots",
                SFMLLiteralGlob.toRegex("**")
        ));
        assertTrue(deep.test("forge:ingots/iron/dust"));
        assertFalse(deep.test("forge:nuggets/iron/dust"));
    }
}
