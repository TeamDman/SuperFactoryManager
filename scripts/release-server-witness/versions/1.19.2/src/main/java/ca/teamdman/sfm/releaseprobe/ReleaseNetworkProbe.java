package ca.teamdman.sfm.releaseprobe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Test-only Forge 43.4.0 registration topology witness. It does not encode, decode, or send packets.
 * Codec presence is observable here; codec behavior is not.
 */
public final class ReleaseNetworkProbe {
    private static final int EXPECTED_RELEASE_MESSAGES = 37;

    private ReleaseNetworkProbe() {}

    /** A deterministic, path-free JSON object, suitable for embedding in a release witness snapshot. */
    public static String captureJson(String target, String loader) throws ReflectiveOperationException {
        require("1.19.2".equals(target) && "forge-43.4.0".equals(loader),
                "Network snapshot endpoint identity mismatch");
        Class<?> packets = Class.forName("ca.teamdman.sfm.common.registry.registration.SFMPackets");
        Object channel = packets.getField("SFM_CHANNEL").get(null);
        Class<?> simpleChannel = Class.forName("net.minecraftforge.network.simple.SimpleChannel");
        if (!simpleChannel.isInstance(channel)) {
            throw new IllegalStateException("SFM channel is not Forge SimpleChannel");
        }

        Object instance = readField(simpleChannel, channel, "instance");
        Class<?> networkInstance = Class.forName("net.minecraftforge.network.NetworkInstance");
        String channelId = networkInstance.getMethod("getChannelName").invoke(instance).toString();
        String protocolVersion = (String) readField(networkInstance, instance, "networkProtocolVersion");
        require("sfm:manager".equals(channelId), "Unexpected SFM network channel");
        require("1.0.0".equals(protocolVersion), "Unexpected SFM channel protocol version");
        require(protocolVersion.equals(packets.getField("SFM_CHANNEL_VERSION").get(null)),
                "SFM channel version disagrees with its declaration");

        Object codec = readField(simpleChannel, channel, "indexedCodec");
        Object indices = readField(codec.getClass(), codec, "indicies"); // Forge 43.4.0 spelling.
        require(indices instanceof Map<?, ?>, "Forge message index is not a map");
        TreeMap<Integer, String> messages = new TreeMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) indices).entrySet()) {
            require(entry.getKey() instanceof Number, "Forge message index is not numeric");
            int discriminator = ((Number) entry.getKey()).intValue() & 0xffff;
            Object handler = entry.getValue();
            require(handler != null, "Null Forge message handler");
            Class<?> handlerClass = handler.getClass();
            int registeredIndex = (Integer) readField(handlerClass, handler, "index");
            require(discriminator == registeredIndex && registeredIndex < 256,
                    "Forge message discriminator disagrees with registration index");
            Object type = readField(handlerClass, handler, "messageType");
            require(type instanceof Class<?>, "Forge message type is not a class");
            String className = ((Class<?>) type).getName();
            require(className.startsWith("ca.teamdman.sfm.common.net."), "Non-SFM type on SFM channel");
            require(present(readField(handlerClass, handler, "encoder")), "Missing Forge encoder");
            require(present(readField(handlerClass, handler, "decoder")), "Missing Forge decoder");
            require(readField(handlerClass, handler, "networkDirection") instanceof Optional<?> direction
                            && direction.isEmpty(),
                    "Expected unspecified Forge network direction");
            require(messages.put(registeredIndex, className) == null, "Duplicate Forge message index");
        }
        require(messages.size() == EXPECTED_RELEASE_MESSAGES, "Unexpected Forge release message count");
        require((Integer) readField(packets, null, "registrationIndex") == messages.size(),
                "SFM registration counter disagrees with Forge message table");
        for (int i = 0; i < messages.size(); i++) {
            require(messages.containsKey(i), "Gap in Forge message discriminators");
        }

        StringBuilder json = new StringBuilder("{\"schema\":\"sfm:release_network_snapshot@1\",\"target\":");
        appendId(json, target);
        json.append(",\"loader\":");
        appendId(json, loader);
        json.append(",\"channel\":");
        appendId(json, channelId);
        json.append(",\"protocolVersion\":");
        appendId(json, protocolVersion);
        json.append(",\"messages\":[");
        boolean first = true;
        for (Map.Entry<Integer, String> message : messages.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"index\":").append(message.getKey()).append(",\"class\":");
            appendId(json, message.getValue());
            json.append(",\"direction\":\"unspecified\",\"encoderPresent\":true,\"decoderPresent\":true}");
        }
        return json.append("]}").toString();
    }

    private static Object readField(Class<?> owner, Object receiver, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static boolean present(Object value) {
        return value instanceof Optional<?> optional && optional.isPresent();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void appendId(StringBuilder json, String id) {
        require(id.matches("[A-Za-z0-9_.$:/-]+"), "Unexpected network ID syntax");
        json.append('"').append(id).append('"');
    }
}
