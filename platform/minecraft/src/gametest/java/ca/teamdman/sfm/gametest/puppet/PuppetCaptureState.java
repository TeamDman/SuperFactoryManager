package ca.teamdman.sfm.gametest.puppet;

import net.minecraft.network.chat.Component;

import java.io.File;

public final class PuppetCaptureState {
    public final String captureName;

    public final File file;

    public final Component caption;

    public final int figureNumber;

    public int ticks;

    public boolean hudPrepared;
    public long frameBeforePreparation;

    public boolean requested;

    public volatile Throwable captureFailure;

    PuppetCaptureState(
            String captureName,
            File file,
            Component caption,
            int figureNumber
    ) {

        this.captureName = captureName;
        this.file = file;
        this.caption = caption;
        this.figureNumber = figureNumber;
    }

}
