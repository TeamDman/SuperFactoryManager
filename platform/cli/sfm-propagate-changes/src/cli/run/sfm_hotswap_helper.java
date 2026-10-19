import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SfmHotswapHelper {
    private static final String SESSION_PROPERTY = "sfm.runtimeSession";
    private static final int MAX_PROPERTY_TABLE_SLOTS = 4096;
    private static final int MAX_PROPERTY_NODES = 4096;

    private SfmHotswapHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            throw new IllegalArgumentException(
                    "Usage: SfmHotswapHelper <host> <port> <expected-session-id> "
                    + "<expected-java-major> <class-selector> <classes-dir>..."
            );
        }
        String host = args[0];
        String port = args[1];
        String expectedSessionId = args[2];
        if (expectedSessionId.isBlank()) {
            throw new IllegalArgumentException("Expected runtime session ID must not be blank.");
        }
        int expectedJavaMajor = Integer.parseInt(args[3]);
        if (expectedJavaMajor < 1) {
            throw new IllegalArgumentException("Expected Java major must be positive.");
        }
        String classSelector = args[4];
        List<Path> classesDirs = new ArrayList<>(args.length - 5);
        for (int index = 5; index < args.length; index++) {
            classesDirs.add(Path.of(args[index]));
        }

        VirtualMachine vm = attach(host, port);
        try {
            String targetDescription = targetRuntimeIdentity(vm);
            System.out.println("[hotswap] Target JVM: " + targetDescription);
            ensureCompatibleRuntime(targetDescription);
            ensureExpectedJavaMajor(targetRuntimeVersion(vm), expectedJavaMajor);
            ensureTargetSession(vm, expectedSessionId);
            if (!vm.canBeModified() || !vm.canRedefineClasses()) {
                throw new IllegalStateException(
                        "Target JVM does not support class redefinition. "
                        + "Restart the client with the successful build to apply these changes."
                );
            }
            Map<ReferenceType, byte[]> redefinitions = collectRedefinitions(vm, classesDirs, classSelector);
            if (redefinitions.isEmpty()) {
                System.out.println(
                        "[hotswap] No loaded classes matched " + classSelector + " under " + classesDirs
                );
                return;
            }
            // Class collection may take time. Verify the same session again immediately
            // before applying, without invoking any method in the target JVM.
            ensureTargetSession(vm, expectedSessionId);
            applyRedefinitions(vm, redefinitions);
            System.out.println(
                    "[hotswap] Redefined " + redefinitions.size() + " loaded classes from "
                    + classesDirs.size() + " active class directories."
            );
            for (ReferenceType type : redefinitions.keySet()) {
                System.out.println("[hotswap] " + type.name());
            }
        } finally {
            try {
                vm.dispose();
            } catch (VMDisconnectedException ignored) {
                // A disconnected target must not mask the primary apply/authentication result.
            }
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

    private static String targetRuntimeVersion(VirtualMachine vm) {
        List<ReferenceType> versionTypes = vm.classesByName("java.lang.VersionProps");
        if (versionTypes.size() != 1) {
            throw new IllegalStateException("Cannot verify target Java major: java.lang.VersionProps unavailable.");
        }
        return versionString(versionTypes.get(0), "java_runtime_version");
    }

    static void ensureExpectedJavaMajor(String runtimeVersion, int expectedJavaMajor) {
        Matcher major = Pattern.compile("^(?:1\\.)?(\\d+)(?:[.\\-+]|$)").matcher(runtimeVersion);
        if (!major.find()) {
            throw new IllegalStateException("Cannot verify target Java major from runtime " + runtimeVersion);
        }
        if (Integer.parseInt(major.group(1)) != expectedJavaMajor) {
            throw new IllegalStateException(
                    "Target Java major " + major.group(1) + " does not match successful build Java major "
                    + expectedJavaMajor + ". Select the intended runtime session or restart the client "
                    + "with the matching runtime and successful build; no classes were redefined."
            );
        }
    }

    private static void ensureTargetSession(VirtualMachine vm, String expectedSessionId) {
        String actualSessionId;
        try {
            actualSessionId = targetSessionId(vm);
        } catch (IllegalStateException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "Cannot verify target runtime session: target state changed or became unavailable. "
                    + "Reconnect to the selected session or restart with the session-aware launcher; "
                    + "no classes were redefined.", error
            );
        }
        if (!expectedSessionId.equals(actualSessionId)) {
            throw new IllegalStateException(
                    "Target runtime session " + actualSessionId + " does not match selected session "
                    + expectedSessionId + ". Select the intended session or restart the client with "
                    + "the session-aware launcher; no classes were redefined."
            );
        }
    }

    // JDI has no getSystemProperties(). Read the OpenJDK 17/21/25 Properties
    // backing map directly: no target method invocation, suspension, or mutation.
    // Unknown layouts or concurrent resize markers fail closed rather than trusting a port.
    private static String targetSessionId(VirtualMachine vm) {
        List<ReferenceType> systemTypes = vm.classesByName("java.lang.System");
        if (systemTypes.size() != 1) {
            throw unverifiableSession("java.lang.System unavailable");
        }
        ReferenceType systemType = systemTypes.get(0);
        ObjectReference properties = requiredObject(
                systemType.getValue(requiredField(systemType, "props")), "System.props"
        );
        if (!"java.util.Properties".equals(properties.referenceType().name())) {
            throw unverifiableSession("unsupported System.props implementation");
        }
        ObjectReference map = requiredObject(objectField(properties, "map"), "Properties.map");
        if (!"java.util.concurrent.ConcurrentHashMap".equals(map.referenceType().name())) {
            throw unverifiableSession("unsupported Properties.map implementation");
        }
        Value tableValue = objectField(map, "table");
        if (!(tableValue instanceof ArrayReference table)) {
            throw unverifiableSession("system property table unavailable");
        }
        if (table.length() > MAX_PROPERTY_TABLE_SLOTS) {
            throw unverifiableSession("system property table exceeds inspection bound");
        }
        Set<Long> visited = new HashSet<>();
        for (Value bucket : table.getValues()) {
            ObjectReference node = nullableObject(bucket, "property table node");
            while (node != null) {
                if (visited.size() >= MAX_PROPERTY_NODES || !visited.add(node.uniqueID())) {
                    throw unverifiableSession("system property nodes exceed inspection bound or repeat");
                }
                String nodeType = node.referenceType().name();
                if ("java.util.concurrent.ConcurrentHashMap$TreeBin".equals(nodeType)) {
                    node = nullableObject(objectField(node, "first"), "property tree first node");
                    continue;
                }
                if (!("java.util.concurrent.ConcurrentHashMap$Node".equals(nodeType)
                        || "java.util.concurrent.ConcurrentHashMap$TreeNode".equals(nodeType))) {
                    throw unverifiableSession("unsupported or changing property node " + nodeType);
                }
                Value key = objectField(node, "key");
                if (key instanceof StringReference name && SESSION_PROPERTY.equals(name.value())) {
                    Value value = objectField(node, "val");
                    if (!(value instanceof StringReference sessionId) || sessionId.value().isBlank()) {
                        throw unverifiableSession("runtime session marker is not a nonblank string");
                    }
                    return sessionId.value();
                }
                node = nullableObject(objectField(node, "next"), "next property node");
            }
        }
        throw unverifiableSession("missing -D" + SESSION_PROPERTY + " marker");
    }

    private static Field requiredField(ReferenceType type, String name) {
        Field field = type.fieldByName(name);
        if (field == null) {
            throw unverifiableSession("missing " + type.name() + "." + name);
        }
        return field;
    }

    private static Value objectField(ObjectReference object, String name) {
        return object.getValue(requiredField(object.referenceType(), name));
    }

    private static ObjectReference requiredObject(Value value, String name) {
        ObjectReference object = nullableObject(value, name);
        if (object == null) {
            throw unverifiableSession(name + " unavailable");
        }
        return object;
    }

    private static ObjectReference nullableObject(Value value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectReference object) {
            return object;
        }
        throw unverifiableSession(name + " is not an object reference");
    }

    private static IllegalStateException unverifiableSession(String reason) {
        return new IllegalStateException(
                "Cannot verify target runtime session: " + reason + ". Restart the client with "
                + "the session-aware launcher and retry explicit apply; no classes were redefined."
        );
    }

    private static void applyRedefinitions(VirtualMachine vm, Map<ReferenceType, byte[]> redefinitions) {
        try {
            // Let the target decide which schema changes it supports, including DCEVM.
            vm.redefineClasses(redefinitions);
        } catch (UnsupportedClassVersionError error) {
            throw new IllegalStateException(
                    "Target JVM rejected the replacement class version. Rebuild for the selected Java "
                    + "runtime, or restart the client with a compatible runtime and successful build.", error
            );
        } catch (UnsupportedOperationException error) {
            throw new IllegalStateException(
                    "Target JVM cannot apply this class or schema change. Restart the client with "
                    + "the successful build to apply it; no automatic retry was performed.", error
            );
        } catch (VerifyError | ClassFormatError | ClassCircularityError | NoClassDefFoundError error) {
            throw new IllegalStateException(
                    "Target JVM rejected the replacement class definitions. Rebuild for the selected "
                    + "runtime and restart the client to apply changes that cannot be redefined.", error
            );
        }
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
