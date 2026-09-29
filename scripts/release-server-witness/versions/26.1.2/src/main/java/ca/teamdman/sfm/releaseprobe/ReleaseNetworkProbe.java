package ca.teamdman.sfm.releaseprobe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Test-only NeoForge 26.1.2.72 registration topology witness. It does not encode, decode, or send packets.
 * Codec presence is observable here; codec behavior is not.
 */
public final class ReleaseNetworkProbe {
    private static final int EXPECTED_RELEASE_PAYLOADS = 37;

    private ReleaseNetworkProbe() {}

    /** A deterministic, path-free JSON object, suitable for embedding in a release witness snapshot. */
    public static String captureJson() throws ReflectiveOperationException {
        // The release runner compiles the probe against a deliberately small loader classpath.
        // Resolve loader types at runtime so this helper does not enlarge that build surface.
        Class<?> networkRegistry = Class.forName("net.neoforged.neoforge.network.registration.NetworkRegistry");
        Class<?> connectionProtocol = Class.forName("net.minecraft.network.ConnectionProtocol");
        Class<?> packetFlow = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        Class<?> payloadRegistration = Class.forName("net.neoforged.neoforge.network.registration.PayloadRegistration");
        Object play = connectionProtocol.getField("PLAY").get(null);
        Field field = networkRegistry.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        Object registrations = field.get(null);
        require(registrations instanceof Map<?, ?>, "NeoForge payload registrations are not a map");
        Object playRegistrations = ((Map<?, ?>) registrations).get(play);
        require(playRegistrations instanceof Map<?, ?>, "Missing NeoForge play payload registrations");

        TreeMap<String, String> payloads = new TreeMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) playRegistrations).entrySet()) {
            require(identifier.isInstance(entry.getKey()), "NeoForge payload ID is not an Identifier");
            Object id = entry.getKey();
            if (!"sfm".equals(identifier.getMethod("getNamespace").invoke(id))) continue;
            require(payloadRegistration.isInstance(entry.getValue()), "SFM payload has no registration");
            Object registration = entry.getValue();
            require(payloadRegistration.getMethod("id").invoke(registration).equals(id),
                    "SFM payload type disagrees with registry key");
            require(payloadRegistration.getMethod("protocols").invoke(registration).equals(List.of(play)),
                    "SFM payload has unexpected protocols");
            Object flowValue = payloadRegistration.getMethod("flow").invoke(registration);
            require(flowValue instanceof Optional<?> && ((Optional<?>) flowValue).isPresent(),
                    "SFM payload direction is unspecified");
            Object flow = ((Optional<?>) flowValue).orElseThrow();
            require(packetFlow.isInstance(flow), "SFM payload has unexpected direction type");
            String direction = ((Enum<?>) flow).name();
            require(direction.equals("CLIENTBOUND") || direction.equals("SERVERBOUND"),
                    "SFM payload has unexpected direction");
            require("1.0.0".equals(payloadRegistration.getMethod("version").invoke(registration)),
                    "Unexpected SFM payload version");
            require(Boolean.FALSE.equals(payloadRegistration.getMethod("optional").invoke(registration)),
                    "SFM release payload unexpectedly optional");
            require(payloadRegistration.getMethod("codec").invoke(registration) != null,
                    "SFM payload has no codec");
            require(networkRegistry.getMethod("getCodec", identifier, connectionProtocol, packetFlow)
                            .invoke(null, id, play, flow) != null,
                    "SFM payload codec is not available to NeoForge dispatch");
            require(id.toString().matches("[a-z0-9_.:/-]+"), "Unexpected SFM payload ID syntax");
            String descriptor = "{\"id\":\"" + id + "\",\"direction\":\""
                    + direction.toLowerCase(java.util.Locale.ROOT)
                    + "\",\"protocol\":\"play\",\"version\":\"1.0.0\",\"optional\":false,\"codecPresent\":true}";
            require(payloads.put(id.toString(), descriptor) == null, "Duplicate SFM payload ID");
        }
        require(payloads.size() == EXPECTED_RELEASE_PAYLOADS, "Unexpected NeoForge release payload count");

        StringBuilder json = new StringBuilder("{\"schema\":\"sfm:release_network_snapshot@1\",\"payloads\":[");
        boolean first = true;
        for (String descriptor : payloads.values()) {
            if (!first) json.append(',');
            first = false;
            json.append(descriptor);
        }
        return json.append("]}").toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
