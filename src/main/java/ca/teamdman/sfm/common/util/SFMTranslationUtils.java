package ca.teamdman.sfm.common.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.jetbrains.annotations.NotNull;

import java.util.stream.StreamSupport;

public class SFMTranslationUtils {
    public static final int MAX_TRANSLATION_ELEMENT_LENGTH = 10240;

    public static TextComponentTranslation deserializeTranslation(NBTTagCompound tag) {
        var key = tag.getString("key");
        var args = StreamSupport
                .stream(
                        tag.getTagList("args", Constants.NBT.TAG_STRING).spliterator(),
                        false
                )
                .map(NBTTagString.class::cast)
                .map(NBTTagString::getString)
                .toArray();
        return getTextComponentTranslation(key, args);
    }

    @NotNull
    public static NBTTagCompound serializeTranslation(TextComponentTranslation contents) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("key", contents.getKey());
        NBTTagList args = new NBTTagList();
        for (var arg : contents.getFormatArgs()) {
            args.appendTag(new NBTTagString(arg.toString()));
        }
        tag.setTag("args", args);
        return tag;
    }

    public static void encodeTranslation(
            TextComponentTranslation contents,
            ByteBuf buf
    ) {
        ByteBufUtils.writeUTF8String(buf, contents.getKey());
        ByteBufUtils.writeVarInt(buf, contents.getFormatArgs().length, MAX_TRANSLATION_ELEMENT_LENGTH);

        for (var arg : contents.getFormatArgs()) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(arg));
        }
    }

    @NotNull
    public static TextComponentTranslation decodeTranslation(ByteBuf buf) {
        String key = ByteBufUtils.readUTF8String(buf);
        int argCount = ByteBufUtils.readVarInt(buf, MAX_TRANSLATION_ELEMENT_LENGTH);
        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = ByteBufUtils.readUTF8String(buf);
        }
        return getTextComponentTranslation(key, args);
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    @MCVersionDependentBehaviour
    public static TextComponentTranslation getTextComponentTranslation(
            String key,
            Object... args
    ) {
        return new TextComponentTranslation(key, null, args);
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    public static TextComponentTranslation getTextComponentTranslation(String key) {
        return getTextComponentTranslation(key, new Object[]{});
    }
}
