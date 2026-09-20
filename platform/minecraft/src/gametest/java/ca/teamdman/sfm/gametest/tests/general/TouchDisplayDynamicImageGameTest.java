package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.render.TouchDisplayBlockEntityRenderer;
import ca.teamdman.sfm.client.render.TouchDisplayTextureRuntime;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.resourcetype.SFMImageStack;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Ambient client proof of server image sync and dynamic texture registration. */
@SFMGameTest(SFMDist.CLIENT)
public final class TouchDisplayDynamicImageGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x3x1";
    }

    @Override
    public int maxTicks() {
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 2, 0);
        helper.setBlock(relativePos, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                .setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        SFMImageSnapshot image = fixture();
        SFMValue state = SFMValue.object(Map.of("fixture", SFMValue.of("dynamic")));
        TouchDisplayBlockEntity serverDisplay = helper.getBlockEntity(relativePos, TouchDisplayBlockEntity.class);
        serverDisplay.commitContent(SFMImageStack.of(image, state));

        BlockPos absolutePos = helper.absolutePos(relativePos);
        Minecraft minecraft = Minecraft.getInstance();
        AtomicBoolean probeScheduled = new AtomicBoolean();
        AtomicBoolean observed = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            if (!observed.get() && probeScheduled.compareAndSet(false, true)) {
                minecraft.execute(() -> {
                    try {
                        if (minecraft.level == null) return;
                        if (!(minecraft.level.getBlockEntity(absolutePos)
                                instanceof TouchDisplayBlockEntity clientDisplay)) return;
                        TouchDisplayBlockEntity.DisplayContent content = clientDisplay.content();
                        if (content.revision() != 1 || content.imageSnapshot() == null) return;
                        if (!content.imageSnapshot().equals(image) || !content.state().equals(state)) {
                            failure.set("Client image projection mismatched the atomic server content");
                            return;
                        }
                        if (!(minecraft.getBlockEntityRenderDispatcher().getRenderer(clientDisplay)
                                instanceof TouchDisplayBlockEntityRenderer)) {
                            failure.set("Touch Display renderer was not registered");
                            return;
                        }

                        var screenBefore = minecraft.screen;
                        ResourceLocation texture = TouchDisplayTextureRuntime
                                .textureFor(content.imageSnapshot(), minecraft.level).orElse(null);
                        if (!content.imageRef().equals(texture)
                            || !(minecraft.getTextureManager().getTexture(texture) instanceof DynamicTexture)) {
                            failure.set("Bounded image did not become a registered dynamic texture");
                            return;
                        }
                        if (!TouchDisplayTextureRuntime.textureFor(image, minecraft.level)
                                .orElseThrow().equals(texture)) {
                            failure.set("An unchanged image did not reuse its texture identity");
                            return;
                        }
                        if (minecraft.screen != screenBefore) {
                            failure.set("Dynamic Touch Display rendering changed the current screen");
                            return;
                        }
                        observed.set(true);
                    } catch (RuntimeException exception) {
                        failure.set(exception.toString());
                    } finally {
                        probeScheduled.set(false);
                    }
                });
            }
            helper.assertTrue(observed.get(), "Client has not registered the bounded display texture");
        });
    }

    private static SFMImageSnapshot fixture() {
        BufferedImage pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xFFFF0000);
        pixels.setRGB(1, 1, 0xFF0000FF);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(pixels, "png", output)) {
                throw new IllegalStateException("No PNG writer is available");
            }
            return SFMImageSnapshot.fromPng(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not make bounded PNG fixture", e);
        }
    }
}
