package ca.teamdman.sfm.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalizationEntryArgumentValidationTests {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path MAIN_SOURCE_ROOT = PROJECT_ROOT.resolve("src/main/java");
    private static final Set<String> ALLOWED_PRIMITIVE_TYPES = Set.of(
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double",
            "boolean"
    );

    @Test
    public void localization_entry_get_component_arguments_match_modern_translatable_contents_requirements() throws IOException {
        JavaParser parser = new JavaParser(
                new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                        .setSymbolResolver(new JavaSymbolSolver(createTypeSolver()))
        );

        List<String> invalidUsages = new ArrayList<>();
        int inspectedCallCount = 0;

        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            List<Path> javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            for (Path javaFile : javaFiles) {
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                CompilationUnit compilationUnit = parseResult
                        .getResult()
                        .orElseThrow(() -> new AssertionError("Failed to parse " + javaFile + ": " + parseResult.getProblems()));

                for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
                    if (!isTargetMethodCall(methodCall)) {
                        continue;
                    }

                    inspectedCallCount++;
                    for (Expression argument : methodCall.getArguments()) {
                        ArgumentValidationResult result = validateArgument(argument);
                        if (!result.isAllowed()) {
                            invalidUsages.add(formatFailure(javaFile, methodCall, argument, result));
                        }
                    }
                }
            }
        }

        assertTrue(inspectedCallCount > 0, "Sanity check failed: no LocalizationEntry#getComponent(Object... args) calls were inspected.");
        assertTrue(
            invalidUsages.isEmpty(),
                "Invalid LocalizationEntry#getComponent(Object... args) usage(s) found:\n"
                + String.join("\n", invalidUsages)
        );
    }

    private static CombinedTypeSolver createTypeSolver() {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new ClassLoaderTypeSolver(LocalizationEntryArgumentValidationTests.class.getClassLoader()));
        addSourceRootIfPresent(typeSolver, MAIN_SOURCE_ROOT);
        addSourceRootIfPresent(typeSolver, PROJECT_ROOT.resolve("build/generated-src/antlr/main"));
        return typeSolver;
    }

    private static Path findProjectRoot() {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();

        for (Path candidate = workingDirectory; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }

            Path nestedMinecraftProjectRoot = candidate.resolve("platform/minecraft");
            if (Files.isDirectory(nestedMinecraftProjectRoot.resolve("src/main/java"))) {
                return nestedMinecraftProjectRoot;
            }
        }

        throw new IllegalStateException("Could not locate project root containing src/main/java from " + workingDirectory);
    }

    private static void addSourceRootIfPresent(
            CombinedTypeSolver typeSolver,
            Path sourceRoot
    ) {
        if (Files.isDirectory(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }
    }

    private static boolean isTargetMethodCall(MethodCallExpr methodCall) {
        return methodCall.getNameAsString().equals("getComponent") && !methodCall.getArguments().isEmpty();
    }

    private static ArgumentValidationResult validateArgument(Expression argument) {
        try {
            ResolvedType resolvedType = argument.calculateResolvedType();
            String typeName = describeType(resolvedType);
            if (isAllowedType(resolvedType, typeName)) {
                return ArgumentValidationResult.allowed(typeName);
            }
            return ArgumentValidationResult.invalid(typeName, "expected Component, Number, Boolean, or String");
        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
            return ArgumentValidationResult.invalid("<unresolved>", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String describeType(ResolvedType resolvedType) {
        if (resolvedType.isPrimitive()) {
            return resolvedType.describe();
        }
        if (resolvedType.isReferenceType()) {
            return resolvedType.asReferenceType().getQualifiedName();
        }
        return resolvedType.describe();
    }

    private static boolean isAllowedType(
            ResolvedType resolvedType,
            String typeName
    ) {
        if (resolvedType.isPrimitive()) {
            return ALLOWED_PRIMITIVE_TYPES.contains(typeName);
        }
        if (!resolvedType.isReferenceType()) {
            return false;
        }
        return typeName.equals(String.class.getCanonicalName())
               || typeName.equals(Boolean.class.getCanonicalName())
               || isAssignableTo(typeName, Number.class)
               || isAssignableTo(typeName, Component.class);
    }

    private static boolean isAssignableTo(
            String typeName,
            Class<?> expectedSupertype
    ) {
        try {
            Class<?> candidate = Class.forName(
                    typeName,
                    false,
                    LocalizationEntryArgumentValidationTests.class.getClassLoader()
            );
            return expectedSupertype.isAssignableFrom(candidate);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String formatFailure(
            Path javaFile,
            MethodCallExpr methodCall,
            Expression argument,
            ArgumentValidationResult result
    ) {
        int line = methodCall.getRange().map(range -> range.begin.line).orElse(-1);
        return relativeToProjectRoot(javaFile)
               + ":"
               + line
               + " -> "
               + argument
               + " resolved to "
               + result.typeName()
               + " ("
               + result.reason()
               + ") in "
               + methodCall;
    }

    private static String relativeToProjectRoot(Path path) {
        return PROJECT_ROOT.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private record ArgumentValidationResult(
            boolean isAllowed,
            String typeName,
            String reason
    ) {
        private static ArgumentValidationResult allowed(String typeName) {
            return new ArgumentValidationResult(true, typeName, "allowed");
        }

        private static ArgumentValidationResult invalid(
                String typeName,
                String reason
        ) {
            return new ArgumentValidationResult(false, typeName, reason);
        }
    }
}