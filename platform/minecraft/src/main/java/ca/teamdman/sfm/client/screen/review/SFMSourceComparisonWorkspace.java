package ca.teamdman.sfm.client.screen.review;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;

public final class SFMSourceComparisonWorkspace {
    private SFMSourceComparisonWorkspace() {}

    public static Screen create(Screen previousScreen) {
        Path ledger = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("sfm-source-review-ledger.json");
        return create(previousScreen, ledger);
    }

    static Screen create(Screen previousScreen, Path ledgerPath) {
        return SFMScreenMultiplexer.create(previousScreen,
                new SFMSourceComparisonPanel(SFMSourceComparisonFixtures.reviewWalkthrough(), SFMReviewLedger.open(ledgerPath)));
    }
}
