package ca.teamdman.sfm.releaseprobe;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Test-only adapter for exercising the unchanged production SFM packet channel. */
public final class ReleaseClientNetworkProbe {
    public static final String RESPONSE_SCREEN = "ca.teamdman.sfm.client.screen.TomlEditScreen";
    private static final String CONFIG_FIXTURE = "# SFM packet codec witness\nmessage = \"caf\u00e9\"\n";

    public record CodecFixture(String requestSha256, int requestBytes, String responseSha256,
                               int responseBytes) {}

    private ReleaseClientNetworkProbe() {}

    public static CodecFixture captureCodecFixture(Object registryAccess) throws ReflectiveOperationException,
            NoSuchAlgorithmException {
        Class<?> modeClass = Class.forName("ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput");
        Object show = showMode(modeClass);
        Class<?> requestClass = Class.forName("ca.teamdman.sfm.common.net.ServerboundServerConfigRequestPacket");
        Object request = requestClass.getConstructor(modeClass).newInstance(show);
        Class<?> responseClass = Class.forName("ca.teamdman.sfm.common.net.ClientboundServerConfigCommandPacket");
        Object response = responseClass.getConstructor(String.class, modeClass).newInstance(CONFIG_FIXTURE, show);

        byte[] requestBytes = roundTripBody(requestClass, request, registryAccess);
        byte[] responseBytes = roundTripBody(responseClass, response, registryAccess);
        if (requestBytes.length == 0 || responseBytes.length <= requestBytes.length) {
            throw new IllegalStateException("Unexpected SFM codec fixture lengths");
        }
        return new CodecFixture(sha256(requestBytes), requestBytes.length,
                sha256(responseBytes), responseBytes.length);
    }

    public static void requestServerConfigShow() throws ReflectiveOperationException {
        Class<?> modeClass = Class.forName("ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput");
        Object show = showMode(modeClass);
        Class<?> requestClass = Class.forName("ca.teamdman.sfm.common.net.ServerboundServerConfigRequestPacket");
        Object request = requestClass.getConstructor(modeClass).newInstance(show);
        Class<?> packetsClass = Class.forName("ca.teamdman.sfm.common.registry.registration.SFMPackets");
        Method send = null;
        for (Method candidate : packetsClass.getMethods()) {
            if (candidate.getName().equals("sendToServer") && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isAssignableFrom(requestClass)) {
                if (send != null) {
                    throw new IllegalStateException("Ambiguous SFM sendToServer method");
                }
                send = candidate;
            }
        }
        if (send == null) {
            throw new IllegalStateException("SFM sendToServer method is missing");
        }
        send.invoke(null, request);
    }

    public static boolean isConfigResponseScreen(Object screen) {
        return screen != null && RESPONSE_SCREEN.equals(screen.getClass().getName());
    }

    private static Object showMode(Class<?> modeClass) {
        for (Object candidate : modeClass.getEnumConstants()) {
            if ("SHOW".equals(((Enum<?>) candidate).name())) {
                return candidate;
            }
        }
        throw new IllegalStateException("SFM SHOW request mode is missing");
    }

    private static byte[] roundTripBody(Class<?> packetClass, Object packet, Object registryAccess)
            throws ReflectiveOperationException {
        Class<?> daddyClass = Class.forName(packetClass.getName() + "$Daddy");
        Object daddy = daddyClass.getConstructor().newInstance();
        Method encode = null;
        for (Method candidate : daddyClass.getMethods()) {
            if (candidate.getName().equals("encode") && !candidate.isBridge()
                    && candidate.getParameterCount() == 2 && candidate.getParameterTypes()[0] == packetClass) {
                if (encode != null) throw new IllegalStateException("Ambiguous SFM packet encoder");
                encode = candidate;
            }
        }
        if (encode == null) throw new IllegalStateException("SFM packet encoder is missing");
        Class<?> bufferClass = encode.getParameterTypes()[1];
        Method decode = null;
        for (Method candidate : daddyClass.getMethods()) {
            if (candidate.getName().equals("decode") && !candidate.isBridge()
                    && candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0] == bufferClass) {
                if (decode != null) throw new IllegalStateException("Ambiguous SFM packet decoder");
                decode = candidate;
            }
        }
        if (decode == null) throw new IllegalStateException("SFM packet decoder is missing");

        Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
        Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
        Object outgoing = newNetworkBuffer(bufferClass, registryAccess,
                unpooledClass.getMethod("buffer").invoke(null), byteBufClass);
        try {
            encode.invoke(daddy, packet, outgoing);
            int length = (Integer) byteBufClass.getMethod("readableBytes").invoke(outgoing);
            byte[] bytes = new byte[length];
            byteBufClass.getMethod("getBytes", int.class, byte[].class).invoke(outgoing, 0, bytes);

            Object incoming = newNetworkBuffer(bufferClass, registryAccess,
                    unpooledClass.getMethod("wrappedBuffer", byte[].class).invoke(null, (Object) bytes),
                    byteBufClass);
            try {
                Object decoded = decode.invoke(daddy, incoming);
                if (!packet.equals(decoded) ||
                        (Integer) byteBufClass.getMethod("readableBytes").invoke(incoming) != 0) {
                    throw new IllegalStateException("SFM packet codec fixture did not round-trip exactly");
                }
            } finally {
                byteBufClass.getMethod("release").invoke(incoming);
            }
            return bytes;
        } finally {
            byteBufClass.getMethod("release").invoke(outgoing);
        }
    }

    private static Object newNetworkBuffer(Class<?> bufferClass, Object registryAccess, Object source,
                                           Class<?> byteBufClass) throws ReflectiveOperationException {
        if (bufferClass.getName().equals("net.minecraft.network.FriendlyByteBuf")) {
            return bufferClass.getConstructor(byteBufClass).newInstance(source);
        }
        if (bufferClass.getName().equals("net.minecraft.network.RegistryFriendlyByteBuf")
                && registryAccess != null) {
            Class<?> accessClass = Class.forName("net.minecraft.core.RegistryAccess");
            if (!accessClass.isInstance(registryAccess)) {
                throw new IllegalStateException("Unexpected registry access for SFM packet fixture");
            }
            return bufferClass.getConstructor(byteBufClass, accessClass).newInstance(source, registryAccess);
        }
        throw new IllegalStateException("Unsupported SFM packet buffer: " + bufferClass.getName());
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
