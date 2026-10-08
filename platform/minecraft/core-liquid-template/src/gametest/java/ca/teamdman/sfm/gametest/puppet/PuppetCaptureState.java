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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    public long frameBeforePreparation;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

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
