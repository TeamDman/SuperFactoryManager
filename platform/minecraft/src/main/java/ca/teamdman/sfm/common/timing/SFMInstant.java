package ca.teamdman.sfm.common.timing;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/// {@link Instant#now()} gives less-precise wall-clock time; we care about elapsed CPU time.
/// Using {@link SFMInstant} ensures a high-fidelity standard measurement for SFM performance monitoring.
/// Using a single {@code long nanos} field gives us ≈ 292 years to work with.
public record SFMInstant(long nanos) implements Comparable<SFMInstant> {

    /// Get a high-precision moment in time.
    public static SFMInstant now() {

        return new SFMInstant(nanoNow());
    }

    public static SFMInstant earliest() {

        return new SFMInstant(Long.MIN_VALUE);
    }

    /// Uses {@link System#nanoTime()} for most accurate timestamping
    public static long nanoNow() {
        /// I tried using {@link java.lang.management.ThreadMXBean#getThreadCpuTime(long)} but it wasn't as good as {@link System#nanoTime()}.
        /// [ThreadMXBean recommendation](https://stackoverflow.com/a/7467299/11141271)
        /// [Best approach for dealing with time measures?](https://stackoverflow.com/questions/37067929/best-approach-for-dealing-with-time-measures)
        return System.nanoTime();
//        return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
    }

    /// Returns the time elapsed from {@link SFMInstant#now()} to {@code this} instant
    public Duration elapsed() {

        return Duration.ofNanos(nanoNow() - nanos);
    }


    @Override
    public String toString() {

        return "SFMInstant{" +
               "nanos=" + nanos +
               ", elapsed=" + elapsed() +
               '}';
    }

    @Override
    public int compareTo(@NotNull SFMInstant o) {

        return Long.compare(nanos, o.nanos);
    }

}
