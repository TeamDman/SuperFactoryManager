package ca.teamdman.sfm.common.logging;

import ca.teamdman.sfm.common.net.FriendlyByteBuf;
import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import com.github.bsideup.jabel.Desugar;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.apache.logging.log4j.Level;

@Desugar public record TranslatableLogEvent(
        Level level,
        SFMEpochInstant instant,
        TextComponentTranslation contents
) {
    public void encode(FriendlyByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, level.name());
        instant.write(buf);
        SFMTranslationUtils.encodeTranslation(contents, buf);
    }

    public static TranslatableLogEvent decode(FriendlyByteBuf buf) {
        var level = Level.getLevel(ByteBufUtils.readUTF8String(buf));
        SFMEpochInstant instant = SFMEpochInstant.read(buf);
        var contents = SFMTranslationUtils.decodeTranslation(buf);

        return new TranslatableLogEvent(level, instant, contents);
    }
}
