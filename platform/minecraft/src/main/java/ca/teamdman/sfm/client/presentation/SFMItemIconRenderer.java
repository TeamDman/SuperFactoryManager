package ca.teamdman.sfm.client.presentation;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

/** Shared fixed-size renderer for file and action icons. */
public final class SFMItemIconRenderer {
    public static final int SIZE = 16;

    private SFMItemIconRenderer() {
    }

    @MCVersionDependentBehaviour
    public static SFMResolvedItemIcon render(PoseStack poseStack, Minecraft minecraft, SFMItemIcon icon, int x, int y) {
        SFMResolvedItemIcon resolved = SFMItemIconResolver.resolve(icon);
        minecraft.getItemRenderer().renderAndDecorateItem(poseStack, resolved.stack(), x, y);
        return resolved;
    }
}
