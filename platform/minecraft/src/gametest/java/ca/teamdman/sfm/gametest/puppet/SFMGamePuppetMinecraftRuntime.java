package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.handler.SFMCommandPaletteKeyHandler;
import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.List;
import java.util.UUID;

final class SFMGamePuppetMinecraftRuntime implements ISFMGamePuppetRuntime {
    private final ActivePuppet active;

    private final Minecraft minecraft;

    SFMGamePuppetMinecraftRuntime(
            ActivePuppet active,
            Minecraft minecraft
    ) {

        this.active = active;
        this.minecraft = minecraft;
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean createFreshFlatWorld() {

        if (!active.worldCreationStarted) {
            active.worldCreationStarted = true;
            RegistryAccess.Frozen registryAccess = RegistryAccess.BUILTIN.get();
            Registry<WorldPreset> presets = registryAccess.registryOrThrow(Registry.WORLD_PRESET_REGISTRY);
            WorldGenSettings worldGenSettings = presets
                    .getOrCreateHolderOrThrow(WorldPresets.FLAT)
                    .value()
                    .createWorldGenSettings(0L, false, false);
            LevelSettings levelSettings = new LevelSettings(
                    SFMGamePuppetHarness.WORLD_NAME_PREFIX + active.definition.puppetName(),
                    GameType.CREATIVE,
                    false,
                    Difficulty.HARD,
                    true,
                    SFMGamePuppetHarness.createWorldGameRules(null),
                    DataPackConfig.DEFAULT
            );
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_CREATING_WORLD puppet={} world={}",
                    active.definition.puppetName(),
                    active.worldId
            );
            minecraft.createWorldOpenFlows().createFreshLevel(
                    active.worldId,
                    levelSettings,
                    registryAccess,
                    worldGenSettings
            );
            return false;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady() || minecraft.player == null) {
            return false;
        }
        if (!active.worldConfigured) {
            active.worldConfigured = true;
            server.execute(() -> SFMGamePuppetHarness.configureWorld(server, server.overworld()));
            SFM.LOGGER.info("SFM_GAME_PUPPET_WORLD_READY puppet={}", active.definition.puppetName());
        }
        return true;
    }

    @Override
    public boolean runGameTest(String testName) {

        if (active.gameTestStartFailure != null) {
            throw new IllegalStateException("Could not start GameTest " + testName, active.gameTestStartFailure);
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady()) {
            return false;
        }
        if (!active.gameTestStartRequested) {
            active.gameTestStartRequested = true;
            server.execute(() -> SFMGamePuppetHarness.startGameTest(active, server, testName));
            return false;
        }
        MultipleTestTracker tracker = active.gameTestTracker;
        if (tracker == null || !tracker.isDone()) {
            return false;
        }
        if (tracker.getFailedRequiredCount() > 0) {
            throw new IllegalStateException(
                    "GameTest " + testName + " failed with " + tracker.getFailedRequiredCount() + " required failures"
            );
        }
        if (active.gameTestOrigin == null) {
            throw new IllegalStateException("GameTest " + testName + " completed without a structure origin");
        }
        return true;
    }

    @Override
    public void positionOrbitCamera(
            BlockPos localTarget,
            double radius,
            double height,
            double angleRadians
    ) {

        BlockPos absoluteTarget = absolute(localTarget);
        Vec3 target = Vec3.atCenterOf(absoluteTarget);
        Vec3 camera = target.add(Math.cos(angleRadians) * radius, height, Math.sin(angleRadians) * radius);
        teleportAndLook(camera, target);
    }

    @Override
    public void positionGameTestOrbitCamera(double angleRadians) {
        GameTestInfo gameTestInfo = active.gameTestInfo;
        if (gameTestInfo == null) {
            throw new IllegalStateException("No completed GameTest is available for orbit capture");
        }
        AABB bounds = gameTestInfo.getStructureBounds();
        if (bounds == null) {
            throw new IllegalStateException("Completed GameTest has no structure bounds for orbit capture");
        }
        Vec3 target = bounds.getCenter();
        double radius = Math.max(7D, Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ) + 4D);
        double height = Math.max(5D, bounds.maxY - bounds.minY + 3D);
        Vec3 camera = target.add(Math.cos(angleRadians) * radius, height, Math.sin(angleRadians) * radius);
        teleportAndLook(camera, target);
    }

    @Override
    public void positionForBlockUse(BlockPos localTarget) {

        Vec3 target = Vec3.atCenterOf(absolute(localTarget));
        teleportAndLook(target.add(0D, 0D, 2.5D), target);
    }

    @Override
    public void useBlock(BlockPos localTarget) {

        if (minecraft.player == null || minecraft.gameMode == null) {
            throw new IllegalStateException("Client player or game mode is unavailable for block interaction");
        }
        BlockPos target = absolute(localTarget);
        InteractionResult result = minecraft.gameMode.useItemOn(
                minecraft.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target), net.minecraft.core.Direction.UP, target, false)
        );
        if (result == InteractionResult.FAIL) {
            throw new IllegalStateException("Client block interaction failed at " + target);
        }
    }

    @Override
    public boolean isScreen(Class<?> expectedType) {

        return expectedType.isInstance(minecraft.screen);
    }

    @Override
    public boolean openCommandPalette() {
        return SFMCommandPaletteKeyHandler.openFromCurrentScreen();
    }

    @Override
    public boolean isOverlay(Class<? extends Overlay> expectedType) {
        return expectedType.isInstance(minecraft.getOverlay());
    }

    @Override
    public boolean capture(
            String captureName,
            Component caption
    ) {

        String safeCaptureName = validateCaptureName(captureName);
        PuppetCaptureState state = active.captures.computeIfAbsent(
                safeCaptureName, name -> {
                    String fileName = active.definition.puppetName() + "__" + name + ".png";
                    return new PuppetCaptureState(
                            name,
                            new File(new File(minecraft.gameDirectory, "screenshots"), fileName),
                            caption.copy(),
                            active.nextFigureNumber++
                    );
                }
        );
        if (state.captureFailure != null) {
            throw new IllegalStateException(
                    "Could not compose screenshot " + state.file.getAbsolutePath(),
                    state.captureFailure
            );
        }
        if (!state.hudPrepared) {
            // The source frame must first render with the clean HUD profile.
            prepareCleanCaptureHud();
            state.hudPrepared = true;
            return false;
        }
        if (!state.requested) {
            state.requested = true;
            if (state.file.exists() && !state.file.delete()) {
                throw new IllegalStateException("Could not replace old screenshot " + state.file.getAbsolutePath());
            }
            queueCaptionedScreenshot(state);
            Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
            String screenName = minecraft.screen == null
                                ? "world"
                                : minecraft.screen.getClass().getSimpleName();
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_CAPTURE_QUEUED puppet={} capture={} file={} figure={} camera_x={} camera_y={} camera_z={} camera_yaw={} camera_pitch={} screen={} hud_hidden={}",
                    active.definition.puppetName(),
                    safeCaptureName,
                    state.file.getName(),
                    state.figureNumber,
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z,
                    minecraft.gameRenderer.getMainCamera().getYRot(),
                    minecraft.gameRenderer.getMainCamera().getXRot(),
                    screenName,
                    minecraft.options.hideGui
            );
            return false;
        }
        state.ticks++;
        if (state.file.isFile() && state.file.length() > 0L) {
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_CAPTURE_WRITTEN puppet={} capture={} file={}",
                    active.definition.puppetName(),
                    safeCaptureName,
                    state.file.getName()
            );
            return true;
        }
        if (state.ticks > SFMGamePuppetHarness.SCREENSHOT_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out writing screenshot " + state.file.getAbsolutePath());
        }
        return false;
    }

    private void queueCaptionedScreenshot(PuppetCaptureState state) {

        if (RenderSystem.isOnRenderThread()) {
            captureCaptionedScreenshot(state);
        } else {
            RenderSystem.recordRenderCall(() -> captureCaptionedScreenshot(state));
        }
    }

    private void captureCaptionedScreenshot(PuppetCaptureState state) {

        TextureTarget captureTarget = null;
        try {
            PuppetCaptureFigureCaptionLayout captionLayout = PuppetCaptureFigureCaptionLayout.create(minecraft, state);
            captureTarget = new TextureTarget(
                    minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height + captionLayout.pixelHeight(),
                    true,
                    Minecraft.ON_OSX
            );
            captureTarget.setClearColor(1F, 1F, 1F, 1F);
            captureTarget.clear(Minecraft.ON_OSX);
            copyMainFrameToCaptionedTarget(minecraft.getMainRenderTarget(), captureTarget);
            renderCaption(captureTarget, captionLayout);
            Screenshot.grab(
                    minecraft.gameDirectory,
                    state.file.getName(),
                    captureTarget,
                    message -> SFM.LOGGER.info(
                            "SFM_GAME_PUPPET_CAPTURE_MESSAGE puppet={} capture={} message={}",
                            active.definition.puppetName(),
                            state.captureName,
                            message.getString()
                    )
            );
        } catch (Throwable failure) {
            state.captureFailure = failure;
            SFM.LOGGER.error(
                    "SFM_GAME_PUPPET_CAPTURE_FAILED puppet={} capture={}",
                    active.definition.puppetName(),
                    state.captureName,
                    failure
            );
        } finally {
            if (captureTarget != null) {
                captureTarget.destroyBuffers();
            }
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void copyMainFrameToCaptionedTarget(
            RenderTarget source,
            TextureTarget destination
    ) {

        GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
        GlStateManager._glBindFramebuffer(36009, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0,
                0,
                source.width,
                source.height,
                0,
                0,
                source.width,
                source.height,
                16384,
                9728
        );
    }

    @MCVersionDependentBehaviour
    private void renderCaption(
            TextureTarget captureTarget,
            PuppetCaptureFigureCaptionLayout captionLayout
    ) {

        Matrix4f originalProjection = RenderSystem.getProjectionMatrix().copy();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            captureTarget.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(Matrix4f.orthographic(
                    0F,
                    (float) (captureTarget.width / captionLayout.guiScale()),
                    0F,
                    (float) (captureTarget.height / captionLayout.guiScale()),
                    1000F,
                    net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane()
            ));
            modelView.setIdentity();
            modelView.translate(0D, 0D, 1000F - net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();

            PoseStack poseStack = new PoseStack();
            int y = SFMGamePuppetHarness.CAPTION_VERTICAL_PADDING;
            for (FormattedCharSequence line : captionLayout.lines()) {
                SFMFontUtils.draw(
                        poseStack,
                        minecraft.font,
                        line,
                        SFMGamePuppetHarness.CAPTION_HORIZONTAL_PADDING,
                        y,
                        0xFF000000,
                        false
                );
                y += minecraft.font.lineHeight;
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(originalProjection);
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void closeScreen() {

        minecraft.setScreen(null);
    }

    @Override
    public void openManagerProgramEditor() {

        if (!(minecraft.screen instanceof ManagerScreen managerScreen)) {
            throw new IllegalStateException("Expected ManagerScreen before opening the program editor");
        }
        List<Button> buttons = managerScreen.getButtonsForJEIExclusionZones();
        if (buttons.size() < 2 || buttons.get(1) == null || !buttons.get(1).visible) {
            throw new IllegalStateException("Manager program editor button is unavailable");
        }
        buttons.get(1).onPress();
    }

    private BlockPos absolute(BlockPos local) {

        BlockPos origin = active.gameTestOrigin;
        if (origin == null) {
            throw new IllegalStateException("No completed GameTest origin is available");
        }
        return origin.offset(local);
    }

    private void prepareCleanCaptureHud() {
        // Retain actual in-game screens, but remove the transient player HUD,
        // chat history, and queued toast notifications from visual artifacts.
        // This setting is scoped to the isolated preview run directory and is
        // never persisted to a developer's normal game options.
        minecraft.options.hideGui = true;
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
    }

    private void teleportAndLook(
            Vec3 position,
            Vec3 target
    ) {

        if (minecraft.player == null) {
            throw new IllegalStateException("Client player is unavailable for camera positioning");
        }
        Vec3 delta = target.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * (180D / Math.PI)) - 90F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * (180D / Math.PI));
        minecraft.player.moveTo(position.x, position.y, position.z, yaw, pitch);
        UUID playerId = minecraft.player.getUUID();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // The server owns the authoritative rotation.  A position-only
                    // teleport would shortly overwrite the camera orientation that
                    // we just applied on the client, leaving overview captures aimed
                    // at the horizon instead of their declared target.
                    player.setYRot(yaw);
                    player.setXRot(pitch);
                    player.setYHeadRot(yaw);
                    player.teleportTo(position.x, position.y, position.z);
                }
            });
        }
    }

    private String validateCaptureName(String captureName) {

        if (captureName == null || !captureName.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid game puppet capture name: " + captureName);
        }
        return captureName;
    }

}
