package ca.teamdman.ai;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A utility that:
 * 1. Reads fully qualified class or package references from the clipboard.
 * 2. Uses JavaParser's symbol solver to find the .java source files (if available).
 * 3. Prints out those files (in code fences) plus any directly imported classes (also in code fences).
 * 4. Places the result back on the system clipboard.
 * <p>
 * If classes are from libraries / jars, but we've extracted their sources
 * into build/tmp/expandedArchives, we should be able to find them.
 * Otherwise, we show a loud error message.
 */
public class FileImportWalker {

    // Adjust if your project sources live elsewhere
    private static final File GENERATED_SOURCE_ROOT = new File("build/generated-src/antlr/main/");
    private static final File SOURCE_ROOT = new File("src/");

    // Add the path where Gradle expands source jars
    private static final File EXPANDED_ARCHIVES_ROOT = new File("build/tmp/expandedArchives");

    /**
     * Represents a reference to be processed, with its import depth.
     */
    private static class Reference {
        String fqn;
        int depth;
        boolean isPackage;

        public Reference(String fqn, int depth, boolean isPackage) {
            this.fqn = fqn;
            this.depth = depth;
            this.isPackage = isPackage;
        }

        @Override
        public String toString() {
            return "Reference{" +
                   "fqn='" + fqn + '\'' +
                   ", depth=" + depth +
                   ", isPackage=" + isPackage +
                   '}';
        }
    }

    private static List<Reference> getReferencesFromClipboard() {
        String clipboardContent = getClipboardContents();
        System.out.println("[INFO] Found clipboard content:\n" + clipboardContent);

        List<Reference> references = new ArrayList<>();
        String[] lines = clipboardContent.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int depth = 0;
            String reference = line;

            // Check if line starts with a number and a space (depth indicator)
            if (line.matches("^\\d+\\s+.*")) {
                String[] parts = line.split("\\s+", 2);
                depth = Integer.parseInt(parts[0]);
                reference = parts[1].trim();
            }

            // Normalize reference
            if (reference.contains("/")) {
                reference = reference.replaceAll("/", ".");
                System.out.println("[INFO] Replaced '/' with '.', yielding: " + reference);
            }
            if (reference.contains(":")) {
                reference = reference.split(":")[0];
                System.out.println("[INFO] Removed : and everything after, yielding: " + reference);
            }
            if (reference.contains("#")) {
                reference = reference.split("#")[0];
                System.out.println("[INFO] Removed # and everything after, yielding: " + reference);
            }
            if (reference.endsWith(".java")) {
                reference = reference.substring(0, reference.length() - 5);
                System.out.println("[INFO] Removed .java extension, yielding: " + reference);
            }

            // Determine if this is a package or class reference
            boolean isPackage = !reference.matches(".*[A-Z].*$");

            // For a single class reference with unspecified depth, default to 1
            if (lines.length == 1 && !isPackage && depth == 0) {
                depth = 1;
            }

            references.add(new Reference(reference, depth, isPackage));
        }

        return references;
    }

    public static void main(String[] args) {
        System.out.println("=== FileImportWalker starting ===");

        // Grab references from clipboard
        List<Reference> references = getReferencesFromClipboard();

        // Create a combined type solver
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();

        // Reflection = standard JDK
        combinedTypeSolver.add(new ReflectionTypeSolver());
        // Local source
        addModuleTypeSolvers(combinedTypeSolver, SOURCE_ROOT);
        combinedTypeSolver.add(new JavaParserTypeSolver(GENERATED_SOURCE_ROOT));
        // For each subdirectory of expandedArchives, add a JavaParserTypeSolver
        addExpandedArchiveTypeSolvers(combinedTypeSolver, EXPANDED_ARCHIVES_ROOT);
        // ClassLoader solver as fallback (so we don't crash on unsolved external references)
        combinedTypeSolver.add(new ClassLoaderTypeSolver(FileImportWalker.class.getClassLoader()));

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("===\n");

        for (Reference ref : references) {
            System.out.println("[INFO] Processing reference: " + ref);

            if (ref.isPackage) {
                processPackage(ref, combinedTypeSolver, resultBuilder);
            } else {
                processClass(ref, combinedTypeSolver, resultBuilder);
            }
        }

        resultBuilder.append("===\n");

        // Place the final chunk on the clipboard
        setClipboardContents(resultBuilder.toString());
        System.out.println("[INFO] Final concatenated content set to clipboard.");

        System.out.println("=== FileImportWalker finished ===");
    }

    /**
     * Process a package reference by finding all classes within the package
     * and processing each one.
     */
    private static void processPackage(
            Reference packageRef,
            CombinedTypeSolver combinedTypeSolver,
            StringBuilder resultBuilder
    ) {
        System.out.println("[INFO] Processing package: " + packageRef.fqn);

        // Convert package to path format for file searches
        String packagePath = packageRef.fqn.replace('.', '/');
        List<File> sourceRoots = new ArrayList<>();

        // Add main source directories
        for (File subdir : Objects.requireNonNull(SOURCE_ROOT.listFiles(File::isDirectory))) {
            File[] javaSubDirs = subdir.listFiles(x -> x.isDirectory() && x.getName().equals("java"));
            if (javaSubDirs != null && javaSubDirs.length > 0) {
                sourceRoots.add(javaSubDirs[0]);
            }
        }

        // Add expanded archives
        if (EXPANDED_ARCHIVES_ROOT.exists() && EXPANDED_ARCHIVES_ROOT.isDirectory()) {
            File[] expandedDirs = EXPANDED_ARCHIVES_ROOT.listFiles(File::isDirectory);
            if (expandedDirs != null) {
                sourceRoots.addAll(Arrays.asList(expandedDirs));
            }
        }

        // Find all .java files in the specified package across all source roots
        List<File> javaFiles = new ArrayList<>();
        for (File sourceRoot : sourceRoots) {
            File packageDir = new File(sourceRoot, packagePath);
            if (packageDir.exists() && packageDir.isDirectory()) {
                File[] files = packageDir.listFiles((dir, name) -> name.endsWith(".java"));
                if (files != null) {
                    javaFiles.addAll(Arrays.asList(files));
                }
            }
        }

        if (javaFiles.isEmpty()) {
            resultBuilder.append("Package: `").append(packageRef.fqn).append("` - No classes found\n\n");
            return;
        }

        resultBuilder.append("Package: `").append(packageRef.fqn).append("` - ")
                .append(javaFiles.size()).append(" classes found\n\n");

        // Process each class in the package
        for (File file : javaFiles) {
            String className = file.getName().replace(".java", "");
            String fullClassName = packageRef.fqn + "." + className;

            Reference classRef = new Reference(fullClassName, packageRef.depth, false);
            processClass(classRef, combinedTypeSolver, resultBuilder);
        }
    }

    /**
     * Process a class reference by finding the source file and processing its imports
     * based on the specified depth.
     */
    private static void processClass(
            Reference classRef,
            CombinedTypeSolver combinedTypeSolver,
            StringBuilder resultBuilder
    ) {
        try {
            SymbolReference<ResolvedReferenceTypeDeclaration> symbolRef =
                    combinedTypeSolver.tryToSolveType(classRef.fqn);

            if (!symbolRef.isSolved()) {
                resultBuilder.append("Class: `").append(classRef.fqn)
                        .append("` - Could not resolve type\n\n");
                System.err.println("[ERROR] Could not resolve type: " + classRef.fqn);
                System.err.println("        Perhaps it's not in local src or expandedArchives.");
                return;
            }

            ResolvedReferenceTypeDeclaration resolvedType = symbolRef.getCorrespondingDeclaration();

            // Convert to AST, then go up to the top-level CompilationUnit
            //noinspection unchecked
            resolvedType.toAst()
                    .flatMap(typeDeclaration -> typeDeclaration.findAncestor(CompilationUnit.class))
                    .ifPresentOrElse(cu -> {
                        try {
                            processCompilationUnit(classRef, cu, combinedTypeSolver, resultBuilder);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, () -> {
                        // If no CU found, that means it's in a jar w/o source or some unresolved location
//                        resultBuilder.append("Class: `").append(classRef.fqn)
//                                .append("` - No CompilationUnit found\n\n");
                        System.err.println("[ERROR] No CompilationUnit for " + classRef.fqn
                                           + ", possibly no .java in expanded archives.");
                    });
        } catch (Exception e) {
            resultBuilder.append("Class: `").append(classRef.fqn)
                    .append("` - Error: ").append(e.getMessage()).append("\n\n");
            System.err.println("[ERROR] Exception processing " + classRef.fqn + ": " + e.getMessage());
        }
    }

    /**
     * Scans src\*\java for subdirectories (each containing a module's source),
     */
    @SuppressWarnings("SameParameterValue")
    private static void addModuleTypeSolvers(
            CombinedTypeSolver combinedSolver,
            File srcDir
    ) {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            System.out.println("[WARN] " + srcDir + " is not a directory. Not adding module solvers.");
            return;
        }

        File[] subDirs = srcDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            System.out.println("[WARN] No subdirectories found under " + srcDir + ".");
            return;
        }

        for (File subdir : subDirs) {
            File[] subSubDirs = subdir.listFiles(x -> x.isDirectory() && x.getName().equals("java"));
            if (subSubDirs == null || subSubDirs.length == 0) {
                System.out.println("[WARN] No src/module/main subdirectory found under " + subdir + ".");
                continue;
            }
            for (File subSubDir : subSubDirs) {
                System.out.println("[INFO] Adding JavaParserTypeSolver for src/module/main dir: "
                                   + subSubDir.getAbsolutePath());
                combinedSolver.add(new JavaParserTypeSolver(subSubDir));
            }
        }
    }

    /**
     * Scans build/tmp/expandedArchives for subdirectories (each containing
     * extracted source from a jar), and adds them as a JavaParserTypeSolver.
     */
    @SuppressWarnings("SameParameterValue")
    private static void addExpandedArchiveTypeSolvers(
            CombinedTypeSolver combinedSolver,
            File rootDir
    ) {
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            System.out.println("[WARN] " + rootDir + " is not a directory. Not adding expanded archives solvers.");
            return;
        }

        File[] subDirs = rootDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            System.out.println("[WARN] No subdirectories found under " + rootDir + ".");
            return;
        }

        for (File subdir : subDirs) {
            System.out.println("[INFO] Adding JavaParserTypeSolver for expanded source dir: "
                               + subdir.getAbsolutePath());
            combinedSolver.add(new JavaParserTypeSolver(subdir));
        }
    }

    /**
     * Processes the main CompilationUnit (the top-level file that holds your reference class),
     * gathers its imports, fetches each imported file if available, and concatenates all the contents
     * into a single output.
     */
    private static void processCompilationUnit(
            Reference reference,
            @NotNull CompilationUnit cu,
            CombinedTypeSolver combinedTypeSolver,
            StringBuilder resultBuilder
    ) throws IOException {
        // Mark the start
        resultBuilder.append("Class: `").append(reference.fqn).append("`\n")
                .append("```java\n")
                .append(readRawFileContents(cu))
                .append("\n```\n\n");

        // If depth is 0, don't process imports
        if (reference.depth <= 0) {
            return;
        }

        // For each import, see if we can find its local .java
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic()) {
                System.err.println("[WARN] Skipping static import: " + imp);
                continue;
            }
            if (imp.getNameAsString().endsWith(".*")) {
                System.err.println("[WARN] Skipping wildcard import: " + imp);
                continue;
            }

            String importFqn = imp.getNameAsString();
            System.out.println("[INFO] Attempting to resolve import: " + importFqn);

            try {
                // Process the import with depth-1
                Reference importRef = new Reference(importFqn, reference.depth - 1, false);
                processClass(importRef, combinedTypeSolver, resultBuilder);
            } catch (UnsolvedSymbolException use) {
                // That means something is definitely missing
                resultBuilder.append("Import: `").append(importFqn)
                        .append("` - UnsolvedSymbolException: ").append(use.getMessage()).append("\n\n");
                System.err.println("[ERROR] UnsolvedSymbol for " + importFqn
                                   + " => " + use.getMessage());
            }
        }
    }

    /**
     * Read the raw file contents that back a CompilationUnit, preserving
     * the original formatting, comments, etc.
     * <p>
     * If no storage path is found, we fall back to cu.toString().
     */
    private static String readRawFileContents(CompilationUnit cu) {
        return cu.getStorage()
                .map(storage -> {
                    File file = storage.getPath().toFile();
                    System.out.println("[DEBUG] Reading from file: " + file.getAbsolutePath());
                    try {
                        return Files.readString(file.toPath());
                    } catch (IOException e) {
                        System.err.println("[ERROR] Could not read from file. Fallback to cu.toString().");
                        return cu.toString();
                    }
                })
                .orElseGet(() -> {
                    System.err.println("[ERROR] No storage found for CU. Using cu.toString().");
                    return cu.toString();
                });
    }

    /**
     * Grab a string from the system clipboard.
     */
    private static String getClipboardContents() {
        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            var contents = clipboard.getContents(null);
            boolean hasText = (contents != null) &&
                              contents.isDataFlavorSupported(DataFlavor.stringFlavor);
            if (hasText) {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            } else {
                throw new RuntimeException("[ERROR] Clipboard does not contain a string.");
            }
        } catch (UnsupportedFlavorException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Put a string onto the system clipboard.
     */
    private static void setClipboardContents(String text) {
        var selection = new StringSelection(text);
        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }
}
