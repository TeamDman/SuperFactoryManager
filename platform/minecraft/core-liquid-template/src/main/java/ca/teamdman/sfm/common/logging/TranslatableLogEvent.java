package ca.teamdman.sfm.common.logging;

import ca.teamdman.sfm.common.timing.SFMEpochInstant;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.apache.logging.log4j.Level;

public record TranslatableLogEvent(
        Level level,
        SFMEpochInstant instant,
        TranslatableContents contents
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(level.name());
        instant.write(buf);
        SFMTranslationUtils.encodeTranslation(contents, buf);
    }
    public static TranslatableLogEvent decode(FriendlyByteBuf buf) {
        var level = Level.getLevel(buf.readUtf());
        SFMEpochInstant instant = SFMEpochInstant.read(buf);
        var contents = SFMTranslationUtils.decodeTranslation(buf);

        return new TranslatableLogEvent(level, instant, contents);
    }
}
