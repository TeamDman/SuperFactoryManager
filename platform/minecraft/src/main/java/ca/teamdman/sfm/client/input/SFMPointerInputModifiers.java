package ca.teamdman.sfm.client.input;

import java.util.OptionalInt;
import java.util.function.Supplier;

/** Modifier mask from the raw pointer callback, including virtual inputs (no OS-key polling). */
public final class SFMPointerInputModifiers {
    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();
    private SFMPointerInputModifiers() { }
    public static void begin(int modifiers) { CURRENT.set(modifiers); }
    public static void end() { CURRENT.remove(); }
    public static OptionalInt current() {
        Integer value = CURRENT.get();
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
    public static boolean isDown(int mask, java.util.function.BooleanSupplier physicalFallback) {
        Integer value = CURRENT.get();
        return value == null ? physicalFallback.getAsBoolean() : (value & mask) != 0;
    }
    public static <T> T during(int modifiers, Supplier<T> operation) {
        Integer previous = CURRENT.get();
        begin(modifiers);
        try { return operation.get(); }
        finally { if (previous == null) end(); else begin(previous); }
    }
}
