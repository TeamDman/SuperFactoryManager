package ca.teamdman.sfm.common.util;

/** Spelling adapters at native-process boundaries; performs no filesystem access. */
public final class SFMNativePaths {
    private SFMNativePaths() { }

    /** Java NIO rejects Rust's Windows extended-length spelling of drive/UNC paths. */
    public static String ordinaryWindowsPath(String value) {
        String extendedUnc = "\\\\?\\UNC\\";
        if (value.regionMatches(true, 0, extendedUnc, 0, extendedUnc.length())) {
            return "\\\\" + value.substring(extendedUnc.length());
        }
        String extended = "\\\\?\\";
        if (value.startsWith(extended)) return value.substring(extended.length());
        return value;
    }
}
