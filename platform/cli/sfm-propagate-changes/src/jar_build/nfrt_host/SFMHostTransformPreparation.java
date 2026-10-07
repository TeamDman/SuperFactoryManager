package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction;
import net.neoforged.neoform.runtime.actions.ApplySourceTransformAction;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.problems.FileProblemReporter;
import net.neoforged.problems.Problem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

/** NFRT 2.0.19 transform preparation/postconditions, with no process launch.
 * Original action objects remain the cache-key and configuration owners.
 */
final class SFMHostTransformPreparation {
    static List<String> sourceArguments(ApplySourceTransformAction action, ProcessingEnvironment environment) throws IOException {
        if (action.getClass() != ApplySourceTransformAction.class) throw new IOException("Unreviewed source transform subclass");
        Path report = environment.getWorkspace().resolve("problems.json");
        Path libraries = action.getListLibraries().writeFile(environment);
        var args = new ArrayList<String>(List.of("--problems-report", report.toAbsolutePath().toString(),
                "--libraries-list", environment.getPathArgument(libraries),
                "--in-format", "ARCHIVE", "--out-format", "ARCHIVE"));
        if (!action.getAccessTransformersData().isEmpty() || !action.getAdditionalAccessTransformers().isEmpty()
                || !action.getValidatedAccessTransformers().isEmpty()) {
            args.add("--enable-accesstransformers");
            addDataTransformers(args, action.getAccessTransformersData(), environment);
            addPaths(args, "--access-transformer", action.getValidatedAccessTransformers(), environment, false);
            addPaths(args, "--access-transformer", action.getAdditionalAccessTransformers(), environment, false);
        }
        List<Path> interfaces = sourceInterfaces(action);
        if (!interfaces.isEmpty()) {
            args.add("--enable-interface-injection");
            addPaths(args, "--interface-injection-data", interfaces, environment, true);
            args.add("--interface-injection-stubs");
            args.add("{stubs}");
        }
        if (action.getParchmentData() != null) {
            args.add("--enable-parchment");
            args.add("--parchment-mappings=" + environment.getPathArgument(action.getParchmentData().toAbsolutePath()));
        }
        if (!action.getParserClasspath().isEmpty()) {
            List<Path> paths = environment.getArtifactManager().resolveClasspath(action.getParserClasspath().getEffectiveClasspath());
            args.add("--classpath");
            args.add(paths.stream().map(environment::getPathArgument).collect(Collectors.joining(File.pathSeparator)));
        }
        args.addAll(action.getAdditionalArguments());
        args.add("{input}");
        args.add("{output}");
        return List.copyOf(args);
    }

    static List<String> devArguments(ApplyDevTransformsAction action, ProcessingEnvironment environment) throws IOException {
        if (action.getClass() != ApplyDevTransformsAction.class) throw new IOException("Unreviewed dev transform subclass");
        var args = new ArrayList<String>(List.of("--task", "PROCESS_MINECRAFT_JAR", "--input", "{input}",
                "--output", "{output}", "--no-mod-manifest"));
        addDataTransformers(args, action.getAccessTransformersData(), environment);
        addPaths(args, "--access-transformer", action.getAdditionalAccessTransformers(), environment, true);
        addPaths(args, "--interface-injection-data", action.getInjectedInterfaces(), environment, true);
        return List.copyOf(args);
    }

    static void sourceFinished(ApplySourceTransformAction action, ProcessingEnvironment environment) throws IOException {
        Path report = environment.getWorkspace().resolve("problems.json");
        if (Files.exists(report)) {
            Set<Path> validated = action.getValidatedAccessTransformers().stream()
                    .map(path -> path.toAbsolutePath().normalize()).collect(Collectors.toSet());
            List<Problem> problems = FileProblemReporter.loadRecords(report);
            var failures = new ArrayList<String>();
            for (Problem problem : problems) {
                Path path = normalizedProblemPath(problem);
                if (path == null || validated.contains(path)) environment.getProblemReporter().report(problem);
                // The pinned original fails on all problems for a validated AT,
                // not only ERROR severity. Other AT diagnostics remain filtered.
                if (path != null && validated.contains(path)) failures.add(problem.toString());
            }
            if (!failures.isEmpty()) throw new IOException("Access transformers failed validation:\n" + String.join("\n", failures));
        }
        if (sourceInterfaces(action).isEmpty()) {
            try (var ignored = new ZipOutputStream(Files.newOutputStream(environment.getOutputPath("stubs")))) {
                // The original source action provides an empty JAR when no
                // injected interfaces produced stubs.
            }
        }
    }

    static void sourceFailed(ProcessingEnvironment environment, Exception failure) {
        Path report = environment.getWorkspace().resolve("problems.json");
        try { environment.getProblemReporter().tryMergeFromFile(report); }
        catch (IOException mergeFailure) {
            // Preserve the original child failure; report merging is secondary.
            System.err.println("Could not merge transform problems from " + report + ": " + mergeFailure);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Path> sourceInterfaces(ApplySourceTransformAction action) throws IOException {
        try {
            var field = ApplySourceTransformAction.class.getDeclaredField("injectedInterfaces");
            field.setAccessible(true);
            Object value = field.get(action);
            if (!(value instanceof List<?> paths) || paths.stream().anyMatch(path -> !(path instanceof Path))) {
                throw new IOException("Pinned source interface configuration changed");
            }
            return List.copyOf((List<Path>) value);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Pinned source interface field unavailable", failure);
        }
    }

    private static Path normalizedProblemPath(Problem problem) {
        return problem.location() == null ? null : problem.location().file().toAbsolutePath().normalize();
    }

    private static void addDataTransformers(List<String> args, List<String> ids, ProcessingEnvironment environment) throws IOException {
        for (String id : ids) {
            try (var paths = Files.walk(environment.extractData(id))) {
                // Keep the original walk order; do not reorder AT application.
                paths.filter(Files::isRegularFile).forEach(path -> {
                    args.add("--access-transformer");
                    args.add(environment.getPathArgument(path));
                });
            }
        }
    }

    private static void addPaths(List<String> args, String flag, List<Path> paths,
            ProcessingEnvironment environment, boolean absolute) {
        for (Path path : paths) {
            args.add(flag);
            args.add(environment.getPathArgument(absolute ? path.toAbsolutePath() : path));
        }
    }
}
