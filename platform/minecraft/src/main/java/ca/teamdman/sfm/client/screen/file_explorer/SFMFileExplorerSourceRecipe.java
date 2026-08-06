package ca.teamdman.sfm.client.screen.file_explorer;

import java.nio.file.Path;
import java.util.Optional;

/** Immutable typed address for reconstructing a supported explorer source. */
public sealed interface SFMFileExplorerSourceRecipe
        permits SFMFileExplorerSourceRecipe.Fixture, SFMFileExplorerSourceRecipe.PathRoot {
    SFMFileExplorerSource open();

    static Optional<SFMFileExplorerSourceRecipe> from(SFMFileExplorerSource source) {
        if (source instanceof SFMFileExplorerFixtureSource) return Optional.of(new Fixture());
        if (source instanceof SFMPathFileExplorerSource pathSource) {
            return Optional.of(new PathRoot(pathSource.root()));
        }
        return Optional.empty();
    }

    record Fixture() implements SFMFileExplorerSourceRecipe {
        @Override
        public SFMFileExplorerSource open() {
            return new SFMFileExplorerFixtureSource();
        }
    }

    record PathRoot(Path root) implements SFMFileExplorerSourceRecipe {
        public PathRoot {
            root = root.toAbsolutePath().normalize();
        }

        @Override
        public SFMFileExplorerSource open() {
            return new SFMPathFileExplorerSource(root);
        }
    }
}
