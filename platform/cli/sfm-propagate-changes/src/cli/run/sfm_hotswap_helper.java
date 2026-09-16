import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SfmHotswapHelper {
    private SfmHotswapHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: SfmHotswapHelper <host> <port> <class-selector> <classes-dir>..."
            );
        }
        String host = args[0];
        String port = args[1];
        String classSelector = args[2];
        List<Path> classesDirs = new ArrayList<>(args.length - 3);
        for (int index = 3; index < args.length; index++) {
            classesDirs.add(Path.of(args[index]));
        }

        VirtualMachine vm = attach(host, port);
        try {
            String targetDescription = targetRuntimeIdentity(vm);
            System.out.println("[hotswap] Target JVM: " + targetDescription);
            ensureCompatibleRuntime(targetDescription);
            Map<ReferenceType, byte[]> redefinitions = collectRedefinitions(vm, classesDirs, classSelector);
            if (redefinitions.isEmpty()) {
                System.out.println(
                        "[hotswap] No loaded classes matched " + classSelector + " under " + classesDirs
                );
                return;
            }
            vm.redefineClasses(redefinitions);
            System.out.println(
                    "[hotswap] Redefined " + redefinitions.size() + " loaded classes from "
                    + classesDirs.size() + " active class directories."
            );
            for (ReferenceType type : redefinitions.keySet()) {
                System.out.println("[hotswap] " + type.name());
            }
        } finally {
            vm.dispose();
        }
    }

    // Validate the attached JVM, not the Java executable running this helper.
    // Updating the helper cannot repair stale JDWP identities in an old live JVM.
    private static String targetRuntimeIdentity(VirtualMachine vm) {
        // JDWP VirtualMachine.Version only exposes java.version (no vendor/build).
        // Reading these bootstrap constants does not invoke a method or suspend threads.
        List<ReferenceType> versionTypes = vm.classesByName("java.lang.VersionProps");
        if (versionTypes.size() != 1) {
            throw new IllegalStateException("Cannot verify target runtime build: java.lang.VersionProps unavailable.");
        }
        ReferenceType type = versionTypes.get(0);
        return versionString(type, "VENDOR") + " " + versionString(type, "VENDOR_VERSION")
                + " runtime=" + versionString(type, "java_runtime_version");
    }

    private static String versionString(ReferenceType type, String name) {
        Field field = type.fieldByName(name);
        if (field == null || !(type.getValue(field) instanceof StringReference value)) {
            throw new IllegalStateException("Cannot verify target runtime build: missing " + name);
        }
        return value.value();
    }

    static void ensureCompatibleRuntime(String description) {
        if (!(description.contains("JBR") || description.contains("JetBrains"))
                || !description.contains("17.0.")) {
            return;
        }
        Matcher build = Pattern.compile("(?:JBR(?:SDK)?-|runtime=)17\\.0\\.\\d+\\+\\d+-b?(\\d+)\\.(\\d+)")
                .matcher(description);
        if (build.find()) {
            int family = Integer.parseInt(build.group(1));
            int revision = Integer.parseInt(build.group(2));
            if (family > 1207 || (family == 1207 && revision >= 6)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Target JBR17 is not verified for repeated enhanced hotswap (JBR-6648). "
                + "Restart the client with JBRSDK 17.0.14 b1367.22 or another fixed build "
                + "(17.0.10 b1207.6 or later fixed build family). Retrying this old JVM "
                + "or using a newer helper does not repair its JDWP identity table."
        );
    }

    private static VirtualMachine attach(String host, String port) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager()
                .attachingConnectors()
                .stream()
                .filter(candidate -> "com.sun.jdi.SocketAttach".equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SocketAttach JDI connector is available."));
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(host);
        arguments.get("port").setValue(port);
        arguments.get("timeout").setValue("10000");
        return connector.attach(arguments);
    }

    private static Map<ReferenceType, byte[]> collectRedefinitions(
            VirtualMachine vm,
            List<Path> classesDirs,
            String classSelector
    ) throws IOException {
        Map<ReferenceType, byte[]> redefinitions = new LinkedHashMap<>();
        List<ReferenceType> loadedClasses = vm.allClasses();
        for (ReferenceType type : loadedClasses) {
            String className = type.name();
            if (!matchesSelector(className, classSelector)) {
                continue;
            }
            Path relativeClassFile = Path.of(className.replace('.', '/') + ".class");
            // The interactive client's merged source root is assembled in this order, so a
            // later source set wins if two active roots happen to contain the same class.
            for (int index = classesDirs.size() - 1; index >= 0; index--) {
                Path classFile = classesDirs.get(index).resolve(relativeClassFile);
                if (Files.isRegularFile(classFile)) {
                    redefinitions.put(type, Files.readAllBytes(classFile));
                    break;
                }
            }
        }
        return redefinitions;
    }

    private static boolean matchesSelector(String className, String classPrefix) {
        if (classPrefix.startsWith("=")) {
            return className.equals(classPrefix.substring(1));
        }
        return className.startsWith(classPrefix);
    }
}
