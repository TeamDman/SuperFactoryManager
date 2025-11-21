package ca.teamdman.sfm.common.logging;

import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import io.netty.buffer.ByteBuf;

@Desugar
public record TranslatableLogEvent(
                                   Level level,
                                   Instant instant,
                                   TextComponentTranslation contents) {

    public void encode(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, level.name());
        buf.writeLong(instant.getEpochMillisecond());
        buf.writeInt(instant.getNanoOfMillisecond());
        SFMTranslationUtils.encodeTranslation(contents, buf);
    }

    public static TranslatableLogEvent decode(ByteBuf buf) {
        var level = Level.getLevel(ByteBufUtils.readUTF8String(buf));
        var epochMillisecond = buf.readLong();
        var epochNano = buf.readInt();
        var contents = SFMTranslationUtils.decodeTranslation(buf);

        var instant = new MutableInstant();
        instant.initFromEpochMilli(epochMillisecond, epochNano);

        return new TranslatableLogEvent(level, instant, contents);
    }
}
