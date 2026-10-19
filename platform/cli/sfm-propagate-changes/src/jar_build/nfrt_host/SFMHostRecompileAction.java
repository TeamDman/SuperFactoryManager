package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.neoform.runtime.actions.RecompileSourcesAction;
import net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionNodeAction;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Same pinned in-process compiler, but no ZipFS output replacement under a
 * protected Windows directory. Fresh class files are streamed into a new JAR.
 */
final class SFMHostRecompileAction implements ExecutionNodeAction {
    private final RecompileSourcesActionWithJDK original;
    private final Path workspace;
    private final String boundConfiguration;
    private final List<ClasspathItem> boundClasspath;
    private final List<ClasspathItem> boundSourcepath;

    SFMHostRecompileAction(RecompileSourcesActionWithJDK original, Path workspace) throws IOException {
        if (original == null || original.getClass() != RecompileSourcesActionWithJDK.class)
            throw new IOException("Unreviewed in-process compiler action");
        this.original = original;
        this.workspace = workspace.toAbsolutePath().normalize();
        boundConfiguration = configuration(original);
        boundClasspath = List.copyOf(original.getClasspath().getEffectiveClasspath());
        boundSourcepath = List.copyOf(original.getSourcepath().getEffectiveClasspath());
    }

    RecompileSourcesActionWithJDK original() { return original; }
    String configuration() throws IOException { unchanged(); return boundConfiguration; }

    static List<String> options(RecompileSourcesActionWithJDK action) throws IOException {
        try {
            var getter = RecompileSourcesActionWithJDK.class.getDeclaredMethod("getCompilerOptions");
            getter.setAccessible(true);
            @SuppressWarnings("unchecked") var values = (List<String>) getter.invoke(action);
            return List.copyOf(values);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Pinned in-process compiler options are unavailable", failure);
        }
    }

    static String configuration(RecompileSourcesActionWithJDK action) throws IOException {
        var result = new JsonObject();
        result.addProperty("class", action.getClass().getName());
        result.addProperty("target_java_version", action.getTargetJavaVersion());
        var arguments = new JsonArray(); options(action).forEach(arguments::add);
        result.add("compiler_options", arguments);
        return result.toString();
    }

    private void unchanged() throws IOException {
        if (!boundConfiguration.equals(configuration(original))
                || !boundClasspath.equals(original.getClasspath().getEffectiveClasspath())
                || !boundSourcepath.equals(original.getSourcepath().getEffectiveClasspath()))
            throw new IOException("Compiler configuration changed after registration");
    }

    @SuppressWarnings("unchecked")
    private List<Path> paths(String method, ProcessingEnvironment environment) throws IOException {
        try {
            var getter = RecompileSourcesAction.class.getDeclaredMethod(method, ProcessingEnvironment.class);
            getter.setAccessible(true);
            return List.copyOf((List<Path>) getter.invoke(original, environment));
        } catch (ReflectiveOperationException failure) {
            if (failure.getCause() instanceof IOException cause) throw cause;
            throw new IOException("Pinned compiler path preparation failed", failure);
        }
    }

    @Override public void computeCacheKey(CacheKeyBuilder builder) { original.computeCacheKey(builder); }

    @Override public void run(ProcessingEnvironment environment) throws IOException, InterruptedException {
        String node = String.valueOf(workspace.getFileName());
        try (var actionTiming = SFMHostTiming.start("recompile_action", node)) {
            Path sources;
            List<Path> classpath;
            List<Path> sourcepath;
            Path classes;
            try (var timing = SFMHostTiming.start("recompile_setup", node)) {
                unchanged();
                if (!workspace.equals(environment.getWorkspace().toAbsolutePath().normalize()))
                    throw new IOException("Compiler belongs to another declared workspace");
                sources = environment.getRequiredInputPath("sources");
                classpath = paths("getEffectiveClasspath", environment);
                sourcepath = paths("getEffectiveSourcepath", environment);
                unchanged();
                classes = Files.createDirectory(workspace.resolve("classes"));
                timing.success((long) classpath.size() + sourcepath.size(), -1);
            }
            try (var sourceFs = FileSystems.newFileSystem(URI.create("jar:" + sources.toUri()), Map.of())) {
                Path sourceRoot = sourceFs.getRootDirectories().iterator().next();
                var sourcePaths = new ArrayList<Path>();
                var resources = new ArrayList<Path>();
                try (var timing = SFMHostTiming.start("recompile_source_inventory", node)) {
                    try (var files = Files.walk(sourceRoot).filter(Files::isRegularFile)) {
                        files.forEach(path -> { if (path.getFileName().toString().endsWith(".java")) sourcePaths.add(path); else resources.add(path); });
                    }
                    timing.success((long) sourcePaths.size() + resources.size(), -1);
                }
                try (var compilerTiming = SFMHostTiming.start("recompile_compiler", node)) {
                    var compiler = ToolProvider.getSystemJavaCompiler();
                    if (compiler == null) throw new IOException("Selected owned JVM has no compiler");
                    System.err.println(" Compiling " + sourcePaths.size() + " source files with lease-compatible output");
                    javax.tools.DiagnosticListener<javax.tools.JavaFileObject> diagnostics = diagnostic -> {
                        if (environment.isVerbose() || diagnostic.getKind() == Diagnostic.Kind.ERROR)
                            System.err.println(" " + diagnostic.getKind() + " Line: " + diagnostic.getLineNumber() + ", "
                                    + diagnostic.getMessage(null) + " in " + (diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName()));
                    };
                    try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                        manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
                        manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
                        manager.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourcepath);
                        var task = compiler.getTask(null, manager, diagnostics, options(original), null,
                                manager.getJavaFileObjectsFromPaths(sourcePaths));
                        try (var timing = SFMHostTiming.start("javac_recompile", workspace.getFileName().toString())) {
                            if (!task.call()) throw new IOException("Compilation failed");
                            timing.success(sourcePaths.size(), -1);
                        }
                    }
                    compilerTiming.success(sourcePaths.size(), -1);
                }
                try (var archiveTiming = SFMHostTiming.start("recompile_archive", node)) {
                    unchanged();
                    Path output = environment.getOutputPath("output");
                    if (!output.toAbsolutePath().normalize().equals(workspace.resolve("output.jar")))
                        throw new IOException("Compiler output left its exact declared path");
                    long compilerOutputCount;
                    long compilerOutputBytes = 0;
                    long resourceBytes = 0;
                    try (var stream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), 64 * 1024))) {
                        var directories = new java.util.HashSet<String>();
                        try (var files = Files.walk(classes).filter(Files::isRegularFile)) {
                            List<Path> classPaths;
                            try (var timing = SFMHostTiming.start("recompile_class_output_inventory", node)) {
                                classPaths = files.sorted().toList();
                                timing.success(classPaths.size(), -1);
                            }
                            compilerOutputCount = classPaths.size();
                            try (var timing = SFMHostTiming.start("recompile_class_output_archive", node)) {
                                for (Path path : classPaths) {
                                    String name = classes.relativize(path).toString().replace('\\', '/');
                                    writeParents(stream, name, directories);
                                    stream.putNextEntry(new ZipEntry(name));
                                    compilerOutputBytes += Files.copy(path, stream); stream.closeEntry();
                                }
                                timing.success(compilerOutputCount, compilerOutputBytes);
                            }
                        }
                        try (var timing = SFMHostTiming.start("recompile_resource_archive", node)) {
                            for (Path resource : resources) {
                                String name = sourceRoot.relativize(resource).toString().replace('\\', '/');
                                writeParents(stream, name, directories);
                                stream.putNextEntry(new ZipEntry(name));
                                resourceBytes += Files.copy(resource, stream); stream.closeEntry();
                            }
                            timing.success(resources.size(), resourceBytes);
                        }
                    }
                    archiveTiming.success(compilerOutputCount + resources.size(), compilerOutputBytes + resourceBytes);
                }
                System.err.println("Copied " + resources.size() + " resource files");
            }
            unchanged();
            actionTiming.success(-1, -1);
        }
    }

    private static void writeParents(ZipOutputStream stream, String name, java.util.Set<String> directories) throws IOException {
        for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
            String directory = name.substring(0, slash + 1);
            if (directories.add(directory)) { stream.putNextEntry(new ZipEntry(directory)); stream.closeEntry(); }
        }
    }
}
