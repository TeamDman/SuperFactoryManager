package ca.teamdman.sfm.gametest;

import java.util.function.Consumer;

/** Implemented only by SFM's extended/dev GameTestInfo mixin. */
public interface SFMGameTestIntegrityAccess {
    void sfm$installIntegrityCheck(Consumer<String> check);
}
