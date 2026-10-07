package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.handler.SFMCommandPaletteKeyHandler;
{% endif %}
import ca.teamdman.sfm.client.screen.ManagerScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% when "26.1.2" %}
{% endcase %}
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.screen.SFMTerminalPasteConfirmationScreen;
{% endif %}
{% endif %}
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerPanel;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerLayout;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMPathFileExplorerSource;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMReadOnlyTextPanel;
{% endif %}
{% if features.file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerScreen;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerPanel;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerLayout;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMPathFileExplorerSource;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMReadOnlyTextPanel;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerWorkspace;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
{% endif %}
{% if features.legacy_file_explorer %}
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
{% endif %}
{% endcase %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalInteractionPuppetProbe;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPresentationPuppetProbe;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesSnapshot;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalErrorCode;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
{% endif %}
{% endif %}
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
import ca.teamdman.sfm.client.terminal.SFMTerminalServiceFactory;
{% endif %}
{% endif %}
{% endcase %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels or features.workspace_dividers %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.workspace_panels and features.timeline_panels %}
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMFalsifiedInventoryReplayPanel;
{% endif %}
{% if features.workspace_panels and features.timeline_panels %}
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.color.SFMArgbColor;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanel;
{% endif %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanelLayout;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
import ca.teamdman.sfm.common.block.TouchDisplaySurface;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
{% when "1.19.4" %}
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
{% when "1.21", "1.21.1" %}
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.Screenshot;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
{% when "1.19.4" %}
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.core.registries.Registries;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.HttpUtil;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.util.FormattedCharSequence;
{% when "26.1.2" %}
{% endcase %}
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.DataPackConfig;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
{% when "26.1.2" %}
import net.minecraft.world.level.levelgen.WorldOptions;
{% endcase %}
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
{% case minecraft_version %}
{% when "1.19.2", "26.1.2" %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import org.joml.Matrix4f;
{% when "1.21", "1.21.1" %}
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
{% endcase %}
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

final class SFMGamePuppetMinecraftRuntime implements ISFMGamePuppetRuntime {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static final long TERMINAL_CONTENT_ASSERTION_TIMEOUT_MILLIS = 3_000L;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static net.minecraft.client.input.MouseButtonEvent leftButtonEvent(double x, double y) {
        return new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

{% endcase %}
    private final ActivePuppet active;

    private final Minecraft minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.file_explorer and features.workspace_panels %}
    private SFMWorkspacePanelId rememberedFileViewerId;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.legacy_file_explorer and features.workspace_panels %}
    private SFMWorkspacePanelId rememberedFileViewerId;
{% endif %}
{% endcase %}

    SFMGamePuppetMinecraftRuntime(
            ActivePuppet active,
            Minecraft minecraft
    ) {

        this.active = active;
        this.minecraft = minecraft;
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2" %}
    public String puppetWorldId() {
        return active.worldId;
    }

    @Override
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    @MCVersionDependentBehaviour
    public boolean createFreshFlatWorld() {

        if (!active.worldCreationStarted) {
            active.worldCreationStarted = true;
{% case minecraft_version %}
{% when "1.19.2" %}
            RegistryAccess registryAccess = RegistryAccess.builtinCopy();
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
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            LevelSettings levelSettings = new LevelSettings(
                    SFMGamePuppetHarness.WORLD_NAME_PREFIX + active.definition.puppetName(),
                    GameType.CREATIVE,
                    false,
                    Difficulty.HARD,
                    true,
                    SFMGamePuppetHarness.createWorldGameRules(null),
                    WorldDataConfiguration.DEFAULT
            );
            WorldOptions worldOptions = new WorldOptions(0L, false, false);
{% when "26.1.2" %}
            LevelSettings levelSettings = createPuppetLevelSettings(active.definition.puppetName());
            WorldOptions worldOptions = new WorldOptions(0L, false, false);
{% endcase %}
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_CREATING_WORLD puppet={} world={}",
                    active.definition.puppetName(),
                    active.worldId
            );
            minecraft.createWorldOpenFlows().createFreshLevel(
                    active.worldId,
                    levelSettings,
{% case minecraft_version %}
{% when "1.19.2" %}
                    registryAccess,
                    worldGenSettings
{% when "1.19.4", "1.20", "1.20.1", "1.20.2" %}
                    worldOptions,
                    registryAccess -> registryAccess
                            .registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT)
                            .value()
                            .createWorldDimensions()
{% when "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    worldOptions,
                    registryAccess -> registryAccess
                            .registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT)
                            .value()
                            .createWorldDimensions(),
                    minecraft.screen
{% when "26.1.2" %}
                    worldOptions,
                    WorldPresets::createFlatWorldDimensions,
                    minecraft.screen
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @Override
    public void prepareGameTest() {
        if (active.gameTestStartRequested
                && (active.gameTestTracker == null || !active.gameTestTracker.isDone())) {
            throw new IllegalStateException("Cannot replace a GameTest that is still running");
        }
        active.gameTestStartRequested = false;
        active.gameTestStartFailure = null;
        active.gameTestTracker = null;
        active.gameTestInfo = null;
        active.gameTestOrigin = null;
        active.gameTestBounds = null;
        active.gameTestName = null;
    }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @Override
    public boolean runGameTest(String testName) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    private static LevelSettings createPuppetLevelSettings(String puppetName) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @Override
    public boolean startGameTest(String testName) {
        if (active.gameTestStartFailure != null) {
            throw new IllegalStateException("Could not start GameTest " + testName, active.gameTestStartFailure);
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady()) {
            return false;
        }
        if (!active.gameTestStartRequested) {
            active.gameTestStartRequested = true;
            active.gameTestName = testName;
            server.execute(() -> SFMGamePuppetHarness.startGameTest(active, server, testName));
            return false;
        }
        requireSelectedGameTest(testName);
        return active.gameTestTracker != null
                && active.gameTestInfo != null
                && active.gameTestOrigin != null;
    }

    @Override
    public boolean startGameTest(SFMGameTestDefinition testDefinition) {
        String testName = testDefinition.testName();
        if (active.gameTestStartFailure != null) {
            throw new IllegalStateException("Could not start GameTest " + testName, active.gameTestStartFailure);
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady()) {
            return false;
        }
        if (!active.gameTestStartRequested) {
            active.gameTestStartRequested = true;
            active.gameTestName = testName;
            server.execute(() -> SFMGamePuppetHarness.startGameTest(active, server, testDefinition));
            return false;
        }
        requireSelectedGameTest(testName);
        return active.gameTestTracker != null
                && active.gameTestInfo != null
                && active.gameTestOrigin != null;
    }

    @Override
    public boolean waitForGameTest(String testName) {
        if (!active.gameTestStartRequested) {
            throw new IllegalStateException("GameTest " + testName + " has not been started");
        }
        requireSelectedGameTest(testName);
        if (active.gameTestStartFailure != null) {
            throw new IllegalStateException("Could not start GameTest " + testName, active.gameTestStartFailure);
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
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
{% when "26.1.2" %}
        return new LevelSettings(
                SFMGamePuppetHarness.WORLD_NAME_PREFIX + puppetName,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.HARD, false, false),
                true,
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT
        );
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
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        AABB bounds = active.gameTestBounds != null
                ? active.gameTestBounds
                : gameTestInfo.getStructureBounds();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        AABB bounds = gameTestInfo.getStructureBounds();
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}

    @Override
    public void positionForTouchDisplayFace(BlockPos localTarget, Direction face, double u, double v) {
        if (minecraft.screen != null || minecraft.player == null) {
            throw new IllegalStateException("Touch Display aiming requires an in-world client player");
        }
        selectEmptyHotbarSlot();
        Vec3 hit = touchDisplayHit(absolute(localTarget), face, u, v);
        // Stay inside the 5x5x5 GameTest structure; 2.5 blocks would put the
        // camera behind its north boundary wall and hide the display entirely.
        Vec3 eye = hit.add(face.getStepX() * 1.25D, face.getStepY() * 1.25D, face.getStepZ() * 1.25D);
        // The player settles onto the template floor before the next rendered
        // observation. Aim from that stable eye height, not a transient
        // mid-air teleport position that gravity invalidates a few ticks later.
        double floorY = active.gameTestOrigin.getY();
        double eyeHeight = minecraft.player.getEyeHeight();
        teleportAndLook(new Vec3(eye.x, floorY, eye.z), hit.add(0D, -eyeHeight, 0D));
    }
{% endif %}
{% if features.touch_display %}

    @Override
    public void pressTouchDisplayFace(BlockPos localTarget, Direction face, double u, double v) {
        if (minecraft.screen != null || minecraft.player == null || minecraft.gameMode == null) {
            throw new IllegalStateException("Touch Display pressing requires an in-world client player");
        }
        if (!minecraft.player.getMainHandItem().isEmpty()) {
            throw new IllegalStateException("Touch Display proof requires an empty main hand");
        }
        BlockPos target = absolute(localTarget);
        InteractionResult result = minecraft.gameMode.useItemOn(
                minecraft.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(touchDisplayHit(target, face, u, v), face, target, false)
        );
        if (result == InteractionResult.FAIL) {
            throw new IllegalStateException("Client gameplay interaction failed at " + target);
        }
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public BlockPos absoluteGameTestPos(BlockPos localTarget) {
        return absolute(localTarget);
    }

    private void selectEmptyHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).isEmpty()) {
                minecraft.player.getInventory().selected = slot;
                minecraft.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                return;
            }
        }
        throw new IllegalStateException("Touch Display proof needs an empty hotbar slot");
    }
{% if features.touch_display %}

    private static Vec3 touchDisplayHit(BlockPos target, Direction face, double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0D || u > 1D || v < 0D || v > 1D) {
            throw new IllegalArgumentException("Touch Display U/V must be finite and in [0,1]");
        }
        TouchDisplaySurface.Basis basis = TouchDisplaySurface.basis(face);
        double right = (u - 0.5D) * 2D * TouchDisplaySurface.HALF_IMAGE_SIZE;
        double up = (0.5D - v) * 2D * TouchDisplaySurface.HALF_IMAGE_SIZE;
        return new Vec3(
                target.getX() + 0.5D + face.getStepX() * 0.5D + basis.rightX() * right + basis.upX() * up,
                target.getY() + 0.5D + face.getStepY() * 0.5D + basis.rightY() * right + basis.upY() * up,
                target.getZ() + 0.5D + face.getStepZ() * 0.5D + basis.rightZ() * right + basis.upZ() * up
        );
    }
{% endif %}

    @Override
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public String currentScreenName() {
        return minecraft.screen == null ? "world" : minecraft.screen.getClass().getName();
    }
{% if features.command_palette %}

    @Override
    public boolean openCommandPalette() {
        return SFMCommandPaletteKeyHandler.openFromCurrentScreen();
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.command_palette %}

    @Override
    public void setCommandPaletteInput(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before setting automation input");
        }
        palette.setInputForAutomation(command);
    }
{% endif %}
{% if features.command_palette %}

    @Override
    public void submitCommandPalette() {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before submitting automation input");
        }
        palette.submitInputForAutomation();
    }
{% endif %}
{% if features.command_palette %}

    @Override
    public void executeCommandPalette(String command) {
        setCommandPaletteInput(command);
        submitCommandPalette();
    }
{% endif %}

    @Override
    @MCVersionDependentBehaviour
    public void pressScreenKey(int keyCode, int modifiers) {
        Screen screen = minecraft.screen;
        if (screen == null) {
            throw new IllegalStateException("Expected a screen before injecting a key");
        }
        var press = new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(
                screen, keyCode, 0, modifiers);
{% if features.keyboard_profiles %}
        ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler.onScreenKeyPressed(press);
{% endif %}
        SFM.LOGGER.info("SFM_PUPPET_SCREEN_KEY key={} modifiers={} consumed={}",
                keyCode, modifiers, press.isCanceled());
        if (!press.isCanceled()) screen.keyPressed(keyCode, 0, modifiers);
{% if features.keyboard_profiles %}
        ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler.onKey(
                new net.minecraftforge.client.event.InputEvent.Key(
                        keyCode, 0, GLFW.GLFW_PRESS, modifiers));
{% endif %}

        Screen releaseScreen = minecraft.screen;
        if (releaseScreen != null) {
            var release = new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(
                    releaseScreen, keyCode, 0, modifiers);
{% if features.keyboard_profiles %}
            ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler.onScreenKeyReleased(release);
{% endif %}
            if (!release.isCanceled()) releaseScreen.keyReleased(keyCode, 0, modifiers);
        }
{% if features.keyboard_profiles %}
        ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler.onKey(
                new net.minecraftforge.client.event.InputEvent.Key(
                        keyCode, 0, GLFW.GLFW_RELEASE, modifiers));
{% endif %}
    }

    @Override
    @MCVersionDependentBehaviour
    public void typeScreenCharacter(char character, int modifiers) {
        if (Character.isSurrogate(character)) {
            throw new IllegalArgumentException("Screen character automation accepts BMP characters only");
        }
        Screen screen = minecraft.screen;
        if (screen == null) {
            throw new IllegalStateException("Expected a screen before injecting a character");
        }
        var event = new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(
                screen,
                character,
                modifiers
        );
{% if features.keyboard_profiles %}
        ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler.onScreenCharacterTyped(event);
{% endif %}
        SFM.LOGGER.info("SFM_PUPPET_SCREEN_CHARACTER codepoint={} modifiers={} consumed={}",
                (int) character, modifiers, event.isCanceled());
        if (!event.isCanceled() && !screen.charTyped(character, modifiers)) {
            throw new IllegalStateException("Screen rejected typed character " + (int) character);
        }
    }
{% if features.command_palette %}

    @Override
    public void exerciseCommandPaletteViewport() {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before viewport exercise");
        }
        palette.exerciseSuggestionViewportForAutomation();
    }
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void assertFormerTerminalStartButtonRoutesToTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMWorkspacePanelId panelId = multiplexer.focusedPanelId();
        SFMScreenPanelBounds localFormer = panel.formerStartButtonBoundsForAutomation();
        SFMScreenPanelBounds globalFormer = multiplexer.measure(panelId, localFormer)
                .map(metrics -> metrics.globalGuiLogicalBounds())
                .orElseThrow(() -> new IllegalStateException(
                        "Former Start/Retry bounds could not be mapped to the workspace"));
        long attemptsBefore = panel.startButtonAttemptCountForAutomation();
        long mouseDispatchesBefore = panel.terminalMouseDispatchCountForAutomation();
        double x = globalFormer.x() + globalFormer.width() / 2D;
        double y = globalFormer.y() + globalFormer.height() / 2D;
        if (!multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Former Start/Retry coordinates were not routed by the workspace");
        }
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (panel.startButtonAttemptCountForAutomation() != attemptsBefore) {
            throw new IllegalStateException("Former Start/Retry coordinates launched the Rust server again");
        }
        if (panel.terminalMouseDispatchCountForAutomation() <= mouseDispatchesBefore) {
            throw new IllegalStateException("Former Start/Retry coordinates did not reach terminal mouse input");
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean isFormerTerminalStartButtonRoutingReady() {
        return minecraft.screen instanceof SFMScreenMultiplexer
                && requireTerminalPanel().formerStartButtonRoutingReadyForAutomation();
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.command_palette %}

    @Override
    public void assertActionChoice(List<String> expectedCommands) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected a constrained command palette but found "
                    + (minecraft.screen == null ? "no screen" : minecraft.screen.getClass().getName()));
        }
        List<String> actual = palette.choiceCommandsForAutomation();
        if (!actual.equals(expectedCommands)) {
            throw new IllegalStateException("Constrained command palette choices were " + actual
                    + " instead of " + expectedCommands);
        }
    }
{% endif %}
{% if features.command_palette %}

    @Override
    public void clickActionChoice(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected a constrained command palette before choice execution");
        }
        palette.clickChoiceForAutomation(command);
    }
{% endif %}
{% if features.workspace_panels and features.workspace_stack_controls and features.workspace_panel_metadata and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    @Override
    public void assertWorkspaceState(
            int totalEntries,
            int visibleEntries,
            int focusedSlotEntries,
            String expectedFocusedNarration,
            int expectedFocusedScale
    ) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected an SFM workspace but found "
                    + (minecraft.screen == null ? "no screen" : minecraft.screen.getClass().getName()));
        }
        if (multiplexer.panels().size() != totalEntries) {
            throw new IllegalStateException("Expected " + totalEntries + " entries but found " + multiplexer.panels().size());
        }
        if (multiplexer.visiblePanelEntries().size() != visibleEntries) {
            throw new IllegalStateException("Expected " + visibleEntries + " visible entries but found "
                    + multiplexer.visiblePanelEntries().size());
        }
        if (multiplexer.focusedSlotEntries().size() != focusedSlotEntries) {
            throw new IllegalStateException("Expected focused stack size " + focusedSlotEntries
                    + " but found " + multiplexer.focusedSlotEntries().size());
        }
        for (var entry : multiplexer.visiblePanelEntries()) {
            if (multiplexer.panelBounds(entry.id()) == null) {
                throw new IllegalStateException("Visible entry has no allocated bounds: " + entry.id());
            }
            if (multiplexer.panelContentBounds(entry.id()) == null) {
                throw new IllegalStateException("Visible entry has no logical content bounds: " + entry.id());
            }
        }
        var focused = multiplexer.visiblePanelEntries().stream()
                .filter(entry -> entry.id().equals(multiplexer.focusedPanelId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Focused entry is not visible"));
        if (expectedFocusedNarration != null && !expectedFocusedNarration.isEmpty()
                && !focused.panel().narration().getString().contains(expectedFocusedNarration)) {
            throw new IllegalStateException("Focused entry narration did not contain " + expectedFocusedNarration);
        }
        if (expectedFocusedScale >= 0
                && !java.util.Objects.equals(focused.metadata().guiScaleOverride(), expectedFocusedScale)) {
            throw new IllegalStateException("Focused entry scale did not equal " + expectedFocusedScale
                    + ": " + focused.metadata().guiScaleOverride());
        }
    }
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    @Override
    public void assertWorkspacePanelExtentComparison(
            int firstPanelIndex,
            int secondPanelIndex,
            SFMWorkspaceAxis axis,
            int expectedComparison
    ) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected an SFM workspace");
        }
        if (expectedComparison < -1 || expectedComparison > 1) {
            throw new IllegalArgumentException("Expected comparison must be -1, 0, or 1");
        }
        List<SFMWorkspacePanelId> ids = multiplexer.panelIds();
        if (firstPanelIndex < 0 || firstPanelIndex >= ids.size()
                || secondPanelIndex < 0 || secondPanelIndex >= ids.size()) {
            throw new IllegalStateException("Workspace panel comparison index is outside " + ids.size());
        }
        SFMScreenPanelBounds first = multiplexer.panelBounds(ids.get(firstPanelIndex));
        SFMScreenPanelBounds second = multiplexer.panelBounds(ids.get(secondPanelIndex));
        if (first == null || second == null) {
            throw new IllegalStateException("Workspace extent comparison requires two visible panels");
        }
        int firstExtent = axis == SFMWorkspaceAxis.HORIZONTAL ? first.width() : first.height();
        int secondExtent = axis == SFMWorkspaceAxis.HORIZONTAL ? second.width() : second.height();
        int actual = expectedComparison == 0 && Math.abs(firstExtent - secondExtent) <= 1
                ? 0
                : Integer.compare(firstExtent, secondExtent);
        if (actual != expectedComparison) {
            throw new IllegalStateException("Expected panel " + firstPanelIndex + " extent " + firstExtent
                    + " to compare as " + expectedComparison + " with panel " + secondPanelIndex
                    + " extent " + secondExtent + " on " + axis);
        }
    }
{% endif %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    @Override
    public void assertWorkspacePanelInstancesDistinct(int firstPanelIndex, int secondPanelIndex) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected an SFM workspace");
        }
        List<SFMWorkspacePanelId> ids = multiplexer.panelIds();
        if (firstPanelIndex < 0 || firstPanelIndex >= ids.size()
                || secondPanelIndex < 0 || secondPanelIndex >= ids.size()) {
            throw new IllegalStateException("Workspace panel identity index is outside " + ids.size());
        }
        if (ids.get(firstPanelIndex).equals(ids.get(secondPanelIndex))) {
            throw new IllegalStateException("Workspace duplicate reused a panel id");
        }
        if (multiplexer.panelInstance(ids.get(firstPanelIndex))
                == multiplexer.panelInstance(ids.get(secondPanelIndex))) {
            throw new IllegalStateException("Workspace duplicate reused a mutable panel instance");
        }
    }
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void openTerminal() {
        SFMScreenMultiplexer.openToSide(minecraft.screen,
                new SFMTerminalPanel(SFMTerminalServiceFactory.createRepl()));
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void executeTerminal(String command) {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.executeForAutomation(command);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void cancelTerminal() {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.cancelForAutomation();
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void restartRustTerminalServer() {
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMTerminalServiceFactory.stopOwnedRustServer();
        try {
            SFMTerminalServiceFactory.startRustServer(null);
            // Keep the panel's old transport closed over the intentional
            // outage.  Clear/reconnect only after the replacement endpoint
            // has passed the readiness probe, otherwise its background
            // poller can race startup and latch a transient refusal.
            panel.reconnectForAutomation();
        } catch (Exception error) {
            throw new IllegalStateException("Rust terminal server restart failed", error);
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_server_lifecycle and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void startRustTerminalThroughUi() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalPanel panel = requireTerminalPanel();
        ResourceLocation expected = new ResourceLocation("sfm", "terminal/server/start_control");
        ResourceLocation focused = panel.widgetHost().orElseThrow()
                .focusedElementId().orElseThrow(() -> new IllegalStateException(
                        "Disconnected terminal has no focused Start/Retry widget"));
        if (!expected.equals(focused)) {
            throw new IllegalStateException("Disconnected terminal focused " + focused
                    + " instead of " + expected);
        }
        if (!multiplexer.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0)) {
            throw new IllegalStateException("Disconnected Start/Retry widget rejected Space");
        }
        if (!panel.startRequestedForAutomation()) {
            throw new IllegalStateException("Start/Retry action did not begin the panel-owned server start");
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void typeTerminalText(String text) {
        SFMTerminalPanel panel = requireTerminalPanel();
        for (int index = 0; index < text.length(); index++) {
            if (!panel.charTyped(text.charAt(index), 0)) {
                throw new IllegalStateException("Terminal rejected typed character at index " + index);
            }
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void pasteTerminalText(String text) {
        requireTerminalPanel().pasteForAutomation(text);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public SFMTerminalInteractionPuppetProbe.TextRange locateTerminalText(String exactText) {
        return SFMTerminalInteractionPuppetProbe.locateExactVisibleLine(requireTerminalPanel(), exactText);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public SFMTerminalInteractionPuppetProbe.Observation observeTerminalInteraction(
            String rendererId,
            String transportId
    ) {
        return SFMTerminalInteractionPuppetProbe.observe(
                requireTerminalPanel(), rendererId, transportId);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void dragTerminalRange(
            SFMTerminalInteractionPuppetProbe.TextRange range,
            boolean reverse
    ) {
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalInteractionPuppetProbe.Point start = SFMTerminalInteractionPuppetProbe.point(
                panel, range.startColumn(), range.row());
        SFMTerminalInteractionPuppetProbe.Point end = SFMTerminalInteractionPuppetProbe.point(
                panel, range.endColumn(), range.row());
        if (reverse) {
            SFMTerminalInteractionPuppetProbe.Point swap = start;
            start = end;
            end = swap;
        }
        multiplexer.mouseClicked(start.x(), start.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseMoved(end.x(), end.y());
        multiplexer.mouseDragged(
                end.x(), end.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT,
                end.x() - start.x(), end.y() - start.y());
        multiplexer.mouseReleased(end.x(), end.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void copyTerminalSelection(boolean rightClick) {
        if (!rightClick) {
            pressTerminalKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
            return;
        }
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalInteractionPuppetProbe.Point point = SFMTerminalInteractionPuppetProbe.point(
                panel, 0, 0);
        multiplexer.mouseClicked(point.x(), point.y(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        multiplexer.mouseReleased(point.x(), point.y(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public String terminalClipboard() {
        return minecraft.keyboardHandler.getClipboard();
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void pasteTerminalTextByRightClick(String text) {
        minecraft.keyboardHandler.setClipboard(text);
        copyTerminalSelection(true);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean isTerminalPasteWarningOpen() {
        return minecraft.screen instanceof SFMTerminalPasteConfirmationScreen;
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public String terminalPasteWarningText() {
        if (!(minecraft.screen instanceof SFMTerminalPasteConfirmationScreen warning)) {
            throw new IllegalStateException("Terminal paste warning is not open");
        }
        return warning.warningTextForAutomation();
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard and features.terminal_paste_confirmation %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean terminalPasteWarningCancelFocused() {
        if (!(minecraft.screen instanceof SFMTerminalPasteConfirmationScreen warning)) {
            throw new IllegalStateException("Terminal paste warning is not open");
        }
        return warning.cancelFocusedForAutomation();
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean terminalContentHasExactLine(String line) {
        return requireTerminalPanel().contentForAutomation().lines()
                .map(String::strip)
                .anyMatch(line::equals);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_clipboard %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void writeTerminalInteractionEvidence(String artifactName, String evidence) {
        String safeArtifactName = validateCaptureName(artifactName);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(
                active.definition.puppetName() + "__" + safeArtifactName + "__push-evidence.txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, evidence, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal interaction evidence " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_PUSH_EVIDENCE_WRITTEN puppet={} variant={} artifact={} file={} chars={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                file.getFileName(),
                evidence.length()
        );
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        SFMTerminalPanel panel = requireTerminalPanel();
        String content = "";
        long deadline = System.nanoTime()
                + TERMINAL_CONTENT_ASSERTION_TIMEOUT_MILLIS * 1_000_000L;
        do {
            content = panel.contentForAutomation();
            boolean requiredSatisfied = requiredText == null
                    || containsTerminalAssertionText(content, requiredText);
            boolean forbiddenSatisfied = forbiddenText == null
                    || !containsTerminalAssertionText(content, forbiddenText);
            if (requiredSatisfied && forbiddenSatisfied) break;
            if (System.nanoTime() >= deadline) break;
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (true);
        if (requiredText != null && !containsTerminalAssertionText(content, requiredText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " is missing required text "
                            + quoted(requiredText) + ":\n" + content);
        }
        if (forbiddenText != null && containsTerminalAssertionText(content, forbiddenText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " contains forbidden text "
                            + quoted(forbiddenText) + ":\n" + content);
        }
        String safeArtifactName = validateCaptureName(artifactName);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(active.definition.puppetName() + "__" + safeArtifactName + ".txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal content " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_CONTENT_WRITTEN puppet={} variant={} artifact={} file={} chars={} required={} forbidden={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                file.getFileName(),
                content.length(),
                requiredText,
                forbiddenText
        );
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void assertTerminalPushEvidence(String artifactName, boolean reconnectExpected) {
        String safeArtifactName = validateCaptureName(artifactName);
        String evidence = requireTerminalPanel().assertPushEvidenceForAutomation(reconnectExpected);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(
                active.definition.puppetName() + "__" + safeArtifactName + "__push-evidence.txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, evidence, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal push evidence " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_PUSH_EVIDENCE_WRITTEN puppet={} variant={} artifact={} file={} chars={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                file.getFileName(),
                evidence.length()
        );
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties and features.workspace_panel_lookup %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean assertTerminalPropertiesEvidence(
            String artifactName,
            String expectedRendererId,
            String expectedTransportId,
            String expectedSurfaceMode,
            String expectedFontMode,
            String expectedCellsMode,
            Integer expectedConfiguredGuiScale,
            Integer expectedPanelGuiScaleOverride,
            String expectedRejectionCode,
            boolean retainedFrameExpected
    ) {
        String safeArtifactName = validateCaptureName(artifactName);
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMWorkspacePanelId ownerId;
        SFMTerminalPanel terminal;
        if (multiplexer.focusedPanelInstance() instanceof SFMTerminalPropertiesPanel properties) {
            ownerId = properties.ownerPanelId();
            terminal = properties.ownerTerminal().orElseThrow(() ->
                    new IllegalStateException("Focused terminal-properties panel has no live owner"));
        } else if (multiplexer.focusedPanelInstance() instanceof SFMTerminalPanel focusedTerminal) {
            ownerId = multiplexer.focusedPanelId();
            terminal = focusedTerminal;
        } else {
            throw new IllegalStateException("Terminal properties evidence requires a terminal or its properties panel");
        }
        if (multiplexer.panel(ownerId).orElse(null) != terminal) {
            throw new IllegalStateException("Terminal-properties owner identity retargeted unexpectedly");
        }
        SFMTerminalPropertiesSnapshot snapshot = terminal.propertiesSnapshot();
        if (snapshot.panelMetrics().isEmpty() || snapshot.workspaceMetrics().isEmpty()
                || snapshot.acceptedFrame().isEmpty() || snapshot.presentation().isEmpty()) {
            reportTerminalPropertiesWait(safeArtifactName, "telemetry-incomplete");
            return false;
        }
        if (snapshot.tuningPending()) {
            reportTerminalPropertiesWait(safeArtifactName,
                    "tuning-pending requested=" + snapshot.requested()
                            + " effective=" + snapshot.effective());
            return false;
        }
        assertTerminalProperty("renderer", expectedRendererId,
                snapshot.requestedRenderer(), snapshot.activeRenderer(),
                snapshot.acceptedFrame().orElseThrow().renderer());
        assertTerminalProperty("transport", expectedTransportId,
                snapshot.requestedTransport(), snapshot.activeTransport(),
                snapshot.acceptedFrame().orElseThrow().transport());
        assertTerminalMode("surface", expectedSurfaceMode,
                snapshot.requested().surfaceWidth() == 0 && snapshot.requested().surfaceHeight() == 0);
        assertTerminalMode("font", expectedFontMode, snapshot.requested().fontPixelSize() == 0);
        assertTerminalMode("cells", expectedCellsMode,
                snapshot.requested().columns() == 0 && snapshot.requested().rows() == 0);
        if (expectedConfiguredGuiScale != null
                && snapshot.configuredGuiScale() != expectedConfiguredGuiScale) {
            throw new IllegalStateException("Terminal configured GUI scale was "
                    + snapshot.configuredGuiScale() + " instead of " + expectedConfiguredGuiScale);
        }
        if (expectedPanelGuiScaleOverride != null) {
            int actualOverride = snapshot.panelMetrics().orElseThrow().panelGuiScaleOverride();
            if (actualOverride != expectedPanelGuiScaleOverride) {
                throw new IllegalStateException("Terminal panel GUI scale override was "
                        + actualOverride + " instead of " + expectedPanelGuiScaleOverride);
            }
        }
        var presentation = snapshot.presentation().orElseThrow();
        if (presentation.framesRejected() != 0 || presentation.receiverFailures() != 0) {
            throw new IllegalStateException("Terminal properties recorded rejected frames or receiver failures");
        }
        if (expectedRejectionCode == null) {
            if (snapshot.lastTypedRejection().isPresent()) {
                throw new IllegalStateException("Unexpected terminal tuning rejection: " + snapshot.lastRejection());
            }
        } else {
            SFMTerminalErrorCode expected = SFMTerminalErrorCode.valueOf(expectedRejectionCode);
            var rejection = snapshot.lastTypedRejection().orElseThrow(() ->
                    new IllegalStateException("Expected typed terminal tuning rejection " + expected));
            if (rejection.error().code() != expected) {
                throw new IllegalStateException("Terminal tuning rejection was "
                        + rejection.error().code() + " instead of " + expected);
            }
        }
        ActivePuppet.TerminalPresentationProgress baseline = active.terminalPresentations.get(ownerId);
        if (retainedFrameExpected) {
            if (baseline == null) {
                throw new IllegalStateException("No pre-rejection terminal frame baseline was recorded");
            }
            var accepted = snapshot.acceptedFrame().orElseThrow();
            var retainedPresentation = snapshot.presentation().orElseThrow();
            if (accepted.sequence() < baseline.frameSequence()
                    || !retainedPresentation.presentationGeneration().equals(baseline.generation())
                    || accepted.targetWidth() != baseline.panelWidth()
                    || accepted.targetHeight() != baseline.panelHeight()
                    || accepted.columns() != baseline.columns()
                    || accepted.rows() != baseline.rows()
                    || accepted.fontPixelSize() != baseline.fontPixelSize()) {
                throw new IllegalStateException(
                        "Rejected tuning did not retain the last accepted semantic frame: baseline="
                                + baseline + " accepted=" + accepted);
            }
        }
        String evidence = String.join("\n",
                "puppet=" + active.definition.puppetName(),
                "viewport_variant=" + active.viewportVariant.id(),
                "owner_panel_id=" + ownerId.value(),
                "focused_panel_id=" + multiplexer.focusedPanelId().value(),
                "retained_frame_expected=" + retainedFrameExpected,
                "retained_frame_baseline=" + (baseline == null ? 0 : baseline.frameSequence()),
                snapshot.artifact(),
                "");
        if ("auto".equals(expectedSurfaceMode)
                && !evidence.contains("properties_java_framebuffer_scale_one=true")) {
            // Opening, closing, or rescaling an adjacent panel changes the
            // terminal allocation immediately. The Rust frame follows over
            // the push lane; keep this action pending until that accepted
            // frame and the Java draw rectangle describe the same pixels.
            var frame = snapshot.acceptedFrame().orElseThrow();
            var draw = snapshot.javaDrawPhysicalBounds().orElse(null);
            reportTerminalPropertiesWait(safeArtifactName,
                    "framebuffer-scale native=" + frame.nativeWidth() + "x" + frame.nativeHeight()
                            + " java=" + (draw == null ? "missing" : draw.width() + "x" + draw.height())
                            + " effective=" + snapshot.effective().surfaceWidth() + "x"
                            + snapshot.effective().surfaceHeight()
                            + " frame_sequence=" + frame.sequence());
            return false;
        }
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(active.definition.puppetName() + "__" + safeArtifactName
                + "__terminal-properties.txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, evidence, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal properties evidence " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_PROPERTIES_EVIDENCE_WRITTEN puppet={} variant={} artifact={} file={} chars={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                file.getFileName(),
                evidence.length());
        active.terminalPropertiesWaitState = "";
        return true;
    }
{% endif %}
{% endif %}
{% endif %}

    @Override
    public boolean publishIntegratedServerToLan() {
        if (active.integratedServerPublishFailure != null) {
            throw new IllegalStateException(
                    "Could not publish the integrated server to LAN",
                    active.integratedServerPublishFailure
            );
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isReady()) {
            return false;
        }
        if (server.isPublished()) {
            return true;
        }
        if (!active.integratedServerPublishRequested) {
            active.integratedServerPublishRequested = true;
            server.execute(() -> {
                try {
                    int port = HttpUtil.getAvailablePort();
                    if (!server.publishServer(GameType.CREATIVE, false, port)) {
                        throw new IllegalStateException("Integrated server rejected LAN publication on port " + port);
                    }
                    SFM.LOGGER.info(
                            "SFM_GAME_PUPPET_LAN_PUBLISHED puppet={} port={}",
                            active.definition.puppetName(),
                            port
                    );
                } catch (Throwable failure) {
                    active.integratedServerPublishFailure = failure;
                }
            });
        }
        return false;
    }
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private void reportTerminalPropertiesWait(String artifactName, String state) {
        String identified = artifactName + " " + state;
        if (identified.equals(active.terminalPropertiesWaitState)) return;
        active.terminalPropertiesWaitState = identified;
        SFM.LOGGER.info("SFM_GAME_PUPPET_TERMINAL_PROPERTIES_WAIT artifact={} state={}", artifactName, state);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public boolean clickTerminalPropertiesControl(String operation) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        if (!(multiplexer.focusedPanelInstance() instanceof SFMTerminalPropertiesPanel properties)) {
            throw new IllegalStateException("Expected a focused terminal-properties panel before clicking a control");
        }
        SFMTerminalTuningOperation parsed;
        try {
            parsed = SFMTerminalTuningOperation.valueOf(operation);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown terminal tuning operation " + operation, error);
        }
        SFMScreenPanelBounds control = properties.controlBoundsForAutomation(parsed).orElseThrow(() ->
                new IllegalStateException("Terminal-properties control is not rendered: " + operation));
        SFMWorkspacePanelId panelId = multiplexer.focusedPanelId();
        SFMScreenPanelBounds panel = multiplexer.panelBounds(panelId);
        SFMScreenPanelBounds globalControl = multiplexer.measure(panelId, control)
                .map(metrics -> metrics.globalGuiLogicalBounds())
                .orElseThrow(() -> new IllegalStateException(
                        "Terminal-properties control could not be mapped to the workspace: " + operation));
        double x = globalControl.x() + globalControl.width() / 2D;
        double y = globalControl.y() + globalControl.height() / 2D;
        if (panel == null || x < panel.x() || x >= panel.x() + panel.width()) {
            throw new IllegalStateException(
                    "Terminal-properties control is horizontally outside the visible panel: " + operation);
        }
        if (y < panel.y() || y >= panel.y() + panel.height()) {
            double panelX = panel.x() + panel.width() / 2D;
            double panelY = panel.y() + panel.height() / 2D;
            double direction = y < panel.y() ? 1D : -1D;
            if (!multiplexer.mouseScrolled(panelX, panelY, direction)) {
                throw new IllegalStateException(
                        "Terminal-properties control could not be scrolled into view: " + operation);
            }
            return false;
        }
        if (!multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Terminal-properties panel rejected control click: " + operation);
        }
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ResourceLocation expectedFocus = new ResourceLocation(
                "sfm", "terminal/properties/" + parsed.name().toLowerCase(java.util.Locale.ROOT));
        ResourceLocation actualFocus = properties.widgetHost().orElseThrow()
                .focusedElementId().orElseThrow(() -> new IllegalStateException(
                        "Clicked terminal-properties control did not acquire focus: " + operation));
        if (!expectedFocus.equals(actualFocus)) {
            throw new IllegalStateException("Clicked terminal-properties control focused "
                    + actualFocus + " instead of " + expectedFocus);
        }
        return true;
    }
{% endif %}
{% endif %}
{% endif %}

    @Override
    public boolean runGameTest(String testName) {
        if (!startGameTest(testName)) {
            return false;
        }
        return waitForGameTest(testName);
    }

    private void requireSelectedGameTest(String testName) {
        if (!testName.equals(active.gameTestName)) {
            throw new IllegalStateException(
                    "Game puppet already selected GameTest " + active.gameTestName
                            + " and cannot operate " + testName
            );
        }
    }
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private static void assertTerminalProperty(
            String axis,
            String expected,
            String requested,
            String active,
            String accepted
    ) {
        if (!expected.equals(requested) || !expected.equals(active) || !expected.equals(accepted)) {
            throw new IllegalStateException("Terminal " + axis + " identities were requested=" + requested
                    + ", active=" + active + ", accepted=" + accepted + " instead of " + expected);
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_properties %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private static void assertTerminalMode(String axis, String expected, boolean automatic) {
        String actual = automatic ? "auto" : "manual";
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Terminal " + axis + " mode was " + actual + " instead of " + expected);
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void assertTerminalPresentationEvidence(
            String artifactName,
            String rendererId,
            String transportId,
            String requiredContentLine,
            boolean initialDefaultExpected,
            boolean freshPresentationExpected,
            boolean panelResizeExpected
    ) {
        String safeArtifactName = validateCaptureName(artifactName);
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMWorkspacePanelId panelId = multiplexer.focusedPanelId();
        SFMScreenPanelBounds bounds = multiplexer.panelContentBounds(panelId);
        if (bounds == null) {
            throw new IllegalStateException("Focused terminal panel has no allocated content bounds");
        }
        SFMTerminalPresentationPuppetProbe.Observation observation =
                SFMTerminalPresentationPuppetProbe.observe(
                        panel,
                        multiplexer,
                        panelId,
                        rendererId,
                        transportId,
                        requiredContentLine
                );
        ActivePuppet.TerminalPresentationProgress previous =
                active.terminalPresentations.get(panelId);
        boolean initialDefaultAsserted = initialDefaultExpected
                && previous == null
                && "rust-cpu-fontdue".equals(rendererId)
                && "full-png".equals(transportId);
        if (initialDefaultExpected && !initialDefaultAsserted) {
            throw new IllegalStateException(
                    "Initial terminal presentation was not the first CPU/full-PNG observation");
        }
        boolean sameSession = previous == null || previous.sessionId().equals(observation.sessionId());
        boolean sameConnectionEpoch = previous == null
                || previous.connectionEpoch().equals(observation.connectionEpoch());
        boolean sameSessionEpoch = previous == null
                || previous.sessionEpoch().equals(observation.sessionEpoch());
        boolean terminalSequenceAdvanced = previous == null
                || observation.terminalSequence() > previous.terminalSequence();
        boolean frameSequenceContinuous = previous == null
                || !previous.generation().equals(observation.presentationGeneration())
                || observation.latestFrameSequence() > previous.frameSequence();
        boolean panelDimensionsChanged = previous != null
                && (previous.panelWidth() != observation.targetPanelWidth()
                || previous.panelHeight() != observation.targetPanelHeight());
        if (!sameSession || !sameConnectionEpoch || !sameSessionEpoch) {
            throw new IllegalStateException("Terminal presentation switch replaced the PTY/session identity");
        }
        if (!terminalSequenceAdvanced) {
            throw new IllegalStateException("Terminal sequence did not advance across the live witness");
        }
        if (!frameSequenceContinuous) {
            throw new IllegalStateException("Frame sequence did not advance within the active presentation generation");
        }
        if (panelResizeExpected && !panelDimensionsChanged) {
            throw new IllegalStateException("Expected a real panel resize before presentation evidence");
        }
        if (previous != null && freshPresentationExpected) {
            if (previous.generation().equals(observation.presentationGeneration())) {
                throw new IllegalStateException("Terminal presentation generation did not change for "
                        + rendererId + " / " + transportId);
            }
            if (observation.fullResyncFrames() <= previous.fullResyncFrames()) {
                throw new IllegalStateException("Terminal presentation switch did not accept a fresh full resync");
            }
        } else if (previous != null) {
            if (!previous.generation().equals(observation.presentationGeneration())
                    || observation.fullResyncFrames() < previous.fullResyncFrames()) {
                throw new IllegalStateException(
                        "Terminal presentation generation changed or its full-resync counter regressed");
            }
        }
        active.terminalPresentations.put(panelId, new ActivePuppet.TerminalPresentationProgress(
                observation.presentationGeneration(),
                observation.fullResyncFrames(),
                observation.sessionId(),
                observation.connectionEpoch(),
                observation.sessionEpoch(),
                observation.terminalSequence(),
                observation.latestFrameSequence(),
                observation.targetPanelWidth(),
                observation.targetPanelHeight(),
                observation.logicalColumns(),
                observation.logicalRows(),
                observation.fontPixelSize()));

        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path contentFile = directory.resolve(
                active.definition.puppetName() + "__" + safeArtifactName + ".txt");
        Path evidenceFile = directory.resolve(
                active.definition.puppetName() + "__" + safeArtifactName + "__push-evidence.txt");
        boolean generationChanged = previous != null
                && !previous.generation().equals(observation.presentationGeneration());
        boolean fullResyncAdvanced = previous != null
                && observation.fullResyncFrames() > previous.fullResyncFrames();
        String evidence = String.join("\n",
                "puppet=" + active.definition.puppetName(),
                "viewport_variant=" + active.viewportVariant.id(),
                "requested_gui_scale=" + active.viewportVariant.requestedScaleName(),
                "effective_gui_scale=" + active.viewportObservation.effectiveGuiScale(),
                "panel_id=" + panelId.value(),
                "initial_default_expected=" + initialDefaultExpected,
                "initial_default_asserted=" + initialDefaultAsserted,
                "fresh_presentation_expected=" + freshPresentationExpected,
                "panel_resize_expected=" + panelResizeExpected,
                "panel_dimensions_changed=" + panelDimensionsChanged,
                "presentation_ui_selection_count="
                        + active.terminalPresentationUiSelections.getOrDefault(panelId, 0),
                "previous_presentation_generation=" + (previous == null ? "none" : previous.generation()),
                "previous_full_resync_frames=" + (previous == null ? 0 : previous.fullResyncFrames()),
                "previous_session_id=" + (previous == null ? "none" : previous.sessionId()),
                "previous_connection_epoch=" + (previous == null ? "none" : previous.connectionEpoch()),
                "previous_session_epoch=" + (previous == null ? "none" : previous.sessionEpoch()),
                "previous_terminal_sequence=" + (previous == null ? 0 : previous.terminalSequence()),
                "previous_frame_sequence=" + (previous == null ? 0 : previous.frameSequence()),
                "session_id_unchanged=" + sameSession,
                "connection_epoch_unchanged=" + sameConnectionEpoch,
                "session_epoch_unchanged=" + sameSessionEpoch,
                "terminal_sequence_advanced=" + terminalSequenceAdvanced,
                "frame_sequence_continuity=" + frameSequenceContinuous,
                "presentation_generation_changed=" + generationChanged,
                "full_resync_advanced=" + fullResyncAdvanced,
                "content_asserted=" + (requiredContentLine != null),
                "required_content_line=" + (requiredContentLine == null ? "none" : requiredContentLine),
                panel.propertiesSnapshot().artifact(),
                observation.artifact());
        if (active.viewportVariant.guiScale() == 7
                && active.viewportObservation.effectiveGuiScale() != 7) {
            throw new IllegalStateException("GUI-scale-7 presentation run was clamped to effective scale "
                    + active.viewportObservation.effectiveGuiScale());
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(contentFile, observation.content(), StandardCharsets.UTF_8);
            Files.writeString(evidenceFile, evidence, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal presentation artifacts", error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_CONTENT_WRITTEN puppet={} variant={} artifact={} file={} chars={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                contentFile.getFileName(),
                observation.content().length()
        );
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_PUSH_EVIDENCE_WRITTEN puppet={} variant={} artifact={} file={} chars={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                safeArtifactName,
                evidenceFile.getFileName(),
                evidence.length()
        );
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_presentation_actions and features.workspace_panel_measurement %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void selectTerminalPresentationThroughUi(boolean rendererAxis, String optionId) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMWorkspacePanelId panelId = multiplexer.focusedPanelId();
        SFMScreenPanelBounds bounds = multiplexer.panelContentBounds(panelId);
        if (bounds == null) {
            throw new IllegalStateException("Focused terminal panel has no content bounds");
        }
        var options = rendererAxis ? panel.rendererOptions() : panel.transportOptions();
        int targetIndex = -1;
        for (int index = 0; index < options.size(); index++) {
            String candidate = rendererAxis
                    ? panel.rendererOptions().get(index).id().wireId()
                    : panel.transportOptions().get(index).id().wireId();
            if (optionId.equals(candidate)) {
                targetIndex = index;
                boolean supported = rendererAxis
                        ? panel.rendererOptions().get(index).supported()
                        : panel.transportOptions().get(index).supported();
                if (!supported) {
                    throw new IllegalStateException("Presentation UI option is unavailable: " + optionId);
                }
                break;
            }
        }
        if (targetIndex < 0) {
            throw new IllegalStateException("Presentation UI omitted option: " + optionId);
        }

        int controlHeight = minecraft.font.lineHeight + 6;
        int controlWidth = Math.min(380, Math.max(150, bounds.width() - 12));
        SFMScreenPanelBounds localControl = new SFMScreenPanelBounds(
                bounds.x() + bounds.width() - 6 - controlWidth,
                bounds.y() + 4,
                controlWidth,
                controlHeight);
        SFMScreenPanelBounds globalControl = multiplexer.measure(panelId, localControl)
                .map(metrics -> metrics.globalGuiLogicalBounds())
                .orElseThrow(() -> new IllegalStateException(
                        "Presentation selector could not be mapped to the workspace"));
        double controlX = globalControl.x() + globalControl.width() / 2D;
        double controlY = globalControl.y() + globalControl.height() / 2D;
        multiplexer.mouseClicked(controlX, controlY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(controlX, controlY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.keyPressed(rendererAxis ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT, 0, 0);
        multiplexer.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        for (int index = 0; index < targetIndex; index++) {
            multiplexer.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        multiplexer.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        ResourceLocation expectedFocus = new ResourceLocation("sfm", "terminal/presentation/select");
        ResourceLocation actualFocus = panel.widgetHost().orElseThrow()
                .focusedElementId().orElseThrow(() -> new IllegalStateException(
                        "Presentation selector lost keyboard focus"));
        if (!expectedFocus.equals(actualFocus)) {
            throw new IllegalStateException("Presentation UI focused " + actualFocus
                    + " instead of " + expectedFocus);
        }
        active.terminalPresentationUiSelections.merge(panelId, 1, Integer::sum);
    }
{% endif %}
{% endif %}
{% endif %}

    /**
     * Content assertions normally search the complete visible witness. A
     * {@code line:...} assertion searches trimmed terminal rows instead, so
     * a literal echoed in the prompt command is not mistaken for output.
     */
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}
    private static boolean containsTerminalAssertionText(String content, String assertionText) {
        if (assertionText.startsWith("line:")) {
            String assertedLine = assertionText.substring("line:".length()).strip();
            return content.lines().map(String::strip).anyMatch(assertedLine::equals);
        }
        return content.contains(assertionText);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void clickTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double x = bounds.x() + bounds.width() / 2D;
        double y = bounds.y() + bounds.height() / 2D;
        multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void dragTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double y = bounds.y() + bounds.height() / 2D;
        double fromX = bounds.x() + bounds.width() / 3D;
        double toX = bounds.x() + bounds.width() * 2D / 3D;
        multiplexer.mouseClicked(fromX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // A real GLFW drag is observed as pointer motion while the button is
        // held. Exercise that dispatch path directly so the puppet does not
        // depend on Screen's internal mouse-capture bookkeeping.
        multiplexer.mouseMoved(toX, y);
        multiplexer.mouseDragged(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, toX - fromX, 0D);
        multiplexer.mouseReleased(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void resizeTerminal(int columns, int rows) {
        requireTerminalPanel().resizeForAutomation(columns, rows);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_focus_gestures and features.workspace_panel_measurement %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void scrollTerminal(double delta) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        multiplexer.mouseScrolled(bounds.x() + bounds.width() / 2D,
                bounds.y() + bounds.height() / 2D, delta);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void pressTerminalKey(int keyCode) {
        pressTerminalKey(keyCode, 0);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void pressTerminalKey(int keyCode, int modifiers) {
        SFMTerminalPanel panel = requireTerminalPanel();
        boolean handled = panel.keyPressed(keyCode, 0, modifiers);
        if (handled) {
            panel.keyReleased(keyCode, 0, modifiers);
        }
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions and features.terminal_keyboard_input %}
{% if features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    @Override
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        requireTerminalPanel().pressKeyForAutomation(keyCode, modifiers);
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    @Override
    public void pressFileExplorerKey(int keyCode) {
        requireFileExplorerPanel().keyPressed(keyCode, 0, 0);
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    @Override
    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        requireFileExplorerPanel().acceptSnapshot(snapshot);
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    @Override
    public boolean isFileExplorerOpen() {
        try {
            requireFileExplorerPanel();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

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
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

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
        int focusedIndex = multiplexer.panelIds().indexOf(multiplexer.focusedPanelId());
        SFMReadOnlyTextPanel viewer = focusedIndex >= 0
                && multiplexer.panels().get(focusedIndex) instanceof SFMReadOnlyTextPanel focusedViewer
                ? focusedViewer
                : multiplexer.panels().stream()
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
{% endif %}
{% if features.file_explorer and features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement and features.workspace_focus_tracking %}

    @Override
    public void assertFileExplorerPreviewFocus(boolean previewFocused) {
        SFMScreenMultiplexer multiplexer = requireFileExplorerMultiplexer();
        SFMWorkspacePanelId explorerId = null;
        SFMWorkspacePanelId viewerId = null;
        for (int index = 0; index < multiplexer.panels().size(); index++) {
            if (multiplexer.panels().get(index) instanceof SFMFileExplorerPanel) {
                explorerId = multiplexer.panelIds().get(index);
            } else if (multiplexer.panels().get(index) instanceof SFMReadOnlyTextPanel) {
                viewerId = multiplexer.panelIds().get(index);
            }
        }
        if (explorerId == null) throw new IllegalStateException("Workspace has no explorer panel");
        if (viewerId == null) throw new IllegalStateException("Workspace has no preview panel");
        SFMWorkspacePanelId expected = previewFocused ? viewerId : explorerId;
        if (!expected.equals(multiplexer.focusedPanelId())) {
            throw new IllegalStateException("Expected " + (previewFocused ? "preview" : "explorer")
                    + " focus but found " + multiplexer.focusedPanelId());
        }
    }
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

    private SFMScreenMultiplexer requireFileExplorerMultiplexer() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected file explorer workspace");
        }
        return multiplexer;
    }
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private SFMTerminalPanel requireTerminalPanel() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        if (multiplexer.focusedPanelInstance() instanceof SFMTerminalPanel terminal) return terminal;
        throw new IllegalStateException("Focused workspace panel is not a terminal");
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private SFMScreenPanelBounds terminalBounds(SFMScreenMultiplexer multiplexer) {
        SFMTerminalPanel panel = requireTerminalPanel();
        int panelIndex = multiplexer.panels().indexOf(panel);
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no terminal panel index");
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(multiplexer.panelIds().get(panelIndex));
        if (bounds == null) throw new IllegalStateException("Terminal panel has no allocated bounds");
        return bounds;
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.workspace_panels and features.workspace_widget_hosts and features.client_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}
{% if features.workspace_focus_tracking or features.workspace_panel_actions %}

    private SFMScreenMultiplexer requireTerminalMultiplexer() {
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) return multiplexer;
        throw new IllegalStateException("Expected terminal workspace");
    }
{% endif %}
{% endif %}
{% endif %}
{% if features.file_explorer and features.workspace_panels %}

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
{% endif %}
{% when "1.20", "1.20.1" %}
{% if features.command_palette %}

    @Override
    public void executeCommandPalette(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before executing a command");
        }
        palette.executeCommandForAutomation(command);
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void openTerminal() {
        SFMScreenMultiplexer.openToSide(minecraft.screen,
                new SFMTerminalPanel(SFMTerminalServiceFactory.createRepl()));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void executeTerminal(String command) {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.executeForAutomation(command);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void cancelTerminal() {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.cancelForAutomation();
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void restartRustTerminalServer() {
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMTerminalServiceFactory.stopOwnedRustServer();
        panel.reconnectForAutomation();
        try {
            SFMTerminalServiceFactory.startRustServer(null);
        } catch (Exception error) {
            throw new IllegalStateException("Rust terminal server restart failed", error);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void typeTerminalText(String text) {
        SFMTerminalPanel panel = requireTerminalPanel();
        for (int index = 0; index < text.length(); index++) {
            if (!panel.charTyped(text.charAt(index), 0)) {
                throw new IllegalStateException("Terminal rejected typed character at index " + index);
            }
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pasteTerminalText(String text) {
        requireTerminalPanel().pasteForAutomation(text);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        SFMTerminalPanel panel = requireTerminalPanel();
        String content = panel.contentForAutomation();
        if (requiredText != null && !content.contains(requiredText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " is missing required text "
                            + quoted(requiredText) + ":\n" + content);
        }
        if (forbiddenText != null && content.contains(forbiddenText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " contains forbidden text "
                            + quoted(forbiddenText) + ":\n" + content);
        }
        String safeArtifactName = validateCaptureName(artifactName);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(active.definition.puppetName() + "__" + safeArtifactName + ".txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal content " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_CONTENT_WRITTEN puppet={} artifact={} file={} chars={} required={} forbidden={}",
                active.definition.puppetName(),
                safeArtifactName,
                file.getFileName(),
                content.length(),
                requiredText,
                forbiddenText
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void clickTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double x = bounds.x() + bounds.width() / 2D;
        double y = bounds.y() + bounds.height() / 2D;
        multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void dragTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double y = bounds.y() + bounds.height() / 2D;
        double fromX = bounds.x() + bounds.width() / 3D;
        double toX = bounds.x() + bounds.width() * 2D / 3D;
        multiplexer.mouseClicked(fromX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // A real GLFW drag is observed as pointer motion while the button is
        // held. Exercise that dispatch path directly so the puppet does not
        // depend on Screen's internal mouse-capture bookkeeping.
        multiplexer.mouseMoved(toX, y);
        multiplexer.mouseDragged(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, toX - fromX, 0D);
        multiplexer.mouseReleased(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void resizeTerminal(int columns, int rows) {
        requireTerminalPanel().resizeForAutomation(columns, rows);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void scrollTerminal(double delta) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        multiplexer.mouseScrolled(bounds.x() + bounds.width() / 2D,
                bounds.y() + bounds.height() / 2D, delta);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode) {
        pressTerminalKey(keyCode, 0);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode, int modifiers) {
        SFMTerminalPanel panel = requireTerminalPanel();
        boolean handled = panel.keyPressed(keyCode, 0, modifiers);
        if (handled) {
            panel.keyReleased(keyCode, 0, modifiers);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        requireTerminalPanel().pressKeyForAutomation(keyCode, modifiers);
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void pressFileExplorerKey(int keyCode) {
        requireFileExplorerPanel().keyPressed(keyCode, 0, 0);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        requireFileExplorerPanel().acceptSnapshot(snapshot);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void openFileExplorer(SFMFileExplorerSource source) {
        minecraft.setScreen(SFMFileExplorerWorkspace.create(minecraft.screen, source));
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public boolean isFileExplorerOpen() {
        try {
            requireFileExplorerPanel();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    private SFMScreenMultiplexer requireFileExplorerMultiplexer() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected file explorer workspace");
        }
        return multiplexer;
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMTerminalPanel requireTerminalPanel() {
        return requireTerminalMultiplexer().panels().stream()
                .filter(SFMTerminalPanel.class::isInstance)
                .map(SFMTerminalPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no terminal panel"));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenPanelBounds terminalBounds(SFMScreenMultiplexer multiplexer) {
        SFMTerminalPanel panel = requireTerminalPanel();
        int panelIndex = multiplexer.panels().indexOf(panel);
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no terminal panel index");
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(multiplexer.panelIds().get(panelIndex));
        if (bounds == null) throw new IllegalStateException("Terminal panel has no allocated bounds");
        return bounds;
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenMultiplexer requireTerminalMultiplexer() {
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) return multiplexer;
        throw new IllegalStateException("Expected terminal workspace");
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.command_palette %}

    @Override
    public void executeCommandPalette(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before executing a command");
        }
        palette.executeCommandForAutomation(command);
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void openTerminal() {
        SFMScreenMultiplexer.openToSide(minecraft.screen,
                new SFMTerminalPanel(SFMTerminalServiceFactory.createRepl()));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void executeTerminal(String command) {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.executeForAutomation(command);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void cancelTerminal() {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.cancelForAutomation();
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void restartRustTerminalServer() {
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMTerminalServiceFactory.stopOwnedRustServer();
        panel.reconnectForAutomation();
        try {
            SFMTerminalServiceFactory.startRustServer(null);
        } catch (Exception error) {
            throw new IllegalStateException("Rust terminal server restart failed", error);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void typeTerminalText(String text) {
        SFMTerminalPanel panel = requireTerminalPanel();
        for (int index = 0; index < text.length(); index++) {
            if (!panel.charTyped(text.charAt(index), 0)) {
                throw new IllegalStateException("Terminal rejected typed character at index " + index);
            }
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pasteTerminalText(String text) {
        requireTerminalPanel().pasteForAutomation(text);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        SFMTerminalPanel panel = requireTerminalPanel();
        String content = panel.contentForAutomation();
        if (requiredText != null && !content.contains(requiredText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " is missing required text "
                            + quoted(requiredText) + ":\n" + content);
        }
        if (forbiddenText != null && content.contains(forbiddenText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " contains forbidden text "
                            + quoted(forbiddenText) + ":\n" + content);
        }
        String safeArtifactName = validateCaptureName(artifactName);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(active.definition.puppetName() + "__" + safeArtifactName + ".txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal content " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_CONTENT_WRITTEN puppet={} artifact={} file={} chars={} required={} forbidden={}",
                active.definition.puppetName(),
                safeArtifactName,
                file.getFileName(),
                content.length(),
                requiredText,
                forbiddenText
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void clickTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double x = bounds.x() + bounds.width() / 2D;
        double y = bounds.y() + bounds.height() / 2D;
        multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void dragTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double y = bounds.y() + bounds.height() / 2D;
        double fromX = bounds.x() + bounds.width() / 3D;
        double toX = bounds.x() + bounds.width() * 2D / 3D;
        multiplexer.mouseClicked(fromX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // A real GLFW drag is observed as pointer motion while the button is
        // held. Exercise that dispatch path directly so the puppet does not
        // depend on Screen's internal mouse-capture bookkeeping.
        multiplexer.mouseMoved(toX, y);
        multiplexer.mouseDragged(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, toX - fromX, 0D);
        multiplexer.mouseReleased(toX, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void resizeTerminal(int columns, int rows) {
        requireTerminalPanel().resizeForAutomation(columns, rows);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void scrollTerminal(double delta) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        multiplexer.mouseScrolled(bounds.x() + bounds.width() / 2D,
                bounds.y() + bounds.height() / 2D, 0D, delta);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode) {
        pressTerminalKey(keyCode, 0);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode, int modifiers) {
        SFMTerminalPanel panel = requireTerminalPanel();
        boolean handled = panel.keyPressed(keyCode, 0, modifiers);
        if (handled) {
            panel.keyReleased(keyCode, 0, modifiers);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        requireTerminalPanel().pressKeyForAutomation(keyCode, modifiers);
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void pressFileExplorerKey(int keyCode) {
        requireFileExplorerPanel().keyPressed(keyCode, 0, 0);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        requireFileExplorerPanel().acceptSnapshot(snapshot);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void openFileExplorer(SFMFileExplorerSource source) {
        minecraft.setScreen(SFMFileExplorerWorkspace.create(minecraft.screen, source));
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public boolean isFileExplorerOpen() {
        try {
            requireFileExplorerPanel();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    private SFMScreenMultiplexer requireFileExplorerMultiplexer() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected file explorer workspace");
        }
        return multiplexer;
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMTerminalPanel requireTerminalPanel() {
        return requireTerminalMultiplexer().panels().stream()
                .filter(SFMTerminalPanel.class::isInstance)
                .map(SFMTerminalPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no terminal panel"));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenPanelBounds terminalBounds(SFMScreenMultiplexer multiplexer) {
        SFMTerminalPanel panel = requireTerminalPanel();
        int panelIndex = multiplexer.panels().indexOf(panel);
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no terminal panel index");
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(multiplexer.panelIds().get(panelIndex));
        if (bounds == null) throw new IllegalStateException("Terminal panel has no allocated bounds");
        return bounds;
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenMultiplexer requireTerminalMultiplexer() {
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) return multiplexer;
        throw new IllegalStateException("Expected terminal workspace");
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% when "26.1.2" %}
{% if features.command_palette %}

    @Override
    public void executeCommandPalette(String command) {
        if (!(minecraft.screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Expected command palette before executing a command");
        }
        palette.executeCommandForAutomation(command);
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void openTerminal() {
        SFMScreenMultiplexer.openToSide(minecraft.screen,
                new SFMTerminalPanel(SFMTerminalServiceFactory.createRepl()));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_repl_actions %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void executeTerminal(String command) {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.executeForAutomation(command);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void cancelTerminal() {
        SFMTerminalPanel panel = requireTerminalPanel();
        panel.cancelForAutomation();
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void restartRustTerminalServer() {
        SFMTerminalPanel panel = requireTerminalPanel();
        SFMTerminalServiceFactory.stopOwnedRustServer();
        panel.reconnectForAutomation();
        try {
            SFMTerminalServiceFactory.startRustServer(null);
        } catch (Exception error) {
            throw new IllegalStateException("Rust terminal server restart failed", error);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void typeTerminalText(String text) {
        SFMTerminalPanel panel = requireTerminalPanel();
        for (int index = 0; index < text.length(); index++) {
            if (!panel.charTyped(text.charAt(index), 0)) {
                throw new IllegalStateException("Terminal rejected typed character at index " + index);
            }
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pasteTerminalText(String text) {
        requireTerminalPanel().pasteForAutomation(text);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void writeTerminalContent(String artifactName, String requiredText, String forbiddenText) {
        SFMTerminalPanel panel = requireTerminalPanel();
        String content = panel.contentForAutomation();
        if (requiredText != null && !content.contains(requiredText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " is missing required text "
                            + quoted(requiredText) + ":\n" + content);
        }
        if (forbiddenText != null && content.contains(forbiddenText)) {
            throw new IllegalStateException(
                    "Terminal content artifact " + artifactName + " contains forbidden text "
                            + quoted(forbiddenText) + ":\n" + content);
        }
        String safeArtifactName = validateCaptureName(artifactName);
        Path directory = minecraft.gameDirectory.toPath().resolve("terminal-content");
        Path file = directory.resolve(active.definition.puppetName() + "__" + safeArtifactName + ".txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write terminal content " + file, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_TERMINAL_CONTENT_WRITTEN puppet={} artifact={} file={} chars={} required={} forbidden={}",
                active.definition.puppetName(),
                safeArtifactName,
                file.getFileName(),
                content.length(),
                requiredText,
                forbiddenText
        );
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void clickTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double x = bounds.x() + bounds.width() / 2D;
        double y = bounds.y() + bounds.height() / 2D;
        multiplexer.mouseClicked(leftButtonEvent(x, y), false);
        multiplexer.mouseReleased(leftButtonEvent(x, y));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void dragTerminal() {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        double y = bounds.y() + bounds.height() / 2D;
        double fromX = bounds.x() + bounds.width() / 3D;
        double toX = bounds.x() + bounds.width() * 2D / 3D;
        multiplexer.mouseClicked(leftButtonEvent(fromX, y), false);
        // A real GLFW drag is observed as pointer motion while the button is
        // held. Exercise that dispatch path directly so the puppet does not
        // depend on Screen's internal mouse-capture bookkeeping.
        multiplexer.mouseMoved(toX, y);
        multiplexer.mouseDragged(leftButtonEvent(toX, y), toX - fromX, 0D);
        multiplexer.mouseReleased(leftButtonEvent(toX, y));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void resizeTerminal(int columns, int rows) {
        requireTerminalPanel().resizeForAutomation(columns, rows);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_focus_gestures %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void scrollTerminal(double delta) {
        SFMScreenMultiplexer multiplexer = requireTerminalMultiplexer();
        SFMScreenPanelBounds bounds = terminalBounds(multiplexer);
        multiplexer.mouseScrolled(bounds.x() + bounds.width() / 2D,
                bounds.y() + bounds.height() / 2D, 0D, delta);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode) {
        pressTerminalKey(keyCode, 0);
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKey(int keyCode, int modifiers) {
        SFMTerminalPanel panel = requireTerminalPanel();
        boolean handled = panel.keyPressed(keyCode, 0, modifiers);
        if (handled) {
            panel.keyReleased(keyCode, 0, modifiers);
        }
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_remote or features.terminal_vox_runtime %}

    @Override
    public void pressTerminalKeyDirect(int keyCode, int modifiers) {
        requireTerminalPanel().pressKeyForAutomation(keyCode, modifiers);
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void pressFileExplorerKey(int keyCode) {
        requireFileExplorerPanel().keyPressed(keyCode, 0, 0);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot) {
        requireFileExplorerPanel().acceptSnapshot(snapshot);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public void openFileExplorer(SFMFileExplorerSource source) {
        minecraft.setScreen(SFMFileExplorerWorkspace.create(minecraft.screen, source));
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    @Override
    public boolean isFileExplorerOpen() {
        try {
            requireFileExplorerPanel();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
        multiplexer.mouseClicked(leftButtonEvent(mouseX, mouseY), false);
    }
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

    private SFMScreenMultiplexer requireFileExplorerMultiplexer() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected file explorer workspace");
        }
        return multiplexer;
    }
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMTerminalPanel requireTerminalPanel() {
        return requireTerminalMultiplexer().panels().stream()
                .filter(SFMTerminalPanel.class::isInstance)
                .map(SFMTerminalPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no terminal panel"));
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenPanelBounds terminalBounds(SFMScreenMultiplexer multiplexer) {
        SFMTerminalPanel panel = requireTerminalPanel();
        int panelIndex = multiplexer.panels().indexOf(panel);
        if (panelIndex < 0) throw new IllegalStateException("Workspace has no terminal panel index");
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(multiplexer.panelIds().get(panelIndex));
        if (bounds == null) throw new IllegalStateException("Terminal panel has no allocated bounds");
        return bounds;
    }
{% endif %}
{% endif %}
{% if features.workspace_panels %}
{% if features.terminal_local or features.terminal_remote or features.terminal_vox_runtime %}

    private SFMScreenMultiplexer requireTerminalMultiplexer() {
        if (minecraft.screen instanceof SFMScreenMultiplexer multiplexer) return multiplexer;
        throw new IllegalStateException("Expected terminal workspace");
    }
{% endif %}
{% endif %}
{% if features.legacy_file_explorer and features.workspace_panels %}

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
{% endif %}
{% endcase %}

    @Override
    public boolean isOverlay(Class<? extends Overlay> expectedType) {
        return expectedType.isInstance(minecraft.getOverlay());
    }

    @Override
    public boolean capture(
            String captureName,
            Component caption
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return capture(captureName, caption, false);
    }

    @Override
    public boolean captureWithHud(
            String captureName,
            Component caption
    ) {

        return capture(captureName, caption, true);
    }

    private boolean capture(
            String captureName,
            Component caption,
            boolean preserveHud
    ) {

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            // The source frame must first render with the requested HUD profile.
            state.frameBeforePreparation = SFMGamePuppetRenderHarness.completedFrames();
            if (preserveHud) {
                prepareOverlayPreservingCaptureHud();
            } else {
                prepareCleanCaptureHud();
            }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            // The source frame must first render with the clean HUD profile.
            prepareCleanCaptureHud();
{% endcase %}
            state.hudPrepared = true;
            return false;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        // Several client ticks can run before the next rendered frame, especially
        // while minimized/locked. A tick delay alone can capture an OLD screen.
        if (SFMGamePuppetRenderHarness.completedFrames() <= state.frameBeforePreparation) return false;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
        if (!state.requested) {
            state.requested = true;
            if (state.file.exists() && !state.file.delete()) {
                throw new IllegalStateException("Could not replace old screenshot " + state.file.getAbsolutePath());
            }
            queueCaptionedScreenshot(state);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
{% when "26.1.2" %}
            Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().position();
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    minecraft.gameRenderer.getMainCamera().getYRot(),
                    minecraft.gameRenderer.getMainCamera().getXRot(),
{% when "26.1.2" %}
                    minecraft.gameRenderer.getMainCamera().yRot(),
                    minecraft.gameRenderer.getMainCamera().xRot(),
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @Override
    public void writeArtifact(
            String artifactName,
            SFMGamePuppetArtifactFormat format,
            String contents
    ) {
        Path stagingDirectory = minecraft.gameDirectory.toPath().resolve("puppet-artifacts");
        SFMGamePuppetArtifactWriter.WrittenArtifact artifact;
        try {
            artifact = SFMGamePuppetArtifactWriter.write(
                    stagingDirectory,
                    active.definition.puppetName(),
                    active.viewportVariant.id(),
                    artifactName,
                    format,
                    contents
            );
        } catch (IOException error) {
            throw new IllegalStateException("Could not write game puppet artifact " + artifactName, error);
        }
        SFM.LOGGER.info(
                "SFM_GAME_PUPPET_ARTIFACT_WRITTEN puppet={} variant={} artifact={} format={} file={} bytes={}",
                active.definition.puppetName(),
                active.viewportVariant.id(),
                artifact.artifactName(),
                artifact.format().id(),
                artifact.path().getFileName(),
                artifact.bytes()
        );
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

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
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

{% when "26.1.2" %}
{% endcase %}
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2" %}
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
{% when "1.19.4" %}
    private void renderCaption(
            TextureTarget captureTarget,
            PuppetCaptureFigureCaptionLayout captionLayout
    ) {

        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            captureTarget.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(
                            0F,
                            (float) (captureTarget.width / captionLayout.guiScale()),
                            (float) (captureTarget.height / captionLayout.guiScale()),
                            0F,
                            1000F,
                            net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane()
                    )
            );
            modelView.setIdentity();
            modelView.translate(0D, 0D, 1000F - net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
{% when "1.20", "1.20.1" %}
    private void renderCaption(
            TextureTarget captureTarget,
            PuppetCaptureFigureCaptionLayout captionLayout
    ) {

        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            captureTarget.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(
                            0F,
                            (float) (captureTarget.width / captionLayout.guiScale()),
                            (float) (captureTarget.height / captionLayout.guiScale()),
                            0F,
                            1000F,
                            net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane()
                    ),
                    VertexSorting.ORTHOGRAPHIC_Z
            );
            modelView.setIdentity();
            modelView.translate(0D, 0D, 1000F - net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
{% when "1.20.2", "1.20.3", "1.20.4" %}
    private void renderCaption(
            TextureTarget captureTarget,
            PuppetCaptureFigureCaptionLayout captionLayout
    ) {

        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            captureTarget.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(
                            0F,
                            (float) (captureTarget.width / captionLayout.guiScale()),
                            (float) (captureTarget.height / captionLayout.guiScale()),
                            0F,
                            1000F,
                            net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()
                    ),
                    VertexSorting.ORTHOGRAPHIC_Z
            );
            modelView.setIdentity();
            modelView.translate(0D, 0D, 1000F - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
{% when "1.21", "1.21.1" %}
    private void renderCaption(
            TextureTarget captureTarget,
            PuppetCaptureFigureCaptionLayout captionLayout
    ) {

        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            captureTarget.bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(
                            0F,
                            (float) (captureTarget.width / captionLayout.guiScale()),
                            (float) (captureTarget.height / captionLayout.guiScale()),
                            0F,
                            1000F,
                            net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()
                    ),
                    VertexSorting.ORTHOGRAPHIC_Z
            );
            modelView.identity();
            modelView.translation(0F, 0F, 10000F - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
{% when "26.1.2" %}
    private void queueCaptionedScreenshot(PuppetCaptureState state) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            int y = SFMGamePuppetHarness.CAPTION_VERTICAL_PADDING;
            for (FormattedCharSequence line : captionLayout.lines()) {
                SFMFontUtils.draw(
                        guiGraphics,
                        minecraft.font,
                        line,
                        SFMGamePuppetHarness.CAPTION_HORIZONTAL_PADDING,
                        y,
                        0xFF000000,
                        false
                );
                y += minecraft.font.lineHeight;
            }
            guiGraphics.flush();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(originalProjection, VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableBlend();
        }
{% when "1.21", "1.21.1" %}
            GuiGraphics guiGraphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            int y = SFMGamePuppetHarness.CAPTION_VERTICAL_PADDING;
            for (FormattedCharSequence line : captionLayout.lines()) {
                SFMFontUtils.draw(
                        guiGraphics,
                        minecraft.font,
                        line,
                        SFMGamePuppetHarness.CAPTION_HORIZONTAL_PADDING,
                        y,
                        0xFF000000,
                        false
                );
                y += minecraft.font.lineHeight;
            }
            guiGraphics.flush();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(originalProjection, VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.disableBlend();
        }
{% when "26.1.2" %}
        PuppetCaptionedScreenshotComposer.write(minecraft, active, state);
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels and features.workspace_panel_lookup and features.workspace_panel_measurement %}

    @Override
    public boolean clickWorkspacePanel(int panelIndex) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM screen multiplexer before focusing a panel");
        }
        var visible = multiplexer.visiblePanelEntries();
        if (panelIndex < 0 || panelIndex >= visible.size()) {
            throw new IllegalArgumentException("Workspace panel index is out of range: " + panelIndex);
        }
        var entry = visible.get(panelIndex);
        SFMScreenPanelBounds bounds = multiplexer.panelBounds(entry.id());
        if (bounds == null) throw new IllegalStateException("Visible workspace panel has no allocated bounds");
        double mouseX = bounds.x() + bounds.width() / 2D;
        double mouseY = bounds.y() + bounds.height() / 2D;
        multiplexer.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        return multiplexer.focusedPanelId().equals(entry.id());
    }
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.workspace_panels %}

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
{% endif %}
{% when "26.1.2" %}
{% if features.workspace_panels %}

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
        multiplexer.mouseClicked(leftButtonEvent(mouseX, mouseY), false);
        return multiplexer.focusedPanel() == panelIndex;
    }
{% endif %}
{% endcase %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void openFalsifiedInventoryTimeline() {
        minecraft.setScreen(SFMScreenMultiplexer.create(
                minecraft.screen,
                new SFMTimelinePanel(new SFMFalsifiedInventoryReplayPanel(), 20)
        ));
    }
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void seekFalsifiedInventoryTimeline(int timestep) {
        requireFalsifiedInventoryTimeline().seek(timestep);
    }
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void seekFalsifiedInventoryKeyframePosition(double position) {
        requireFalsifiedInventoryTimeline().seekKeyframePosition(position);
    }
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void seekFalsifiedInventoryElapsedTicks(double ticks) {
        requireFalsifiedInventoryTimeline().seekElapsedTicks(ticks);
    }
{% endif %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void jumpFalsifiedInventoryKeyframe(int direction) {
        requireFalsifiedInventoryTimeline().jumpKeyframe(direction);
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.timeline_panels and features.workspace_panels %}

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
{% endif %}
{% when "26.1.2" %}
{% if features.timeline_panels and features.workspace_panels %}

    @Override
    public void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep) {
        SFMTimelinePanel timeline = requireFalsifiedInventoryTimeline();
        SFMScreenMultiplexer multiplexer = (SFMScreenMultiplexer) minecraft.screen;
        timeline.seek(fromTimestep);
        double fromX = timeline.xForTimestep(fromTimestep);
        double toX = timeline.xForTimestep(toTimestep);
        double y = timeline.trackY();
        multiplexer.mouseClicked(leftButtonEvent(fromX, y), false);
        multiplexer.mouseDragged(leftButtonEvent(toX, y), toX - fromX, 0D);
        multiplexer.mouseReleased(leftButtonEvent(toX, y));
        if (timeline.model().current() != toTimestep) {
            throw new IllegalStateException(
                    "Timeline drag selected " + timeline.model().current() + " instead of " + toTimestep
            );
        }
    }
{% endif %}
{% endcase %}
{% if features.timeline_panels and features.workspace_panels %}

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
{% endif %}

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public void openManagerProgramEditor() {

        if (!(minecraft.screen instanceof ManagerScreen managerScreen)) {
            throw new IllegalStateException("Expected ManagerScreen before opening the program editor");
        }
        List<Button> buttons = managerScreen.getButtonsForJEIExclusionZones();
        if (buttons.size() < 2 || buttons.get(1) == null || !buttons.get(1).visible) {
            throw new IllegalStateException("Manager program editor button is unavailable");
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        buttons.get(1).onPress();
{% when "26.1.2" %}
        buttons.get(1).onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
{% endcase %}
    }
{% if features.workspace_panels %}

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
{% endif %}
{% if features.workspace_panels %}

    @Override
    public void setColorInputHueSaturation(double hue, double saturation) {
        SFMColorInputPanel panel = requireColorInput();
        SFMColorInputPanelLayout.Rect field = panel.layout().hueSaturation();
        clickWorkspace(field.x() + hue * (field.width() - 1D),
                field.y() + (1D - saturation) * (field.height() - 1D));
    }
{% endif %}
{% if features.workspace_panels %}

    @Override
    public void setColorInputValue(double value) {
        SFMColorInputPanelLayout.Rect slider = requireColorInput().layout().valueSlider();
        clickWorkspace(slider.x() + value * (slider.width() - 1D), slider.y() + slider.height() / 2D);
    }
{% endif %}
{% if features.workspace_panels %}

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
{% endif %}
{% if features.workspace_panels %}

    @Override
    public void selectColorInputRecent(int index) {
        SFMColorInputPanel panel = requireColorInput();
        SFMColorInputPanelLayout.Rect recents = panel.layout().recents();
        int size = Math.min(20, recents.height());
        clickWorkspace(recents.x() + index * (size + 4) + size / 2D, recents.y() + size / 2D);
    }
{% endif %}
{% if features.workspace_panels %}

    @Override public void resetColorInput() { clickRect(requireColorInput().layout().reset()); }

    @Override
    public void setColorInputHex(String hex, boolean rgbaOrder) {
        requireColorInput().setHexValue(hex,
                rgbaOrder ? SFMArgbColor.HexOrder.RGBA : SFMArgbColor.HexOrder.ARGB);
    }
{% endif %}
{% if features.workspace_panels %}

    @Override
    public void confirmColorInput() {
        SFMColorInputPanel panel = requireColorInput();
        clickRect(panel.layout().confirm());
        if (panel.confirmedResult() == null) throw new IllegalStateException("Colour input did not confirm a typed result");
    }
{% endif %}
{% if features.workspace_panels %}

    private SFMColorInputPanel requireColorInput() {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM workspace for colour input");
        }
        return multiplexer.panels().stream().filter(SFMColorInputPanel.class::isInstance)
                .map(SFMColorInputPanel.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no colour input panel"));
    }
{% endif %}
{% if features.workspace_panels %}

    private void clickRect(SFMColorInputPanelLayout.Rect rect) {
        clickWorkspace(rect.x() + rect.width() / 2D, rect.y() + rect.height() / 2D);
    }
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.workspace_panels %}

    private void clickWorkspace(double x, double y) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM workspace before mouse input");
        }
        multiplexer.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        multiplexer.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
{% endif %}
{% when "26.1.2" %}
{% if features.workspace_panels %}

    private void clickWorkspace(double x, double y) {
        if (!(minecraft.screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected SFM workspace before mouse input");
        }
        multiplexer.mouseClicked(leftButtonEvent(x, y), false);
        multiplexer.mouseReleased(leftButtonEvent(x, y));
    }
{% endif %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private BlockPos absolute(BlockPos local) {

        BlockPos origin = active.gameTestOrigin;
        if (origin == null) {
            throw new IllegalStateException("No completed GameTest origin is available");
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return origin.offset(local);
{% when "26.1.2" %}
        // SFMGameTestHelper compensates for this version's GameTest coordinate
        // convention by passing relativePos.below() to the vanilla helper.  Puppet
        // positions share the public SFM GameTest coordinate system, so they must
        // apply the same conversion before addressing the live world.
        return origin.offset(local.below());
{% endcase %}
    }

    private void prepareCleanCaptureHud() {
        // Retain actual in-game screens, but remove the transient player HUD,
        // chat history, and queued toast notifications from visual artifacts.
        // This setting is scoped to the isolated preview run directory and is
        // never persisted to a developer's normal game options.
        minecraft.options.hideGui = true;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
    }

    private void prepareOverlayPreservingCaptureHud() {
        // HUD-backed SFM overlays are skipped whenever hideGui is true. Keep the
        // deterministic toast/chat cleanup from ordinary captures while making
        // the HUD renderable for the next source frame.
        minecraft.options.hideGui = false;
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
    }

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
    }

{% when "26.1.2" %}
        minecraft.getToastManager().clear();
        minecraft.gui.getChat().clearMessages(false);
    }

{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        minecraft.player.moveTo(position.x, position.y, position.z, yaw, pitch);
{% when "26.1.2" %}
        minecraft.player.setPos(position.x, position.y, position.z);
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
{% endcase %}
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

    private static String quoted(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }

}
