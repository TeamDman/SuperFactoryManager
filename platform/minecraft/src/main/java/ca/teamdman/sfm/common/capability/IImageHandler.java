package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.resourcetype.SFMImageStack;

/** A single-slot, zero-or-one image resource capability. */
public interface IImageHandler {
    SFMImageStack getImage();

    /** Returns the image not accepted; an empty stack means complete insertion. */
    SFMImageStack insertImage(SFMImageStack image, boolean simulate);

    /** Returns the extracted image, or an empty stack. */
    SFMImageStack extractImage(boolean simulate);

    default boolean canInsert() {
        return true;
    }

    default boolean canExtract() {
        return true;
    }
}
