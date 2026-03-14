package ca.teamdman.sfm;

import ca.teamdman.sfml.ast.TagMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TagMatcherTests {

    @Test
    public void testTagMatcher() {
        TagMatcher matcher = createTagMatcher("namespace:path");
        assertTrue(matcher.test("namespace:path"));
        assertFalse(matcher.test("namespace:other"));
        assertFalse(matcher.test("whatever:path"));
        assertFalse(matcher.test("something:here"));
        assertFalse(matcher.test(":"));
        assertFalse(matcher.test(":path"));
        assertFalse(matcher.test("namespace:"));
    }

    @Test
    public void testTagMatcherPattern() {
        TagMatcher matcher = createTagMatcher("forge:*");
        assertTrue(matcher.test("forge:path"));
        assertTrue(matcher.test("forge:idk"));
        assertTrue(matcher.test("forge:something"));
        assertFalse(matcher.test("forge:who/knows"));
        assertFalse(matcher.test("minecraft:beef"));
    }

    @Test
    public void testTagMatcherDeepPattern() {
        TagMatcher matcher = createTagMatcher("forge:**");
        assertTrue(matcher.test("forge:path"));
        assertTrue(matcher.test("forge:idk"));
        assertTrue(matcher.test("forge:something"));
        assertTrue(matcher.test("forge:who/knows"));
        assertTrue(matcher.test("forge:who/knows/what"));
        assertFalse(matcher.test("namespace:path"));
        assertFalse(matcher.test("namespace:other"));
        assertFalse(matcher.test("whatever:path"));
        assertFalse(matcher.test("whatever:path/be/deep"));
        assertFalse(matcher.test("something:here"));
        assertFalse(matcher.test(":"));
        assertFalse(matcher.test(":path"));
        assertFalse(matcher.test("namespace:"));
    }

    @Test
    public void testTagMatcherExactPath() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/axe/red_matter");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe"));
        assertFalse(matcher.test("minecraft:mineable/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe/other_matter"));
        assertFalse(matcher.test("minecraft:other_namespace/axe/red_matter"));
    }

    @Test
    public void testTagMatcherPartialPathWithWildcard() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/*/*");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("minecraft:mineable/beef/yummy"));
        assertFalse(matcher.test("minecraft:mineable/axe"));
        assertFalse(matcher.test("minecraft:mineable/red_matter"));
        assertFalse(matcher.test("minecraft:other_namespace/axe/red_matter"));
    }

    @Test
    public void testTagMatcherDeepWildcard() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/**");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("minecraft:mineable/axe"));
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter/extra"));
        assertFalse(matcher.test("minecraft:mineable"));
        assertFalse(matcher.test("minecraft:other_namespace/axe/red_matter"));
        assertFalse(matcher.test("minecraft:other_namespace"));
        assertFalse(matcher.test("minecraft:other_namespace/beef"));
    }

    @Test
    public void testTagMatcherPartialAndDeepWildcard() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/*/**");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter/extra"));
        assertTrue(matcher.test("minecraft:mineable/axe/other"));
        assertFalse(matcher.test("minecraft:mineable"));
        assertFalse(matcher.test("minecraft:mineable/axe"));
        assertFalse(matcher.test("minecraft:other_namespace/axe/red_matter"));
    }

    @Test
    public void testTagMatcherSpecificPath() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/axe/**");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("minecraft:mineable/axe/extra/more"));
        assertFalse(matcher.test("minecraft:mineable"));
        assertFalse(matcher.test("minecraft:beef/axe/extra/mode"));
        assertFalse(matcher.test("minecraft:mineable/red_matter"));
        assertFalse(matcher.test("minecraft:other_namespace/axe/red_matter"));
    }

    @Test
    public void testTagMatcherWildcardNamespace() {
        TagMatcher matcher = TagMatcher.fromPath(List.of("mineable", "axe", "red_matter"));
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("other_namespace:mineable/axe/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe/other_matter"));
        assertFalse(matcher.test("minecraft:bruh/axe/other_matter"));
        assertFalse(matcher.test("minecraft:beef"));
    }



    @Test
    public void testTagMatcherMixedWildcardAndSpecific() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable/*/red_matter");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe/blue_matter"));
        assertFalse(matcher.test("minecraft:mineable/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe/red_matter/extra"));
    }
    @Test
    public void testTagMatcherMultipleWildcards() {
        TagMatcher matcher = createTagMatcher("minecraft:*/axe/*");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("minecraft:other/axe/blue_matter"));
        assertFalse(matcher.test("minecraft:axe/red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe"));
    }
    @Test
    public void testTagMatcherMultipleSlashes() {
        TagMatcher matcher = createTagMatcher("minecraft:mineable///red_matter");
        assertFalse(matcher.test("minecraft:mineable/red_matter"));
        assertFalse(matcher.test("minecraft:mineable//red_matter"));
        assertFalse(matcher.test("minecraft:mineable/axe/red_matter"));
    }
    @Test
    public void testTagMatcherWildcardOnly() {
        TagMatcher matcher = createTagMatcher("*:*/**");
        assertTrue(matcher.test("minecraft:mineable/axe/red_matter"));
        assertTrue(matcher.test("anything:any/path/structure"));
        assertFalse(matcher.test(":/")); // this is probably not a valid tag lol, we can adjust if needed
    }


    private static TagMatcher createTagMatcher(String string) {
        string = string.replaceAll("\\*",".*");
        String[] chunks = string.split(":");
        if (chunks.length != 2) {
            throw new IllegalArgumentException("Invalid tag matcher string: " + string);
        }
        String namespace = chunks[0];
        String path = chunks[1];
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be empty, the grammar prevents it");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty, the grammar prevents it");
        }
        return TagMatcher.fromNamespaceAndPath(namespace, List.of(path.split("/")));
    }
}
