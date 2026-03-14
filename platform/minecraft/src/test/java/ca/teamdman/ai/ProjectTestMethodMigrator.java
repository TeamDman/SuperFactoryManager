package ca.teamdman.ai;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
I was stupid and only ran this on 1.19.2 then deleted the old tests and forward propagated.
Instead, should have retained the old tests and re-ran this script for each version to ensure we copied the differences between versions instead of me having to copy them manually.
 */
@SuppressWarnings({"CallToPrintStackTrace", "CodeBlock2Expr"})
public class ProjectTestMethodMigrator {

    private static final Path OUTPUT_DIR = Paths.get("src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated");
    private static final String BASE_TEST_PACKAGE = "ca.teamdman.sfm.gametest.tests.old";
    private static final String MIGRATED_TEST_PKG = "ca.teamdman.sfm.gametest.tests.migrated";

    public static void main(String[] args) throws IOException {
        Path projectPath = Paths.get("src/gametest/java/ca/teamdman/sfm/gametest");
        JavaParser javaParser = new JavaParser(
                new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
        );

        // Create output directory if it doesn't exist
        Files.createDirectories(OUTPUT_DIR);

        List<TestMethodInfo> testMethods = new ArrayList<>();

        // First pass: collect all test methods
        Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs
            ) {
                if (file.toString().endsWith(".java")) {
                    try {
                        CompilationUnit compilationUnit = LexicalPreservingPrinter.setup(javaParser
                                                                                                 .parse(file)
                                                                                                 .getResult()
                                                                                                 .orElseThrow());
                        String packageName = compilationUnit.getPackageDeclaration()
                                .map(NodeWithName::getNameAsString)
                                .orElse("");

                        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                            cls.getMethods().forEach(method -> {
                                Optional<AnnotationExpr> gameTestAnnotation = method.getAnnotations().stream()
                                        .filter(annotation -> {
                                            String name = annotation.getName().getIdentifier();
                                            return name.equals("GameTest") ||
                                                   name.equals("net.minecraft.gametest.framework.GameTest");
                                        })
                                        .findFirst();

                                if (gameTestAnnotation.isPresent()) {
                                    TestMethodInfo info = new TestMethodInfo();
                                    info.originalFile = file;
                                    info.originalPackage = packageName;
                                    info.originalClass = cls.getNameAsString();
                                    info.methodName = method.getNameAsString();
                                    info.className = snakeCaseToPascalCase(method.getNameAsString()) + "GameTest";
                                    info.template = extractTemplateValue(gameTestAnnotation.get());
                                    info.timeoutTicks = extractTimeoutTicks(gameTestAnnotation.get());
                                    info.batchName = extractBatchName(gameTestAnnotation.get());
                                    info.methodBody = method.getBody().orElseThrow();
                                    info.compilationUnit = compilationUnit; // Store the whole compilation unit

                                    testMethods.add(info);
                                    System.out.println("Found test: " + info.methodName + " -> " + info.className);
                                }
                            });
                        });

                    } catch (IOException | ParseProblemException e) {
                        System.err.println("Failed to parse file: " + file);
                        e.printStackTrace();
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Second pass: generate new test classes
        for (TestMethodInfo info : testMethods) {
            generateTestClass(info);
        }

        System.out.println("\nGenerated " + testMethods.size() + " test classes in " + OUTPUT_DIR);
    }

    private static String extractTemplateValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            return cleanStringLiteral(singleMember.getMemberValue().toString());
        } else if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            return normalAnnotation.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("template"))
                    .map(MemberValuePair::getValue)
                    .map(value -> cleanStringLiteral(value.toString()))
                    .findFirst()
                    .orElse("empty");
        }
        return "empty";
    }

    private static String extractBatchName(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            return normalAnnotation.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("batch"))
                    .map(MemberValuePair::getValue)
                    .map(value -> cleanStringLiteral(value.toString()))
                    .findFirst()
                    .orElse("defaultBatch");
        }
        return "defaultBatch";
    }

    private static Optional<String> extractTimeoutTicks(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            return normalAnnotation.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("timeoutTicks"))
                    .map(MemberValuePair::getValue)
                    .map(Node::toString)
                    .findFirst();
        }
        return Optional.empty();
    }

    private static String cleanStringLiteral(String literal) {
        if (literal.startsWith("\"") && literal.endsWith("\"")) {
            return literal.substring(1, literal.length() - 1);
        }
        return literal;
    }

    private static String snakeCaseToPascalCase(String snakeCase) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static void generateTestClass(TestMethodInfo info) throws IOException {
        // compute the new package name and on-disk path
        String origPkg = info.originalPackage;
        String suffix = "";
        if (origPkg.startsWith(BASE_TEST_PACKAGE)) {
            suffix = origPkg.substring(BASE_TEST_PACKAGE.length()); // e.g. ".compat.ae2"
        }
        String newPkg = MIGRATED_TEST_PKG + suffix;
        // turn ".compat.ae2" → "compat/ae2"
        Path subDir = suffix.isEmpty()
                    ? Paths.get("")
                    : Paths.get(suffix.substring(1).replace('.', File.separatorChar));
        Path targetDir = OUTPUT_DIR.resolve(subDir);
        Files.createDirectories(targetDir);

        String className = info.className;
        Path outputFile = targetDir.resolve(className + ".java");

        StringBuilder sb = new StringBuilder();
        // 1) new package line
        sb.append("package ").append(newPkg).append(";\n\n");

        // 2) copy imports
        info.compilationUnit.getImports()
            .forEach(i -> sb.append(i.toString()).append("\n"));
        sb.append("\n");

        // 3) your extra imports
        sb.append("import ca.teamdman.sfm.gametest.SFMGameTest;\n")
          .append("import ca.teamdman.sfm.gametest.SFMGameTestDefinition;\n")
          .append("import ca.teamdman.sfm.gametest.SFMGameTestHelper;\n")
          .append("\nimport static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.*;\n\n");

        // 4) class javadoc & annotations
        sb.append("/** Migrated from ")
          .append(info.originalClass).append(".")
          .append(info.methodName).append(" */\n")
          .append("@SuppressWarnings({\n")
          .append("    \"RedundantSuppression\",\n")
          .append("    \"DataFlowIssue\",\n")
          .append("    \"OptionalGetWithoutIsPresent\",\n")
          .append("    \"DuplicatedCode\",\n")
          .append("    \"ArraysAsListWithZeroOrOneArgument\"\n")
          .append("})\n")
          .append("@SFMGameTest\n")
          .append("public class ").append(className)
          .append(" extends SFMGameTestDefinition {\n\n");

        // 5) override template(), batchName(), maxTicks()…
        sb.append("    @Override\n")
          .append("    public String template() {\n")
          .append("        return \"").append(info.template).append("\";\n")
          .append("    }\n\n");
        if (!info.batchName.equals("defaultBatch")) {
            sb.append("    @Override\n")
              .append("    public String batchName() {\n")
              .append("        return \"").append(info.batchName).append("\";\n")
              .append("    }\n\n");
        }
        info.timeoutTicks.ifPresent(t -> sb.append("    @Override\n")
            .append("    public int maxTicks() {\n")
            .append("        return ").append(t).append(";\n")
            .append("    }\n\n"));

        // 6) testMethod body
        sb.append("    @Override\n")
          .append("    public void testMethod(SFMGameTestHelper helper) {")
          .append(convertMethodBody(info.methodBody))
          .append("}\n}\n");

        Files.writeString(outputFile, sb.toString());
        System.out.println("Generated: " + outputFile);
    }

    private static String convertMethodBody(BlockStmt originalBody) {
        // Remove the outer braces and adjust indentation
        String body = LexicalPreservingPrinter.print(originalBody);//.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }

        // Convert GameTestHelper to SFMGameTestHelper
        body = body.replace("GameTestHelper helper", "SFMGameTestHelper helper");

//        // Add helper.succeed() if not present and no other succeed call
//        if (!body.contains("succeed")) {
//            body += "\n        helper.succeed();";
//        }

//        // Fix indentation - add 8 spaces to each line
//        String[] lines = body.split("\n");
//        StringBuilder result = new StringBuilder();
//        for (String line : lines) {
//            if (!line.trim().isEmpty()) {
//                result.append("        ").append(line.replaceFirst("^\\s*", "")).append("\n");
//            } else {
//                result.append("\n");
//            }
//        }

        return body;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class TestMethodInfo {
        Path originalFile;
        String originalPackage;
        String originalClass;
        String methodName;
        String className;
        String template;
        String batchName;
        BlockStmt methodBody;
        Optional<String> timeoutTicks;
        CompilationUnit compilationUnit; // Store the whole compilation unit for imports
    }
}