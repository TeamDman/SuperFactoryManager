package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.file.Files;

/**
 * 26.1.2 caption adapter.
 *
 * <p>The extracted GUI renderer no longer exposes the immediate off-screen GUI
 * path used by older versions. This adapter extends the public screenshot
 * readback and rasterizes Minecraft's bundled bitmap font into the new caption
 * area, without modifying the live render state.</p>
 */
final class PuppetCaptionedScreenshotComposer {
    private static final Identifier ASCII_FONT = Identifier.withDefaultNamespace("textures/font/ascii.png");
    private static final int GLYPH_GRID_SIZE = 16;
    private static final int SPACE_ADVANCE = 4;

    private PuppetCaptionedScreenshotComposer() {
    }

    @MCVersionDependentBehaviour
    static void write(
            Minecraft minecraft,
            ActivePuppet active,
            PuppetCaptureState state
    ) {

        PuppetCaptureFigureCaptionLayout layout = PuppetCaptureFigureCaptionLayout.create(minecraft, state);
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), source -> {
            try (source; NativeImage ascii = loadAsciiFont(minecraft)) {
                NativeImage output = compose(source, ascii, layout);
                Util.ioPool().execute(() -> writeAsync(output, active, state));
            } catch (Throwable failure) {
                recordFailure(active, state, failure);
            }
        });
    }

    @MCVersionDependentBehaviour
    private static NativeImage compose(
            NativeImage source,
            NativeImage ascii,
            PuppetCaptureFigureCaptionLayout layout
    ) {

        NativeImage output = new NativeImage(source.getWidth(), source.getHeight() + layout.pixelHeight(), false);
        output.fillRect(0, 0, output.getWidth(), output.getHeight(), 0xFFFFFFFF);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                output.setPixel(x, y + layout.pixelHeight(), source.getPixel(x, y));
            }
        }

        int y = SFMGamePuppetHarness.CAPTION_VERTICAL_PADDING;
        for (FormattedCharSequence line : layout.lines()) {
            drawLine(output, ascii, line, y, layout.guiScale());
            y += Minecraft.getInstance().font.lineHeight;
        }
        return output;
    }

    @MCVersionDependentBehaviour
    private static NativeImage loadAsciiFont(Minecraft minecraft) throws Exception {

        try (InputStream stream = minecraft.getResourceManager().open(ASCII_FONT)) {
            return NativeImage.read(NativeImage.Format.RGBA, stream);
        }
    }

    @MCVersionDependentBehaviour
    private static void drawLine(
            NativeImage output,
            NativeImage ascii,
            FormattedCharSequence line,
            int y,
            double guiScale
    ) {

        int[] cursor = {SFMGamePuppetHarness.CAPTION_HORIZONTAL_PADDING};
        line.accept((position, style, codepoint) -> {
            int glyph = codepoint >= 32 && codepoint <= 255 ? codepoint : '?';
            int color = color(style);
            drawGlyph(output, ascii, glyph, cursor[0], y, guiScale, color);
            if (style.isBold()) {
                drawGlyph(output, ascii, glyph, cursor[0] + 1, y, guiScale, color);
            }
            cursor[0] += glyphAdvance(ascii, glyph);
            return true;
        });
    }

    @MCVersionDependentBehaviour
    private static int glyphAdvance(NativeImage ascii, int codepoint) {

        // The blank ASCII glyph has no lit pixels, but Minecraft gives it a four-pixel advance.
        if (codepoint == ' ') {
            return SPACE_ADVANCE;
        }
        int glyphWidth = ascii.getWidth() / GLYPH_GRID_SIZE;
        int glyphHeight = ascii.getHeight() / GLYPH_GRID_SIZE;
        int startX = (codepoint % GLYPH_GRID_SIZE) * glyphWidth;
        int startY = (codepoint / GLYPH_GRID_SIZE) * glyphHeight;
        for (int x = glyphWidth - 1; x >= 0; x--) {
            for (int y = 0; y < glyphHeight; y++) {
                if (ascii.getLuminanceOrAlpha(startX + x, startY + y) != 0) {
                    return x + 2;
                }
            }
        }
        return 1;
    }

    @MCVersionDependentBehaviour
    private static void drawGlyph(
            NativeImage output,
            NativeImage ascii,
            int codepoint,
            int x,
            int y,
            double guiScale,
            int color
    ) {

        int glyphWidth = ascii.getWidth() / GLYPH_GRID_SIZE;
        int glyphHeight = ascii.getHeight() / GLYPH_GRID_SIZE;
        int startX = (codepoint % GLYPH_GRID_SIZE) * glyphWidth;
        int startY = (codepoint / GLYPH_GRID_SIZE) * glyphHeight;
        for (int glyphY = 0; glyphY < glyphHeight; glyphY++) {
            for (int glyphX = 0; glyphX < glyphWidth; glyphX++) {
                if (ascii.getLuminanceOrAlpha(startX + glyphX, startY + glyphY) != 0) {
                    fillScaledPixel(output, x + glyphX, y + glyphY, guiScale, color);
                }
            }
        }
    }

    @MCVersionDependentBehaviour
    private static void fillScaledPixel(NativeImage output, int guiX, int guiY, double guiScale, int color) {

        int x0 = (int) Math.floor(guiX * guiScale);
        int y0 = (int) Math.floor(guiY * guiScale);
        int x1 = (int) Math.ceil((guiX + 1) * guiScale);
        int y1 = (int) Math.ceil((guiY + 1) * guiScale);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                output.setPixel(x, y, color);
            }
        }
    }

    @MCVersionDependentBehaviour
    private static int color(Style style) {

        return 0xFF000000 | (style.getColor() == null ? 0 : style.getColor().getValue());
    }

    private static void writeAsync(NativeImage output, ActivePuppet active, PuppetCaptureState state) {

        try (output) {
            Files.createDirectories(state.file.toPath().toAbsolutePath().getParent());
            output.writeToFile(state.file);
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_CAPTURE_MESSAGE puppet={} capture={} message={}",
                    active.definition.puppetName(),
                    state.captureName,
                    state.file.getName()
            );
        } catch (Throwable failure) {
            recordFailure(active, state, failure);
        }
    }

    private static void recordFailure(ActivePuppet active, PuppetCaptureState state, Throwable failure) {

        state.captureFailure = failure;
        SFM.LOGGER.error(
                "SFM_GAME_PUPPET_CAPTURE_FAILED puppet={} capture={}",
                active.definition.puppetName(),
                state.captureName,
                failure
        );
    }
}
