import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SfmHotswapHelper {
    private SfmHotswapHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: SfmHotswapHelper <host> <port> <classes-dir> <class-prefix>");
        }
        String host = args[0];
        String port = args[1];
        Path classesDir = Path.of(args[2]);
        String classPrefix = args[3];

        VirtualMachine vm = attach(host, port);
        try {
            Map<ReferenceType, byte[]> redefinitions = collectRedefinitions(vm, classesDir, classPrefix);
            if (redefinitions.isEmpty()) {
                System.out.println("[hotswap] No loaded classes matched " + classPrefix + " under " + classesDir);
                return;
            }
            vm.redefineClasses(redefinitions);
            System.out.println("[hotswap] Redefined " + redefinitions.size() + " loaded classes.");
            for (ReferenceType type : redefinitions.keySet()) {
                System.out.println("[hotswap] " + type.name());
            }
        } finally {
            vm.dispose();
        }
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
        return connector.attach(arguments);
    }

    private static Map<ReferenceType, byte[]> collectRedefinitions(
            VirtualMachine vm,
            Path classesDir,
            String classPrefix
    ) throws IOException {
        Map<ReferenceType, byte[]> redefinitions = new LinkedHashMap<>();
        List<ReferenceType> loadedClasses = vm.allClasses();
        for (ReferenceType type : loadedClasses) {
            String className = type.name();
            if (!matchesSelector(className, classPrefix)) {
                continue;
            }
            Path classFile = classesDir.resolve(className.replace('.', '/') + ".class");
            if (!Files.isRegularFile(classFile)) {
                continue;
            }
            redefinitions.put(type, Files.readAllBytes(classFile));
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
