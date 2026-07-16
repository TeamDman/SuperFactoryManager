package ca.teamdman.sfm.gametest.puppet;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public record PuppetCaptureFigureCaptionLayout(
        List<FormattedCharSequence> lines,

        double guiScale,

        int pixelHeight
) {
    public static PuppetCaptureFigureCaptionLayout create(
            Minecraft minecraft,
            PuppetCaptureState state
    ) {

        double guiScale = minecraft.getWindow().getGuiScale();
        int captionWidth = Math.max(
                1,
                (int) (minecraft.getMainRenderTarget().width / guiScale)
                - SFMGamePuppetHarness.CAPTION_HORIZONTAL_PADDING * 2
        );
        Component numberedCaption = Component.literal("Figure " + state.figureNumber + ": ")
                .withStyle(ChatFormatting.BLACK)
                .append(state.caption.copy());
        List<FormattedCharSequence> lines = minecraft.font.split(numberedCaption, captionWidth);
        int pixelHeight = Mth.ceil(
                (SFMGamePuppetHarness.CAPTION_VERTICAL_PADDING * 2 + lines.size() * minecraft.font.lineHeight)
                * guiScale
        );
        return new PuppetCaptureFigureCaptionLayout(lines, guiScale, pixelHeight);
    }

}
