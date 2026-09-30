package ca.teamdman.sfm.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.nbt.Tag;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;

public class SFMTranslationUtils {
    public static final int MAX_TRANSLATION_ELEMENT_LENGTH = 10240;

    public static TranslatableContents deserializeTranslation(CompoundTag tag) {
        var key = tag.getString("key");
        var args = tag
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .getList("args", Tag.TAG_STRING)
{% when '26.1.2' %}
                .getList("args")
{% endcase %}
                .stream()
                .map(StringTag.class::cast)
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .map(StringTag::getAsString)
{% when '26.1.2' %}
                .map(StringTag::asString)
{% endcase %}
                .toArray();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return getTranslatableContents(key, args);
{% when '26.1.2' %}
        return getTranslatableContents(key.get(), args);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2' %}
        return new TranslatableContents(key, args);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return new TranslatableContents(key, null, args);
{% when '1.21', '1.21.1', '26.1.2' %}
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (TranslatableContents.isAllowedPrimitiveArgument(arg)) {
                newArgs[i] = arg;
            } else if (arg == null) {
                newArgs[i] = "null";
            } else {
//                SFM.LOGGER.warn(
//                        "Invalid argument type for translation argument {} key '{}': {}",
//                        i,
//                        key,
//                        arg.getClass().getName(),
//                        new IllegalArgumentException()
//                );
                newArgs[i] = arg.toString();
            }
        }
        return new TranslatableContents(key, null, newArgs);
{% endcase %}
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    public static TranslatableContents getTranslatableContents(String key) {
        return getTranslatableContents(key, new Object[]{});
    }
}
