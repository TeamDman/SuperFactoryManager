package ca.teamdman.ai;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@SuppressWarnings({"CallToPrintStackTrace", "CodeBlock2Expr"})
public class ProjectTestMethodLister {
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
                                    System.out.println("    Test Method: " + method.getName());
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
    }
}
