package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.SFMExternalCliPuppetProcess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared source-matched CLI and PowerShell framing for packet terminal puppets. */
final class SFMPacketTerminalCommandSupport {
    private SFMPacketTerminalCommandSupport() {
    }

    static Path controlCliExecutable() {
        String configured = System.getProperty(SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            throw new IllegalStateException(
                    "System property " + SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY
                            + " must identify the checkout-local sfm.exe"
            );
        }
        Path executable = configuredExecutable(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("Checkout-local SFM CLI does not exist: " + executable);
        }
        try {
            return executable.toRealPath();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not resolve checkout-local SFM CLI " + executable, failure);
        }
    }

    /** Converts Rust's Windows extended-length canonical form into Java NIO's accepted form. */
    static Path configuredExecutable(String configured) {
        if (configured.startsWith("\\\\?\\UNC\\")) {
            return Path.of("\\\\" + configured.substring("\\\\?\\UNC\\".length()));
        }
        if (configured.startsWith("\\\\?\\")) {
            return Path.of(configured.substring("\\\\?\\".length()));
        }
        return Path.of(configured);
    }

    static String powerShellLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
