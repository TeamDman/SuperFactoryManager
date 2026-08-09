package ca.teamdman.sfm.common.timing;

import net.minecraft.network.FriendlyByteBuf;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/// Used for communicating log timing information between clients and the server.
/// Note that {@link SFMInstant} is a local monotonic time and therefore not network safe.
public record SFMEpochInstant(Instant instant) implements Comparable<SFMEpochInstant> {
    public static SFMEpochInstant zero() {

        return new SFMEpochInstant(new MutableInstant());
    }

    @Override
    public int compareTo(@NotNull SFMEpochInstant o) {

        long millisDiff = this.instant.getEpochMillisecond() - o.instant.getEpochMillisecond();
        if (millisDiff != 0) {
            return Long.signum(millisDiff);
        }
        return Integer.compare(this.instant.getNanoOfMillisecond(), o.instant.getNanoOfMillisecond());
    }

    public void write(FriendlyByteBuf buf) {

        buf.writeLong(instant.getEpochMillisecond());
        buf.writeInt(instant.getNanoOfMillisecond());
    }

    public static SFMEpochInstant read(FriendlyByteBuf buf) {

        var epochMillisecond = buf.readLong();
        var epochNano = buf.readInt();
        var instant = new MutableInstant();
        instant.initFromEpochMilli(epochMillisecond, epochNano);
        return new SFMEpochInstant(instant);
    }

    public static SFMEpochInstant now() {
        MutableInstant instant = new MutableInstant();
        instant.initFromEpochMilli(System.currentTimeMillis(), 0);
        return new SFMEpochInstant(instant);
    }

    public Duration elapsed() {
        SFMEpochInstant now = now();
        long millis = now.instant.getEpochMillisecond() - this.instant.getEpochMillisecond();
        long nanosOfMillisecond = now.instant.getNanoOfMillisecond() - this.instant.getNanoOfMillisecond();
        return Duration.ofNanos(millis * 1_000_000L + nanosOfMillisecond);
    }

}
