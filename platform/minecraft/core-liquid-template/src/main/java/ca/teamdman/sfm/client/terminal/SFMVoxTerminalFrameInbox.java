package ca.teamdman.sfm.client.terminal;

import java.util.Optional;

/**
 * Pure latest-frame state machine for one request-scoped Vox subscription.
 *
 * <p>The local subscription generation rejects completions from a receiver
 * that belonged to a transport which has already been replaced. Remote epochs
 * are opaque equality tokens and become authoritative only after the first
 * full resynchronization frame for the current generation is accepted.
 */
final class SFMVoxTerminalFrameInbox {
    enum State {
        DISCONNECTED,
        AWAITING_FULL_RESYNC,
        LIVE
    }

    enum OfferResult {
        ACCEPTED,
        ACCEPTED_SUPERSEDING,
        REJECTED_STALE_SUBSCRIPTION,
        REJECTED_SESSION,
        REJECTED_AWAITING_FULL_RESYNC,
        REJECTED_EPOCH,
        REJECTED_SEQUENCE,
        REJECTED_INVALID_FRAME;

        boolean accepted() {
            return this == ACCEPTED || this == ACCEPTED_SUPERSEDING;
        }

        boolean superseded() {
            return this == ACCEPTED_SUPERSEDING;
        }
    }

    record Subscription(long generation, String sessionId) {}

    record Event(
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            long terminalSequence,
            long frameSequence,
            boolean fullResync,
            long maxFrameBytes,
            String backendId,
            String transportId,
            String correlationId,
            String snapshotSessionId,
            long snapshotSequence,
            boolean snapshotComplete,
            SFMTerminalFrame frame) {}

    record Snapshot(
            State state,
            long generation,
            String expectedSessionId,
            String connectionEpoch,
            String sessionEpoch,
            long lastTerminalSequence,
            long lastFrameSequence,
            long negotiatedMaxFrameBytes,
            boolean framePending,
            long subscriptionsStarted,
            long disconnects,
            long eventsReceived,
            long eventsAccepted,
            long eventsSuperseded,
            long framesDelivered,
            long staleSubscriptionRejections,
            long sessionRejections,
            long fullResyncRejections,
            long epochRejections,
            long sequenceRejections,
            long invalidFrameRejections) {}

    private final long configuredMaxFrameBytes;
    private State state = State.DISCONNECTED;
    private long generation;
    private String expectedSessionId = "";
    private String connectionEpoch = "";
    private String sessionEpoch = "";
    private long lastTerminalSequence = Long.MIN_VALUE;
    private long lastFrameSequence = Long.MIN_VALUE;
    private long negotiatedMaxFrameBytes;
    private SFMTerminalFrame pendingFrame;
    private long subscriptionsStarted;
    private long disconnects;
    private long eventsReceived;
    private long eventsAccepted;
    private long eventsSuperseded;
    private long framesDelivered;
    private long staleSubscriptionRejections;
    private long sessionRejections;
    private long fullResyncRejections;
    private long epochRejections;
    private long sequenceRejections;
    private long invalidFrameRejections;

    SFMVoxTerminalFrameInbox(long configuredMaxFrameBytes) {
        if (configuredMaxFrameBytes <= 0) {
            throw new IllegalArgumentException("configuredMaxFrameBytes must be positive");
        }
        this.configuredMaxFrameBytes = configuredMaxFrameBytes;
    }

    synchronized Subscription begin(String sessionId) {
        if (isBlank(sessionId)) throw new IllegalArgumentException("sessionId must not be blank");
        if (generation == Long.MAX_VALUE) {
            throw new IllegalStateException("terminal subscription generation exhausted");
        }
        generation++;
        expectedSessionId = sessionId;
        connectionEpoch = "";
        sessionEpoch = "";
        lastTerminalSequence = Long.MIN_VALUE;
        lastFrameSequence = Long.MIN_VALUE;
        negotiatedMaxFrameBytes = 0;
        pendingFrame = null;
        state = State.AWAITING_FULL_RESYNC;
        subscriptionsStarted = increment(subscriptionsStarted);
        return new Subscription(generation, sessionId);
    }

    synchronized void disconnect(Subscription subscription) {
        if (!isCurrent(subscription)) return;
        disconnects = increment(disconnects);
        expectedSessionId = "";
        connectionEpoch = "";
        sessionEpoch = "";
        lastTerminalSequence = Long.MIN_VALUE;
        lastFrameSequence = Long.MIN_VALUE;
        negotiatedMaxFrameBytes = 0;
        pendingFrame = null;
        state = State.DISCONNECTED;
    }

    synchronized boolean isCurrent(Subscription subscription) {
        return subscription != null
                && state != State.DISCONNECTED
                && subscription.generation() == generation
                && expectedSessionId.equals(subscription.sessionId());
    }

    synchronized OfferResult offer(Subscription subscription, Event event) {
        eventsReceived = increment(eventsReceived);
        if (!isCurrent(subscription)) {
            staleSubscriptionRejections = increment(staleSubscriptionRejections);
            return OfferResult.REJECTED_STALE_SUBSCRIPTION;
        }
        if (event == null || !expectedSessionId.equals(event.sessionId())) {
            sessionRejections = increment(sessionRejections);
            return OfferResult.REJECTED_SESSION;
        }
        if (!validFrame(event)) {
            invalidFrameRejections = increment(invalidFrameRejections);
            return OfferResult.REJECTED_INVALID_FRAME;
        }
        if (state == State.AWAITING_FULL_RESYNC) {
            if (!event.fullResync()) {
                fullResyncRejections = increment(fullResyncRejections);
                return OfferResult.REJECTED_AWAITING_FULL_RESYNC;
            }
            connectionEpoch = event.connectionEpoch();
            sessionEpoch = event.sessionEpoch();
            negotiatedMaxFrameBytes = event.maxFrameBytes();
            state = State.LIVE;
        } else {
            if (!connectionEpoch.equals(event.connectionEpoch())
                    || !sessionEpoch.equals(event.sessionEpoch())
                    || negotiatedMaxFrameBytes != event.maxFrameBytes()) {
                epochRejections = increment(epochRejections);
                return OfferResult.REJECTED_EPOCH;
            }
            if (event.terminalSequence() < lastTerminalSequence
                    || event.frameSequence() <= lastFrameSequence) {
                sequenceRejections = increment(sequenceRejections);
                return OfferResult.REJECTED_SEQUENCE;
            }
        }

        boolean superseded = pendingFrame != null;
        pendingFrame = event.frame();
        lastTerminalSequence = event.terminalSequence();
        lastFrameSequence = event.frameSequence();
        eventsAccepted = increment(eventsAccepted);
        if (superseded) eventsSuperseded = increment(eventsSuperseded);
        return superseded ? OfferResult.ACCEPTED_SUPERSEDING : OfferResult.ACCEPTED;
    }

    synchronized Optional<SFMTerminalFrame> takeLatest() {
        if (pendingFrame == null) return Optional.empty();
        SFMTerminalFrame result = pendingFrame;
        pendingFrame = null;
        framesDelivered = increment(framesDelivered);
        return Optional.of(result);
    }

    synchronized long lastTerminalSequence() {
        return state == State.LIVE ? lastTerminalSequence : 0;
    }

    synchronized boolean isLive() {
        return state == State.LIVE;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                state,
                generation,
                expectedSessionId,
                connectionEpoch,
                sessionEpoch,
                lastTerminalSequence,
                lastFrameSequence,
                negotiatedMaxFrameBytes,
                pendingFrame != null,
                subscriptionsStarted,
                disconnects,
                eventsReceived,
                eventsAccepted,
                eventsSuperseded,
                framesDelivered,
                staleSubscriptionRejections,
                sessionRejections,
                fullResyncRejections,
                epochRejections,
                sequenceRejections,
                invalidFrameRejections);
    }

    private boolean validFrame(Event event) {
        if (isBlank(event.connectionEpoch())
                || isBlank(event.sessionEpoch())
                || isBlank(event.backendId())
                || isBlank(event.transportId())
                || isBlank(event.correlationId())
                || event.terminalSequence() < 0
                || event.frameSequence() < 0
                || event.maxFrameBytes() <= 0
                || event.maxFrameBytes() > configuredMaxFrameBytes
                || event.frame() == null
                || !event.sessionId().equals(event.snapshotSessionId())
                || event.snapshotSequence() != event.terminalSequence()
                || !event.snapshotComplete()
                || event.frame().sequence() != event.frameSequence()
                || !event.frame().full()
                || !event.frame().png()) {
            return false;
        }
        byte[] payload = event.frame().payload();
        return payload.length > 0
                && payload.length <= event.maxFrameBytes()
                && isPng(payload);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPng(byte[] payload) {
        return payload != null
                && payload.length >= 8
                && payload[0] == (byte) 0x89
                && payload[1] == 0x50
                && payload[2] == 0x4E
                && payload[3] == 0x47
                && payload[4] == 0x0D
                && payload[5] == 0x0A
                && payload[6] == 0x1A
                && payload[7] == 0x0A;
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }
}
