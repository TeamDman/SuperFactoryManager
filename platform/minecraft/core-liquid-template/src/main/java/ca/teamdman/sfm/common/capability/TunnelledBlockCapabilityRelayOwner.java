package ca.teamdman.sfm.common.capability;

/** Keeps position-scoped capability listeners alive with their tunnel block entity. */
public interface TunnelledBlockCapabilityRelayOwner {
    TunnelledBlockCapabilityRelay sfm$getCapabilityRelay();
}
