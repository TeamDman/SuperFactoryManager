package ca.teamdman.sfm.client.program.signing;

import java.util.Objects;
import java.util.UUID;

/** Cosmetic acknowledgement and delayed accessible alternative; never cryptographic evidence. */
public final class ClientProgramSigningCeremony {
    public static final int REVIEW_DELAY_TICKS = 40;
    private UUID challenge;
    private String fingerprint;
    private int remaining;
    private boolean acknowledged;

    /** Returns false when a previous ceremony was invalidated or replaced. */
    public boolean bind(UUID nextChallenge, String nextFingerprint) {
        if (nextChallenge == null || nextFingerprint == null) { clear(); return false; }
        if (!nextChallenge.equals(challenge) || !Objects.equals(nextFingerprint, fingerprint)) {
            challenge = nextChallenge;
            fingerprint = nextFingerprint;
            remaining = REVIEW_DELAY_TICKS;
            acknowledged = false;
            return false;
        }
        return true;
    }
    public void tick() { if (remaining > 0) remaining--; }
    public void acknowledge() { if (challenge != null && fingerprint != null) acknowledged = true; }
    public boolean ready() { return challenge != null && acknowledged && remaining == 0; }
    public boolean acknowledged() { return acknowledged; }
    public int remainingTicks() { return remaining; }
    public void clear() { challenge = null; fingerprint = null; remaining = 0; acknowledged = false; }
}
