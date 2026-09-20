package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMPathExpressionTests {
    @TempDir
    Path root;

    @Test
    public void canonicalConcreteExamplesRoundTrip() {
        assertCanonical("file:///C:/tmp", "file:///c:/tmp");
        assertCanonical("file://server/share/dir", "file://SERVER/share/dir");
        assertCanonical("registry://minecraft/item/", "registry://minecraft/item/");
        assertCanonical(
                "registry://minecraft/item/minecraft/stick",
                "registry://minecraft/item/minecraft/stick"
        );
        assertCanonical("selection://my%20selection", "selection://my%20selection");
        assertCanonical("selection://review@revision-7", "selection://review@revision-7");
        assertCanonical("example://authority", "example://authority");
        assertCanonical("example://authority/", "example://authority/");
        assertCanonical("example://authority/some%20path", "example://authority/some%20path");
        assertNotEquals(
                SFMPath.parse("example://authority"),
                SFMPath.parse("example://authority/")
        );
        assertEquals(SFMPath.parse("file:///C:/tmp"), SFMPath.parse("file:///C:/tmp/"));
        assertEquals(
                SFMPath.parse("registry://minecraft/item/"),
                SFMPath.parse("registry://minecraft/item")
        );
    }

    @Test
    public void nativePathsBecomeStrictCanonicalFileUris() {
        Path nativePath = root.resolve("folder with spaces").resolve("π.txt").toAbsolutePath().normalize();
        SFMPath path = SFMPath.fromNative(nativePath);
        assertEquals(SFMPath.Kind.FILE, path.kind());
        assertTrue(path.canonical().startsWith("file:///"));
        assertTrue(path.canonical().contains("folder%20with%20spaces"));
        assertTrue(path.canonical().contains("%CF%80.txt"));
        assertEquals(nativePath, path.toNativePath());
        assertEquals(path, SFMPath.parse(path.canonical()));
    }

    @Test
    public void nonAsciiDriveLikeSegmentsRemainOrdinaryEncodedComponents() {
        String canonical = "file:///%C3%84%3A/tmp";
        SFMPath path = SFMPath.parse(canonical);

        assertEquals(List.of("Ä:", "tmp"), path.segments());
        assertEquals(canonical, path.canonical());
        assertEquals(path, SFMPath.parse(path.canonical()));
    }

    @Test
    public void pathExpressionKeepsSelectionDereferenceExplicit() {
        SFMPathExpression expression = SFMPathExpression.parse(
                "difference(union(file:///C:/a,registry://minecraft/item/),"
                        + "members(union(name(primary),id(selection-2))))"
        );
        assertInstanceOf(SFMPathExpression.Difference.class, expression);
        assertEquals(
                "difference(union(file:///C:/a,registry://minecraft/item/),"
                        + "members(union(name(primary),id(selection-2))))",
                expression.canonical()
        );
        assertEquals(expression, SFMPathExpression.parse(expression.canonical()));
    }

    @Test
    public void childrenAndSetOperationsAreTypedNodes() {
        SFMPathExpression expression = SFMPathExpression.parse(
                "intersection(children(file:///C:/repo),union(file:///C:/repo/a,file:///C:/repo/b))"
        );
        SFMPathExpression.Intersection intersection = assertInstanceOf(
                SFMPathExpression.Intersection.class,
                expression
        );
        assertInstanceOf(SFMPathExpression.Children.class, intersection.expressions().get(0));
        assertInstanceOf(SFMPathExpression.Union.class, intersection.expressions().get(1));
    }

    @Test
    public void invalidEncodingDotSegmentsAndPipeAggregatesFailClosed() {
        assertCode("text.invalid-percent-escape", () -> SFMPath.parse("file:///C:/bad%2"));
        assertCode("path.noncanonical-segment", () -> SFMPath.parse("file:///C:/a/../b"));
        assertCode(
                "path-expression.pipe-aggregate-forbidden",
                () -> SFMPathExpression.parse("file:///C:/a|file:///C:/b")
        );
        assertCode("text.invalid-utf8", () -> SFMPath.parse("file:///C:/%FF"));
        assertCode("path.invalid-unicode", () -> SFMPath.parse("file:///C:/\uD800"));
    }

    @Test
    public void emptyAndMalformedExpressionsFailClosed() {
        assertThrows(SFMParseException.class, () -> SFMPathExpression.parse("union()"));
        assertThrows(SFMParseException.class, () -> SFMPathExpression.parse("difference(file:///C:/a)"));
        assertThrows(SFMParseException.class, () -> SFMPathExpression.parse("children(file:///C:/a,file:///C:/b)"));
        assertThrows(SFMParseException.class, () -> SFMPathExpression.parse("union(file:///C:/a,)"));
    }

    @Test
    public void directConstructionCannotMismatchKindAndScheme() {
        assertCode(
                "path.kind-scheme-mismatch",
                () -> new SFMPath(
                        SFMPath.Kind.FILE,
                        "registry",
                        "minecraft",
                        java.util.List.of("item"),
                        java.util.Optional.empty(),
                        true
                )
        );
    }

    @Test
    public void directConstructionNormalizesFieldsUsedByEqualityAndOrdering() {
        SFMPath constructedDrive = new SFMPath(
                SFMPath.Kind.FILE,
                "file",
                "",
                List.of("c:", "tmp"),
                Optional.empty(),
                true
        );
        SFMPath parsedDrive = SFMPath.parse("file:///C:/tmp");
        assertEquals(parsedDrive, constructedDrive);
        assertEquals(0, parsedDrive.compareTo(constructedDrive));

        SFMPath constructedUnc = new SFMPath(
                SFMPath.Kind.FILE,
                "file",
                "SERVER",
                List.of("share", "dir"),
                Optional.empty(),
                false
        );
        assertEquals(SFMPath.parse("file://server/share/dir"), constructedUnc);

        TreeSet<SFMPath> ordered = new TreeSet<>();
        ordered.add(parsedDrive);
        ordered.add(constructedDrive);
        assertEquals(1, ordered.size());
    }

    @Test
    public void nativeConversionRejectsDecodedSeparatorsAndRootChangingComponents() {
        assertCode(
                "path.native-embedded-separator",
                () -> SFMPath.parse("file:///C:/safe/..%5Cescape").toNativePath()
        );
        assertCode(
                "path.native-embedded-separator",
                () -> SFMPath.parse("file:///C:/safe/part%2Fescape").toNativePath()
        );
        assertCode(
                "path.native-embedded-separator",
                () -> SFMPath.parse("file://server%5Cescape/share").toNativePath()
        );
        assertCode(
                "path.native-drive-prefix",
                () -> SFMPath.parse("file:///C:/safe/D%3A/escape").toNativePath()
        );
    }

    private static void assertCanonical(String expected, String input) {
        SFMPath parsed = SFMPath.parse(input);
        assertEquals(expected, parsed.canonical());
        assertEquals(parsed, SFMPath.parse(parsed.canonical()));
    }

    private static void assertCode(String expected, Runnable operation) {
        SFMParseException exception = assertThrows(SFMParseException.class, operation::run);
        assertEquals(expected, exception.code());
    }
}
