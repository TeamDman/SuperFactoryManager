package ca.teamdman.sfm.common.tutorial.chamber;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class SFMTutorialTestChamberDefinition {
    public abstract void run(SFMTutorialTestChamberHelper helper);

    public @Nullable ResourceLocation nextChamberId() {
        return null;
    }
}