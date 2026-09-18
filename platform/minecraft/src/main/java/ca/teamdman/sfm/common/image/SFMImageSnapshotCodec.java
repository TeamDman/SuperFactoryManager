package ca.teamdman.sfm.common.image;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/** Versioned, fail-closed persistence for a complete bounded PNG snapshot. */
public final class SFMImageSnapshotCodec {
    public static final int VERSION = 1;

    private static final String VERSION_TAG = "version";
    private static final String WIDTH_TAG = "width";
    private static final String HEIGHT_TAG = "height";
    private static final String SHA256_TAG = "sha256";
    private static final String PNG_TAG = "png";

    private SFMImageSnapshotCodec() {}

    public static CompoundTag toTag(SFMImageSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, VERSION);
        tag.putInt(WIDTH_TAG, snapshot.width());
        tag.putInt(HEIGHT_TAG, snapshot.height());
        tag.putString(SHA256_TAG, snapshot.sha256());
        tag.putByteArray(PNG_TAG, snapshot.pngBytes());
        return tag;
    }

    /** Missing, unsupported or corrupt persisted data yields no image. */
    public static Optional<SFMImageSnapshot> fromTag(CompoundTag tag) {
        if (tag == null
                || !tag.contains(VERSION_TAG, Tag.TAG_INT)
                || tag.getInt(VERSION_TAG) != VERSION
                || !tag.contains(WIDTH_TAG, Tag.TAG_INT)
                || !tag.contains(HEIGHT_TAG, Tag.TAG_INT)
                || !tag.contains(SHA256_TAG, Tag.TAG_STRING)
                || !tag.contains(PNG_TAG, Tag.TAG_BYTE_ARRAY)) {
            return Optional.empty();
        }
        byte[] bytes = tag.getByteArray(PNG_TAG);
        if (bytes.length > SFMImageSnapshot.MAX_ENCODED_BYTES) {
            return Optional.empty();
        }
        try {
            SFMImageSnapshot snapshot = SFMImageSnapshot.fromPng(bytes);
            if (snapshot.width() != tag.getInt(WIDTH_TAG)
                    || snapshot.height() != tag.getInt(HEIGHT_TAG)
                    || !snapshot.sha256().equals(tag.getString(SHA256_TAG))) {
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
