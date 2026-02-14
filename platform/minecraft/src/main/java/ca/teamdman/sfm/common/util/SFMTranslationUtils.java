package ca.teamdman.sfm.common.util;

import java.util.stream.StreamSupport;

import com.bbscn.Tools;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import org.jetbrains.annotations.NotNull;

import io.netty.buffer.ByteBuf;

public class SFMTranslationUtils {
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
        ByteBufUtils.writeVarInt(buf, contents.getFormatArgs().length, 5);

        for (var arg : contents.getFormatArgs()) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(arg));
        }
    }

    @NotNull
    public static TextComponentTranslation decodeTranslation(ByteBuf buf) {
        String key = ByteBufUtils.readUTF8String(buf);
        int argCount = ByteBufUtils.readVarInt(buf, 5);
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
    public static TextComponentTranslationHashable getTextComponentTranslation(
            String key,
            Object... args
    ) {
            return new TextComponentTranslationHashable(key, args);
//        Object[] newArgs = new Object[args.length];
//        for (int i = 0; i < args.length; i++) {
//            Object arg = args[i];
//            if (arg instanceof Number || arg instanceof Boolean || arg instanceof String) {
//                newArgs[i] = arg;
//            } else if (arg == null) {
//                newArgs[i] = "null";
//            } else {
////                SFM.LOGGER.warn(
////                        "Invalid argument type for translation argument {} key '{}': {}",
////                        i,
////                        key,
////                        arg.getClass().getName(),
////                        new IllegalArgumentException()
////                );
//                newArgs[i] = arg.toString();
//            }
//        }
//        TextComponentTranslation iTextComponents = new TextComponentTranslation(key, newArgs);
//        Tools.defaultize(iTextComponents);
//        return iTextComponents;
    }

    /**
     * Helper method to avoid noisy git merges between versions
     */
    public static TextComponentTranslationHashable getTextComponentTranslation(String key) {
        return getTextComponentTranslation(key, new Object[]{});
    }
}
