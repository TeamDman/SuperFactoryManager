package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.handler.SFMCommandPaletteKeyHandler;
import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerPanel;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerLayout;
import ca.teamdman.sfm.client.screen.file_explorer.SFMPathFileExplorerSource;
import ca.teamdman.sfm.client.screen.file_explorer.SFMReadOnlyTextPanel;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerWorkspace;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMJavaLocalTerminalService;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMFalsifiedInventoryReplayPanel;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.client.screen.color.SFMArgbColor;
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanel;
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanelLayout;
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
import net.minecraft.client.gui.screens.Screen;
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
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

final class SFMGamePuppetMinecraftRuntime implements ISFMGamePuppetRuntime {
    private final ActivePuppet active;

    private final Minecraft minecraft;
    private SFMWorkspacePanelId rememberedFileViewerId;

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
    public String currentScreenName() {
        return minecraft.screen == null ? "world" : minecraft.screen.getClass().getName();
    }

    @Override
    public boolean openCommandPalette() {
        return SFMCommandPaletteKeyHandler.openFromCurrentScreen();
    }

    @Override
    public void executeCommandPalette(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before executing a command");
        }
        palette.executeCommandForAutomation(command);
    }

    @Override
    public void openTerminal() {
        SFMScreenMultiplexer.openToSide(minecraft.screen, new SFMTerminalPanel(new SFMJavaLocalTerminalService()));
    }

    @Override
    public void executeTerminal(String command) {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.executeForAutomation(command);
    }

    @Override
    public void scrollTerminal(double delta) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalPanel panel = requireTerminalPanel();
        int panelIndex = multiplexer.panels().indexOf(panel);
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no terminal panel index");
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(multiplexer.panelIds().get(panelIndex));
        if (bounds == null) throw new IllegalStateException("Terminal panel has no allocated bounds");
        multiplexer.mouseScrolled(bounds.x() + bounds.width() / 2D,
                bounds.y() + bounds.height() / 2D, delta);
    }

    @Override
    public void pressTerminalKey(int keyCode) {
        requireTerminalPanel().keyPressed(keyCode, 0, 0);
    }

    @Override
    public void pressFileExplorerKey(int keyCode) {
        requireFileExplorerPanel().keyPressed(keyCode, 0, 0);
    }

    @Override
    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        requireFileExplorerPanel().acceptSnapshot(snapshot);
    }

    @Override
    public void openFileExplorer(SFMFileExplorerSource source) {
        minecraft.setScreen(SFMFileExplorerWorkspace.create(minecraft.screen, source));
    }

    @Override
    public boolean isFileExplorerOpen() {
        try {
            requireFileExplorerPanel();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    @Override
    public void deliverFileExplorerDropFixture() {
        if (minecraft.screen == null) throw new IllegalStateException("Expected a screen for file drop delivery");
        Path fixture = minecraft.gameDirectory.toPath().resolve("sfm-file-explorer-drop-fixture");
        try {
            Files.createDirectories(fixture);
            Files.writeString(fixture.resolve("alpha.txt"), "alpha content from dropped root\nline two\n");
            Files.writeString(fixture.resolve("beta.txt"), "beta replacement content\nline two\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare deterministic file-drop fixture", exception);
        }
        minecraft.screen.onFilesDrop(List.of(fixture));
    }

    @Override
    public void clickFileExplorerRow(int visibleRowIndex) {
        SFMScreenMultiplexer multiplexer = requireFileExplorerMultiplexer();
        int panelIndex = -1;
        for (int i = 0; i < multiplexer.panels().size(); i++) {
            if (multiplexer.panels().get(i) instanceof SFMFileExplorerPanel) {
                panelIndex = i;
                break;
            }
        }
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no explorer panel");
        SFMWorkspacePanelId panelId = multiplexer.panelIds().get(panelIndex);
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(panelId);
        if (bounds == null) throw new IllegalStateException("Explorer panel has no allocated bounds");
        SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        double mouseX = layout.list().x() + Math.max(1, layout.list().width() / 2D);
        double mouseY = layout.list().y() + visibleRowIndex * SFMFileExplorerPanel.ROW_HEIGHT
                + SFMFileExplorerPanel.ROW_HEIGHT / 2D;
        multiplexer.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    ) {
        SFMScreenMultiplexer multiplexer = requireFileExplorerMultiplexer();
        if (multiplexer.panels().size() != panelCount) {
            throw new IllegalStateException("Expected " + panelCount + " panels but found " + multiplexer.panels().size());
        }
        SFMFileExplorerPanel explorer = requireFileExplorerPanel();
        if (!expectedRootName.isEmpty()) {
            if (!(explorer.model().source() instanceof SFMPathFileExplorerSource pathSource)
                    || !pathSource.root().getFileName().toString().equals(expectedRootName)) {
                throw new IllegalStateException("Explorer root did not match " + expectedRootName);
            }
        }
        SFMReadOnlyTextPanel viewer = multiplexer.panels().stream()
                .filter(SFMReadOnlyTextPanel.class::isInstance)
                .map(SFMReadOnlyTextPanel.class::cast)
                .findFirst().orElse(null);
        if (expectedViewerPath.isEmpty()) {
            if (viewer != null) throw new IllegalStateException("Expected no viewer panel");
        } else {
            if (viewer == null || !viewer.path().equals(expectedViewerPath)
                    || !viewer.text().contains(expectedViewerText)) {
                throw new IllegalStateException("Viewer did not show expected path/content");
            }
            int viewerIndex = multiplexer.panels().indexOf(viewer);
            SFMWorkspacePanelId viewerId = multiplexer.panelIds().get(viewerIndex);
            if (rememberOrRequireViewerIdentity) {
                if (rememberedFileViewerId == null) rememberedFileViewerId = viewerId;
                else if (!rememberedFileViewerId.equals(viewerId)) {
                    throw new IllegalStateException("Viewer panel identity changed across previews");
                }
            }
        }
        if (panelCount == 2) {
            SFMScreenPanelBounds first = multiplexer.panelBounds(multiplexer.panelIds().get(0));
            SFMScreenPanelBounds second = multiplexer.panelBounds(multiplexer.panelIds().get(1));
            if (first == null || second == null || Math.abs(first.width() - second.width()) > 1
                    || second.x() - first.x() - first.width() != 2) {
                throw new IllegalStateException("Expected equal-share horizontal allocation; first=" + first + ", second=" + second);
            }
        }
    }

    private SFMScreenMultiplexer requireFileExplorerMultiplexer() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected file explorer workspace");
        }
        return multiplexer;
    }

    private SFMTerminalPanel requireTerminalPanel() {
        return requireTerminalMultiplexer().panels().stream()
                .filter(SFMTerminalPanel.class::isInstance)
                .map(SFMTerminalPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no terminal panel"));
    }

    private SFMScreenMultiplexer requireTerminalMultiplexer() {
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) return multiplexer;
        throw new IllegalStateException("Expected terminal workspace");
    }

    private SFMFileExplorerPanel requireFileExplorerPanel() {
        if (minecraft.screen instanceof SFMFileExplorerScreen screen) return screen.panel();
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) {
            return multiplexer.panels().stream()
                    .filter(SFMFileExplorerPanel.class::isInstance)
                    .map(SFMFileExplorerPanel.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Workspace has no file explorer panel"));
        }
        throw new IllegalStateException("Expected file explorer screen or workspace");
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
                    String variantSuffix = active.definition.viewportProfile() == SFMGamePuppetViewportProfile.CURRENT
                            ? ""
                            : "__viewport-" + active.viewportVariant.width() + "x" + active.viewportVariant.height()
                              + "-gui-" + active.viewportVariant.requestedScaleName()
                              + "-effective-" + active.viewportObservation.effectiveGuiScale();
                    String fileName = active.definition.puppetName() + "__" + name + variantSuffix + ".png";
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
                    "SFM_GAME_PUPPET_CAPTURE_QUEUED puppet={} variant={} capture={} file={} figure={} actual_width={} actual_height={} framebuffer_width={} framebuffer_height={} requested_gui_scale={} effective_gui_scale={} logical_width={} logical_height={} camera_x={} camera_y={} camera_z={} camera_yaw={} camera_pitch={} screen={} hud_hidden={}",
                    active.definition.puppetName(),
                    active.viewportVariant.id(),
                    safeCaptureName,
                    state.file.getName(),
                    state.figureNumber,
                    active.viewportObservation.windowWidth(),
                    active.viewportObservation.windowHeight(),
                    active.viewportObservation.framebufferWidth(),
                    active.viewportObservation.framebufferHeight(),
                    active.viewportVariant.requestedScaleName(),
                    active.viewportObservation.effectiveGuiScale(),
                    active.viewportObservation.logicalWidth(),
                    active.viewportObservation.logicalHeight(),
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
                    "SFM_GAME_PUPPET_CAPTURE_WRITTEN puppet={} variant={} capture={} file={}",
                    active.definition.puppetName(),
                    active.viewportVariant.id(),
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
    public void closeScreenNaturally() {
        Screen screen = minecraft.screen;
        if (screen == null) {
            throw new IllegalStateException("Expected a screen to close naturally");
        }
        screen.onClose();
    }

    @Override
    public boolean clickWorkspacePanel(int panelIndex) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM screen multiplexer before focusing a panel");
        }
        if (panelIndex < 0 || panelIndex >= multiplexer.panels().size()) {
            throw new IllegalArgumentException("Workspace panel index is out of range: " + panelIndex);
        }
        double mouseX = (panelIndex + 0.5D) * multiplexer.width / multiplexer.panels().size();
        double mouseY = multiplexer.height / 2D;
        multiplexer.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        return multiplexer.focusedPanel() == panelIndex;
    }

    @Override
    public void openFalsifiedInventoryTimeline() {
        minecraft.setScreen(SFMScreenMultiplexer.create(
                minecraft.screen,
                new SFMTimelinePanel(new SFMFalsifiedInventoryReplayPanel(), 20)
        ));
    }

    @Override
    public void seekFalsifiedInventoryTimeline(int timestep) {
        requireFalsifiedInventoryTimeline().seek(timestep);
    }

    @Override
    public void seekFalsifiedInventoryKeyframePosition(double position) {
        requireFalsifiedInventoryTimeline().seekKeyframePosition(position);
    }

    @Override
    public void seekFalsifiedInventoryElapsedTicks(double ticks) {
        requireFalsifiedInventoryTimeline().seekElapsedTicks(ticks);
    }

    @Override
    public void jumpFalsifiedInventoryKeyframe(int direction) {
        requireFalsifiedInventoryTimeline().jumpKeyframe(direction);
    }

    @Override
    public void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep) {
        SFMTimelinePanel timeline = requireFalsifiedInventoryTimeline();
        SFMScreenMultiplexer multiplexer = (SFMScreenMultiplexer) minecraft.screen;
        timeline.seek(fromTimestep);
        double fromX = timeline.xForTimestep(fromTimestep);
        double toX = timeline.xForTimestep(toTimestep);
        double y = timeline.trackY();
        multiplexer.mouseClicked(fromX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseDragged(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, toX - fromX, 0D);
        multiplexer.mouseReleased(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (timeline.model().current() != toTimestep) {
            throw new IllegalStateException(
                    "Timeline drag selected " + timeline.model().current() + " instead of " + toTimestep
            );
        }
    }

    private SFMTimelinePanel requireFalsifiedInventoryTimeline() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM screen multiplexer for inventory timeline");
        }
        return multiplexer.panels().stream()
                .filter(SFMTimelinePanel.class::isInstance)
                .map(SFMTimelinePanel.class::cast)
                .filter(panel -> panel.title().getString().contains("Falsified chest replay"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no falsified inventory timeline"));
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

    @Override
    public void openColorInput(boolean toSide) {
        SFMColorInputPanel panel = new SFMColorInputPanel(
                new SFMArgbColor(0xFF3366CC),
                java.util.List.of(new SFMArgbColor(0xFFFFAA00), new SFMArgbColor(0xFF44CC66),
                        new SFMArgbColor(0x808844CC)),
                colour -> SFM.LOGGER.info("SFM_COLOR_INPUT_CONFIRMED value={}", colour.toHex(SFMArgbColor.HexOrder.ARGB)),
                () -> SFM.LOGGER.info("SFM_COLOR_INPUT_CANCELLED")
        );
        if (toSide) SFMScreenMultiplexer.openToSide(minecraft.screen, panel);
        else minecraft.setScreen(SFMScreenMultiplexer.create(minecraft.screen, panel));
    }

    @Override
    public void setColorInputHueSaturation(double hue, double saturation) {
        SFMColorInputPanel panel = requireColorInput();
        SFMColorInputPanelLayout.Rect field = panel.layout().hueSaturation();
        clickWorkspace(field.x() + hue * (field.width() - 1D),
                field.y() + (1D - saturation) * (field.height() - 1D));
    }

    @Override
    public void setColorInputValue(double value) {
        SFMColorInputPanelLayout.Rect slider = requireColorInput().layout().valueSlider();
        clickWorkspace(slider.x() + value * (slider.width() - 1D), slider.y() + slider.height() / 2D);
    }

    @Override
    public void adjustColorInputChannel(int channel, int direction, int clicks) {
        if (channel < 0 || channel > 3 || (direction != -1 && direction != 1) || clicks < 0) {
            throw new IllegalArgumentException("Invalid colour channel adjustment");
        }
        SFMColorInputPanelLayout.Rect channels = requireColorInput().layout().channels();
        int rowHeight = channels.height() / 4;
        double x = direction < 0 ? channels.right() - 30D : channels.right() - 9D;
        double y = channels.y() + channel * rowHeight + rowHeight / 2D;
        for (int i = 0; i < clicks; i++) clickWorkspace(x, y);
    }

    @Override
    public void selectColorInputRecent(int index) {
        SFMColorInputPanel panel = requireColorInput();
        SFMColorInputPanelLayout.Rect recents = panel.layout().recents();
        int size = Math.min(20, recents.height());
        clickWorkspace(recents.x() + index * (size + 4) + size / 2D, recents.y() + size / 2D);
    }

    @Override public void resetColorInput() { clickRect(requireColorInput().layout().reset()); }

    @Override
    public void setColorInputHex(String hex, boolean rgbaOrder) {
        requireColorInput().setHexValue(hex,
                rgbaOrder ? SFMArgbColor.HexOrder.RGBA : SFMArgbColor.HexOrder.ARGB);
    }

    @Override
    public void confirmColorInput() {
        SFMColorInputPanel panel = requireColorInput();
        clickRect(panel.layout().confirm());
        if (panel.confirmedResult() == null) throw new IllegalStateException("Colour input did not confirm a typed result");
    }

    private SFMColorInputPanel requireColorInput() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM workspace for colour input");
        }
        return multiplexer.panels().stream().filter(SFMColorInputPanel.class::isInstance)
                .map(SFMColorInputPanel.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no colour input panel"));
    }

    private void clickRect(SFMColorInputPanelLayout.Rect rect) {
        clickWorkspace(rect.x() + rect.width() / 2D, rect.y() + rect.height() / 2D);
    }

    private void clickWorkspace(double x, double y) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM workspace before mouse input");
        }
        multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
