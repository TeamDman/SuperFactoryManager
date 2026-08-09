package ca.teamdman.ai;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("CallToPrintStackTrace")
public class ProjectEverythingLister {
    public static void main(String[] args) throws IOException {
        Path projectPath = Paths.get("src/"); // Replace with your source directory
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        AtomicInteger methodIndex = new AtomicInteger();
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
                            System.out.println("File: " + file);
                            System.out.println("  Class: " + cls.getName());
                            cls.getMethods().forEach(method -> {
                                String annotations = method
                                        .getAnnotations()
                                        .stream()
                                        .map(annotation -> "@" + annotation.getName().getIdentifier())
                                        .collect(Collectors.joining(", "));
                                if (!annotations.isEmpty()) {
                                    System.out.print("        ");
                                    System.out.println(annotations);
                                }
                                String signature = method.getDeclarationAsString();
                                System.out.printf("%-4d    ", methodIndex.incrementAndGet());
                                System.out.println(signature);
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
