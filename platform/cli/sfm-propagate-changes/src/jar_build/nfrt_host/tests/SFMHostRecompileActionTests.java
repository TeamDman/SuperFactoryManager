package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Actual adapter under the Windows lease, compared with pinned original output. */
public final class SFMHostRecompileActionTests {
    public static void main(String[] args) throws Exception {
        Path invocation = Path.of(args[0]);
        Path workspace = Path.of(args[1]).toAbsolutePath().normalize();
        var transfer = JsonParser.parseString(Files.readString(invocation.resolve("contract.json"))).getAsJsonObject();
        Path version = null;
        for (var value : transfer.getAsJsonArray("inputs")) {
            var row = value.getAsJsonObject();
            if (row.get("request_key").getAsString().equals("version_json")) version = invocation.resolve(row.get("snapshot_relative_path").getAsString());
        }
        if (version == null) throw new AssertionError("Missing exact manifest");
        var manifest = MinecraftVersionManifest.from(version);
        try (var engine = SFMNamedEngineFactory.createUnstartedEngine(SFMRustContractConverter.convert(transfer, invocation, manifest))) {
            Path source = workspace.resolve("sources.jar");
            try (var output = new ZipOutputStream(Files.newOutputStream(source))) {
                for (var entry : List.of("Fixture.java", "nested/fixture.txt")) {
                    output.putNextEntry(new ZipEntry(entry));
                    output.write((entry.endsWith(".java") ? "public class Fixture {}\n" : "resource fixture\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.closeEntry();
                }
            }
            var compiler = new RecompileSourcesActionWithJDK(); compiler.setTargetJavaVersion(17);
            var adapter = new SFMHostRecompileAction(compiler, workspace);
            var environment = environment(workspace, source, manifest, engine);
            adapter.run(environment);
            Path baseline = Files.createTempDirectory("sfm-original-recompile-");
            compiler.run(environment(baseline, source, manifest, engine));
            try (var actual = new ZipFile(workspace.resolve("output.jar").toFile());
                 var original = new ZipFile(baseline.resolve("output.jar").toFile())) {
                if (actual.size() != original.size()) throw new AssertionError("Compiler archive membership changed");
                for (var entries = original.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement();
                    var found = actual.getEntry(entry.getName());
                    if (found == null || !java.util.Arrays.equals(original.getInputStream(entry).readAllBytes(), actual.getInputStream(found).readAllBytes()))
                        throw new AssertionError("Compiler payload changed: " + entry.getName());
                }
                if (actual.getEntry("Fixture.class") == null || actual.getEntry("nested/fixture.txt") == null)
                    throw new AssertionError("Missing class or resource");
            }
            try { adapter.run(environment); throw new AssertionError("Repeated class output was adopted"); }
            catch (java.nio.file.FileAlreadyExistsException expected) { }
            compiler.setTargetJavaVersion(21);
            try { adapter.run(null); throw new AssertionError("Changed compiler accepted"); }
            catch (java.io.IOException expected) {
                if (!expected.getMessage().contains("changed after registration")) throw expected;
            }
        }
        System.out.println("PASS leased compiler output, original class/resource byte equality, repeat and mutation refusal");
    }

    private static ProcessingEnvironment environment(Path workspace, Path source, MinecraftVersionManifest manifest,
            net.neoforged.neoform.runtime.engine.NeoFormEngine engine) {
        return (ProcessingEnvironment) Proxy.newProxyInstance(ProcessingEnvironment.class.getClassLoader(), new Class<?>[]{ProcessingEnvironment.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "getWorkspace" -> workspace;
                    case "getRequiredInputPath" -> { if (!values[0].equals("sources")) throw new AssertionError("Foreign source input"); yield source; }
                    case "getRequiredInput" -> { if (!values[0].equals("versionManifest")) throw new AssertionError("Foreign manifest"); yield manifest; }
                    case "getArtifactManager" -> engine.getArtifactManager();
                    case "getOutputPath" -> workspace.resolve("output.jar");
                    case "isVerbose" -> false;
                    default -> throw new AssertionError("Unexpected compiler environment call: " + method.getName());
                });
    }
}
