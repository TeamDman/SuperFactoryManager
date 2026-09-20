package ca.teamdman.sfm.client.program.signing;

import java.util.Arrays;

/** No String conversion, history, clipboard or narration surface for a typed passphrase. */
public final class ClientSigningSecretBuffer implements AutoCloseable {
    private final char[] value = new char[1024];
    private int length;

    public int length() { return length; }
    public boolean usable() { return length >= 12; }
    public boolean append(char character) {
        if (Character.isISOControl(character) || character == '\u00a7' || length == value.length) return false;
        value[length++] = character;
        return true;
    }
    public void backspace() { if (length > 0) value[--length] = '\0'; }
    public boolean matches(ClientSigningSecretBuffer other) {
        int mismatch = length ^ other.length;
        for (int index = 0; index < value.length; index++) mismatch |= value[index] ^ other.value[index];
        return mismatch == 0;
    }
    /** Ownership transfers to the caller, which must clear the returned array. */
    public char[] consume() {
        char[] result = Arrays.copyOf(value, length);
        close();
        return result;
    }
    @Override public void close() { Arrays.fill(value, '\0'); length = 0; }
    @Override public String toString() { return "[hidden passphrase]"; }
}
