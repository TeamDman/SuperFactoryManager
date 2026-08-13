package ca.teamdman.sfm.client.explorer;

/**
 * Marker for a stable, domain-specific selector identity.
 *
 * <p>Implementations remain distinct Java types so an explorer id cannot be
 * passed to a selection repository by accident.</p>
 */
public interface SFMStableEntityId {
    String value();
}
