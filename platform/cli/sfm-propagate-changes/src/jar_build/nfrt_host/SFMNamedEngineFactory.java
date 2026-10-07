package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.cli.FileHashService;
import net.neoforged.neoform.runtime.cli.Main;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;

import java.io.IOException;

/**
 * Staged direct-engine construction, not a runnable native build entry point.
 * The caller still owes the verified Rust export, child-tool policy and fresh
 * producer-output integration before loading or running any graph. Construction
 * avoids NeoFormEngineCommand.call(), which instantiates its own downloader.
 */
public final class SFMNamedEngineFactory {
    private SFMNamedEngineFactory() {
    }

    public static NeoFormEngine createUnstartedEngine(
            SFMNamedNeoFormLauncher.LaunchContract contract
    ) throws IOException {
        Main adapter = SFMNamedNeoFormLauncher.adapterForReviewedContract(contract);
        var cache = adapter.createCacheManager();
        var locks = adapter.createLockManager();
        var installations = adapter.createLauncherInstallations();
        var downloader = new SFMClosedDownloadManager();
        try {
            var artifacts = adapter.createArtifactManager(cache, downloader, locks, installations);
            var engine = new NeoFormEngine(artifacts, new FileHashService(), cache, locks);
            // Engine closure also closes the downloader. Neither cache nor lock
            // maintenance is invoked; the adapter keeps cache restore disabled.
            engine.addManagedResource(downloader);
            return engine;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                downloader.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
