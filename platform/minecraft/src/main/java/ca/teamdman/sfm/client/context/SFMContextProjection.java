package ca.teamdman.sfm.client.context;

/** Immutable provider-neutral payload contributed by one context origin. */
public sealed interface SFMContextProjection permits
        SFMContextDocumentProjection,
        SFMContextPathProjection,
        SFMContextSpatialProjection {
}
