package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.downloads.DownloadManager;
import net.neoforged.neoform.runtime.downloads.DownloadSpec;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Staged direct-engine adapter component. Not yet wired into production. */
public final class SFMClosedDownloadManager extends DownloadManager {
    private static final String REFUSAL = "Named NeoForm inputs must already be checksum-verified snapshots";

    @Override
    public void download(URI uri, Path destination) throws IOException {
        throw new IOException(REFUSAL);
    }

    @Override
    public boolean download(DownloadSpec spec, Path destination) throws IOException {
        throw new IOException(REFUSAL);
    }

    @Override
    public boolean download(DownloadSpec spec, Path destination, boolean silent) throws IOException {
        throw new IOException(REFUSAL);
    }

    /** Negative API regression only; does not run NeoForm or its child tools. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Supply one nonexistent destination for the negative proof");
        }
        Path destination = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("Proof refuses an existing destination");
        }
        URI uri = URI.create("https://invalid.invalid/must-not-be-requested.jar");
        DownloadSpec spec = DownloadSpec.of(uri);
        // Use the upstream static type: each public overload must dispatch to
        // the refusal, including both values of the silent parameter.
        try (DownloadManager downloader = new SFMClosedDownloadManager()) {
            expectRefusal(() -> downloader.download(uri, destination));
            expectRefusal(() -> downloader.download(spec, destination));
            expectRefusal(() -> downloader.download(spec, destination, false));
            expectRefusal(() -> downloader.download(spec, destination, true));
        }
        if (Files.exists(destination)) {
            throw new AssertionError("Refused download created an output");
        }
        System.out.println("PASS: all four download calls refused; no destination created; no engine or child executed");
    }

    @FunctionalInterface
    private interface Attempt {
        void run() throws IOException;
    }

    private static void expectRefusal(Attempt attempt) throws IOException {
        try {
            attempt.run();
        } catch (IOException error) {
            if (!REFUSAL.equals(error.getMessage())) {
                throw new AssertionError("Unexpected failure rather than the adapter refusal", error);
            }
            return;
        }
        throw new AssertionError("Download overload did not refuse");
    }
}
