package ca.teamdman.ai;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"CallToPrintStackTrace", "CodeBlock2Expr", "ExtractMethodRecommender"})
public class ProjectTestMethodProgramLister {
    public static void main(String[] args) throws IOException {
        Path projectPath = Paths.get("src/gametest/java/ca/teamdman/sfm/gametest"); // Replace with your source directory
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs
            ) {
                if (file.toString().endsWith(".java")) {
                    try {
                        CompilationUnit compilationUnit = StaticJavaParser.parse(file);

                        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                            cls.getMethods().forEach(method -> {
                                boolean isTest = method.getAnnotations().stream().anyMatch(annotation -> {
                                    String name = annotation.getName().getIdentifier();
                                    return name.equals("GameTest") || name.equals(
                                            "net.minecraft.gametest.framework.GameTest"); // Adjust as needed
                                });

                                if (isTest) {
                                    System.out.println("File: " + file);
                                    System.out.println("  Class: " + cls.getName());
                                    System.out.println("    Test Method:  " + method.getName());
                                    // now we want to find the program
                                    @SuppressWarnings("OptionalGetWithoutIsPresent") var body = method.getBody().get();
                                    var statements = body.getStatements();
                                    AtomicBoolean foundProgram = new AtomicBoolean(false);
                                    for (var statement : statements) {
                                        statement.ifExpressionStmt(expr -> {
                                            var expression = expr.getExpression();
                                            expression.ifVariableDeclarationExpr(decl -> {
                                                decl.getVariables().forEach(variable -> {
                                                    var initializer = variable.getInitializer();
                                                    if (initializer.isPresent()) {
                                                        Node value = initializer.get();
                                                        while (value instanceof MethodCallExpr methodCallExpr) {
                                                            value = methodCallExpr.getChildNodes().get(0);
                                                        }
                                                        //noinspection StatementWithEmptyBody
                                                        if (value instanceof LiteralStringValueExpr literal) {
                                                            if (literal.getValue().toLowerCase(Locale.ROOT).contains("every")) {
                                                                System.out.println("      Program (variable=" + variable.getName()+"):\n" + literal.getValue().stripTrailing().stripIndent());
                                                                if (foundProgram.getAndSet(true)) {
                                                                    System.out.println("      Multiple programs found in test method " + method.getNameAsString());
                                                                    System.err.println("      Multiple programs found in test method " + method.getNameAsString());
                                                                }
                                                            }
                                                        } else {
//                                                            System.out.println("      Program (variable): (unknown) " + value);
                                                        }
                                                    }
                                                });
                                            });
                                            expression.ifMethodCallExpr(call -> {
                                                var name = call.getName().getIdentifier();
                                                var args = call.getArguments();
                                                if (name.equals("setProgram") && args.size() == 1) {
                                                    // it may be a "asd".stripTrailing().stripIndent() value
                                                    Node arg = args.get(0);
                                                    while (arg instanceof MethodCallExpr methodCallExpr) {
                                                        arg = methodCallExpr.getChildNodes().get(0);
                                                    }
                                                    //noinspection StatementWithEmptyBody
                                                    if (arg instanceof LiteralStringValueExpr literal) {
                                                        System.out.println("      Program:\n" + literal.getValue().stripTrailing().stripIndent());
                                                        if (foundProgram.getAndSet(true)) {
                                                            System.out.println("      Multiple programs found in test method " + method.getNameAsString());
                                                            System.err.println("      Multiple programs found in test method " + method.getNameAsString());
                                                        }
                                                    } else {
//                                                        System.out.println("      Program: (unknown) " + arg);
                                                    }
                                                }
                                            });
                                        });
                                    }
                                    if (!foundProgram.get()) {
                                        System.out.println("      No program found in test method " + method.getNameAsString());
                                        System.err.println("      No program found in test method " + method.getNameAsString());
                                    }
                                }
                            });
                        });

                    } catch (IOException | ParseProblemException e) {
                        System.out.println("Failed to parse file: " + file);
                        System.err.println("Failed to parse file: " + file);
                        e.printStackTrace();
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
