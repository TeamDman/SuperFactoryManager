package ca.teamdman.sfm.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;

public class SFMTranslationUtils {
    public static final int MAX_TRANSLATION_ELEMENT_LENGTH = 10240;

    public static TranslatableContents deserializeTranslation(CompoundTag tag) {
        var key = tag.getString("key");
        var args = tag
                .getList("args", Tag.TAG_STRING)
                .stream()
                .map(StringTag.class::cast)
                .map(StringTag::getAsString)
                .toArray();
        return getTranslatableContents(key, args);
    }

    public static CompoundTag serializeTranslation(TranslatableContents contents) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", contents.getKey());
        ListTag args = new ListTag();
        for (var arg : contents.getArgs()) {
            args.add(StringTag.valueOf(arg.toString()));
        }
        tag.put("args", args);
        return tag;
    }

    public static void encodeTranslation(
            TranslatableContents contents,
            FriendlyByteBuf buf
    ) {
        buf.writeUtf(contents.getKey(), MAX_TRANSLATION_ELEMENT_LENGTH);
        buf.writeVarInt(contents.getArgs().length);
        for (var arg : contents.getArgs()) {
            buf.writeUtf(String.valueOf(arg), MAX_TRANSLATION_ELEMENT_LENGTH);
        }
    }

    public static TranslatableContents decodeTranslation(FriendlyByteBuf buf) {
        String key = buf.readUtf(MAX_TRANSLATION_ELEMENT_LENGTH);
        int argCount = buf.readVarInt();
        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = buf.readUtf(MAX_TRANSLATION_ELEMENT_LENGTH);
        }
        return getTranslatableContents(key, args);
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    @MCVersionDependentBehaviour
    public static TranslatableContents getTranslatableContents(
            String key,
            Object... args
    ) {
        return new TranslatableContents(key, null, args);
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    public static TranslatableContents getTranslatableContents(String key) {
        return getTranslatableContents(key, new Object[]{});
    }
}
