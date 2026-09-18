package ca.teamdman.sfm.common.net;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-thread-only subscription and send-budget state for one Minecraft server. */
final class SFMServerClientInboxSubscriptions {
    static final int MAX_CHANNELS_PER_PLAYER = 16;
    static final int MAX_MESSAGES_PER_TICK = 32;
    static final int MAX_BYTES_PER_TICK = 64 * 1024;

    private final Map<UUID, PlayerSession> players = new HashMap<>();

    boolean subscribe(UUID player, UUID session, ResourceLocation dimension, ResourceLocation channel) {
        PlayerSession state = players.computeIfAbsent(player, ignored -> new PlayerSession(session));
        if (!state.session.equals(session)) {
            PlayerSession previous = state;
            state = new PlayerSession(session);
            state.budgetTick = previous.budgetTick;
            state.sentMessages = previous.sentMessages;
            state.sentBytes = previous.sentBytes;
            players.put(player, state);
        }
        ChannelKey key = new ChannelKey(dimension, channel);
        return state.channels.contains(key)
               || (state.channels.size() < MAX_CHANNELS_PER_PLAYER && state.channels.add(key));
    }

    void unsubscribe(UUID player, UUID session, ResourceLocation dimension, ResourceLocation channel) {
        PlayerSession state = players.get(player);
        if (state != null && state.session.equals(session)) {
            state.channels.remove(new ChannelKey(dimension, channel));
        }
    }

    Optional<UUID> subscribedSession(SFMClientInboxAddress address) {
        PlayerSession state = players.get(address.recipient());
        if (state == null || !state.channels.contains(new ChannelKey(address.dimension(), address.channel()))) {
            return Optional.empty();
        }
        return Optional.of(state.session);
    }

    boolean reserveSend(UUID player, long tick, int payloadBytes) {
        PlayerSession state = players.get(player);
        if (state == null || payloadBytes < 0 || payloadBytes > MAX_BYTES_PER_TICK) {
            return false;
        }
        if (state.budgetTick != tick) {
            state.budgetTick = tick;
            state.sentMessages = 0;
            state.sentBytes = 0;
        }
        if (state.sentMessages >= MAX_MESSAGES_PER_TICK
            || state.sentBytes + payloadBytes > MAX_BYTES_PER_TICK) {
            return false;
        }
        state.sentMessages++;
        state.sentBytes += payloadBytes;
        return true;
    }

    void remove(UUID player) {
        players.remove(player);
    }

    private record ChannelKey(ResourceLocation dimension, ResourceLocation channel) {
    }

    private static final class PlayerSession {
        private final UUID session;
        private final Set<ChannelKey> channels = new HashSet<>();
        private long budgetTick = Long.MIN_VALUE;
        private int sentMessages;
        private int sentBytes;

        private PlayerSession(UUID session) {
            this.session = session;
        }
    }
}
