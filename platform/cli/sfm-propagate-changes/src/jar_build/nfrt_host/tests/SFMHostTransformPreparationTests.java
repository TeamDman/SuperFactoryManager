package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction;
import net.neoforged.neoform.runtime.actions.ApplySourceTransformAction;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.problems.FileProblemReporter;
import net.neoforged.problems.Problem;
import net.neoforged.problems.ProblemGroup;
import net.neoforged.problems.ProblemId;
import net.neoforged.problems.ProblemReporter;
import net.neoforged.problems.ProblemSeverity;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Preparation and postconditions only: no graph node or child tool executes. */
public final class SFMHostTransformPreparationTests {
    public static void main(String[] args) throws Exception {
        Path invocation = Path.of(args[0]).toAbsolutePath().normalize();
        var transfer = JsonParser.parseString(Files.readString(invocation.resolve("contract.json"))).getAsJsonObject();
        Path version = null;
        for (var row : transfer.getAsJsonArray("inputs")) {
            if (row.getAsJsonObject().get("request_key").getAsString().equals("version_json")) {
                version = invocation.resolve(row.getAsJsonObject().get("snapshot_relative_path").getAsString());
            }
        }
        if (version == null) throw new AssertionError("missing original version fixture");
        var manifest = MinecraftVersionManifest.from(version);
        var contract = SFMRustContractConverter.convert(transfer, invocation, manifest);
        try (var engine = SFMNamedEngineFactory.createUnstartedEngine(contract)) {
            Path workspace = Files.createTempDirectory("sfm-transform-prepare-test-");
            var frozenSource = new ApplySourceTransformAction();
            var frozenWrapper = new SFMHostTransformToolAction("sourceTransform", frozenSource, workspace,
                    request -> { throw new AssertionError("changed transform reached child launch"); });
            var frozenConfig = JsonParser.parseString(frozenWrapper.configuration()).getAsJsonObject();
            if (!frozenConfig.get("class").getAsString().equals(ApplySourceTransformAction.class.getName())
                    || !JsonParser.parseString(frozenConfig.get("transform_preparation_json").getAsString())
                    .getAsJsonObject().get("schema").getAsString().equals("sfm:nfrt_source_transform@1")) {
                throw new AssertionError("specialized wrapper lost original graph identity");
            }
            frozenSource.addArg("--changed-after-registration");
            try {
                frozenWrapper.run(null);
                throw new AssertionError("changed source transform was accepted");
            } catch (java.io.IOException expectedFailure) {
                if (!expectedFailure.getMessage().contains("changed after graph registration")) throw expectedFailure;
            }
            try {
                new SFMHostTransformToolAction("foreign", new net.neoforged.neoform.runtime.actions.ExternalJavaToolAction(List.of(), "fixture.Main"),
                        workspace, request -> { throw new AssertionError("foreign action reached child launch"); });
                throw new AssertionError("base external action entered specialized wrapper");
            } catch (java.io.IOException expectedFailure) {
                if (!expectedFailure.getMessage().contains("Unreviewed")) throw expectedFailure;
            }
            Path data = Files.createDirectory(workspace.resolve("data"));
            Path embedded = Files.writeString(data.resolve("embedded.cfg"), "public fixture.Type\n");
            Path validated = Files.writeString(workspace.resolve("validated.cfg"), "public fixture.Type\n");
            Path additional = Files.writeString(workspace.resolve("additional.cfg"), "public fixture.Type\n");
            Path interfaces = Files.writeString(workspace.resolve("interfaces.json"), "{}");
            Path parchment = Files.writeString(workspace.resolve("parchment.zip"), "fixture");
            Path parser = invocation.resolve("inputs/artifacts/0000.bin");
            var reported = new ArrayList<Problem>();
            ProblemReporter reporter = reported::add;
            var environment = (ProcessingEnvironment) Proxy.newProxyInstance(ProcessingEnvironment.class.getClassLoader(),
                    new Class<?>[] { ProcessingEnvironment.class }, (proxy, method, values) -> {
                        return switch (method.getName()) {
                            case "getWorkspace" -> workspace;
                            case "getArtifactManager" -> engine.getArtifactManager();
                            case "getRequiredInput" -> {
                                if (!values[0].equals("versionManifest")) throw new AssertionError("foreign required input");
                                yield manifest;
                            }
                            case "getPathArgument" -> ((Path) values[0]).toString();
                            case "extractData" -> {
                                if (!values[0].equals("fixture_at")) throw new AssertionError("foreign data request");
                                yield data;
                            }
                            case "getOutputPath" -> workspace.resolve(values[0] + ".jar");
                            case "getProblemReporter" -> reporter;
                            default -> throw new AssertionError("unreviewed preparation call " + method.getName());
                        };
                    });
            var source = new ApplySourceTransformAction();
            source.setAccessTransformersData(List.of("fixture_at"));
            source.setValidatedAccessTransformers(List.of(validated));
            source.setAdditionalAccessTransformers(List.of(additional));
            source.setInjectedInterfaces(List.of(interfaces));
            source.setParchmentData(parchment);
            source.getParserClasspath().addPaths(List.of(parser));
            source.addArg("--access-transformer-validation=error");
            List<String> expected = List.of("--problems-report", workspace.resolve("problems.json").toString(),
                    "--libraries-list", workspace.resolve("libraries.txt").toString(),
                    "--in-format", "ARCHIVE", "--out-format", "ARCHIVE", "--enable-accesstransformers",
                    "--access-transformer", embedded.toString(), "--access-transformer", validated.toString(),
                    "--access-transformer", additional.toString(), "--enable-interface-injection",
                    "--interface-injection-data", interfaces.toString(), "--interface-injection-stubs", "{stubs}",
                    "--enable-parchment", "--parchment-mappings=" + parchment,
                    "--classpath", parser.toString(), "--access-transformer-validation=error", "{input}", "{output}");
            if (!SFMHostTransformPreparation.sourceArguments(source, environment).equals(expected)) {
                throw new AssertionError("source preparation changed original argument order or semantics");
            }
            if (!source.getArgs().isEmpty()) throw new AssertionError("preparation mutated original source arguments");
            var dev = new ApplyDevTransformsAction();
            dev.setAccessTransformersData(List.of("fixture_at"));
            dev.setAdditionalAccessTransformers(List.of(additional));
            dev.setInjectedInterfaces(List.of(interfaces));
            List<String> expectedDev = List.of("--task", "PROCESS_MINECRAFT_JAR", "--input", "{input}",
                    "--output", "{output}", "--no-mod-manifest", "--access-transformer", embedded.toString(),
                    "--access-transformer", additional.toString(), "--interface-injection-data", interfaces.toString());
            if (!SFMHostTransformPreparation.devArguments(dev, environment).equals(expectedDev)) throw new AssertionError("dev preparation changed");
            Path report = workspace.resolve("problems.json");
            var id = ProblemId.create("fixture", "Fixture", ProblemGroup.create("sfm_fixture", "SFM fixture"));
            Problem ordinary = Problem.builder(id).severity(ProblemSeverity.ERROR).inFile(additional).contextualLabel("ordinary").build();
            Problem global = Problem.builder(id).severity(ProblemSeverity.ADVICE).contextualLabel("global").build();
            Problem validatedAdvice = Problem.builder(id).severity(ProblemSeverity.ADVICE).inFile(validated).contextualLabel("validated").build();
            try (var writer = new FileProblemReporter(report)) { writer.report(ordinary); writer.report(global); }
            source.setInjectedInterfaces(List.of());
            SFMHostTransformPreparation.sourceFinished(source, environment);
            if (!reported.equals(List.of(global))) throw new AssertionError("original report filtering changed");
            try (var stubs = new ZipFile(workspace.resolve("stubs.jar").toFile())) {
                if (stubs.size() != 0) throw new AssertionError("empty stub output not preserved");
            }
            try (var writer = new FileProblemReporter(report)) { writer.report(validatedAdvice); }
            try { SFMHostTransformPreparation.sourceFinished(source, environment); throw new AssertionError("validated advice was accepted"); }
            catch (java.io.IOException expectedFailure) {
                if (!expectedFailure.getMessage().startsWith("Access transformers failed validation:")) throw expectedFailure;
            }
            reported.clear();
            SFMHostTransformPreparation.sourceFailed(environment, new java.io.IOException("fixture child failure"));
            if (!reported.equals(List.of(validatedAdvice))) throw new AssertionError("failure report merge lost");
            System.out.println("PASS source/dev argument ordering, ATs, interfaces, parchment, parser classpath, original immutability, report filtering, validated advice refusal, failure merge and empty stubs; no child executed");
        }
    }
}
