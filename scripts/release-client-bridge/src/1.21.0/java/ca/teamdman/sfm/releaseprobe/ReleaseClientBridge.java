package ca.teamdman.sfm.releaseprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/** Test-only, file-driven witness for an unchanged NeoForge production SFM JAR. */
@Mod("sfmreleaseprobe")
public final class ReleaseClientBridge {
    private static final String CONTROL_PROPERTY = "sfm.releaseProbe.controlDirectory";
    private static final String TITLE_SCREENSHOT_NAME = "sfm-release-client-title.png";
    private static final String WORLD_SCREENSHOT_NAME = "sfm-release-client-world.png";
    private static final ResourceLocation MANAGER_ID = ResourceLocation.fromNamespaceAndPath("sfm", "manager");
    private static final int TITLE_FRAMES_BEFORE_CAPTURE = 90;
    private static final int WORLD_FRAMES_BEFORE_CAPTURE = 30;

    private final Path controlDirectory;
    private final Path gameDirectory;
    private final String runId;
    private final String expectedSfmSha256;
    private final String captureMode;
    private final String worldId;
    private int renderedTitleFrames;
    private int renderedWorldFrames;
    private boolean renderEventObserved;
    private boolean clientTickObserved;
    private boolean worldCreationStarted;
    private boolean worldSetupRequested;
    private boolean worldRenderObserved;
    private boolean clientBlockSynced;
    private boolean clientAimLogged;
    private boolean rayHit;
    private volatile boolean serverBlockPlaced;
    private volatile BlockPos managerPosition;
    private volatile String worldSetupFailure;
    private boolean captureQueued;
    private boolean resultWritten;

    public ReleaseClientBridge() {
        try {
            controlDirectory = Path.of(System.getProperty(CONTROL_PROPERTY, "")).toAbsolutePath().normalize();
            Properties request = new Properties();
            try (InputStream input = Files.newInputStream(controlDirectory.resolve("request.properties"))) {
                request.load(input);
            }
            runId = request.getProperty("run_id", "");
            expectedSfmSha256 = request.getProperty("sfm_sha256", "").toLowerCase();
            captureMode = request.getProperty("capture", "");
            if (!runId.matches("[0-9a-fA-F-]{36}") || !expectedSfmSha256.matches("[0-9a-f]{64}")
                    || !("title".equals(captureMode) || "world".equals(captureMode))) {
                throw new IllegalArgumentException("Invalid release-client request");
            }
            worldId = "sfm_release_probe_" + runId.replace("-", "");
            gameDirectory = controlDirectory.getParent().resolve("game").toAbsolutePath().normalize();
            if (!Files.isDirectory(gameDirectory) || !Files.isDirectory(gameDirectory.resolve("mods"))) {
                throw new IllegalArgumentException("Missing isolated game directory");
            }
            String actualSfmSha256 = sha256(gameDirectory.resolve("mods").resolve("sfm.jar"));
            if (!expectedSfmSha256.equals(actualSfmSha256)) {
                throw new IllegalArgumentException("SFM JAR hash mismatch");
            }
            System.out.println("SFM_RELEASE_CLIENT_BRIDGE_READY run_id=" + runId
                    + " sfm_sha256=" + actualSfmSha256);
            NeoForge.EVENT_BUS.register(this);
        } catch (Exception failure) {
            throw new IllegalStateException("Release-client bridge setup failed", failure);
        }
    }

    @SubscribeEvent
    public void onScreenRendered(ScreenEvent.Render.Post event) {
        if (!renderEventObserved) {
            renderEventObserved = true;
            System.out.println("SFM_RELEASE_CLIENT_SCREEN_RENDER_OBSERVED run_id=" + runId
                    + " screen=" + event.getScreen().getClass().getName());
        }
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        renderedTitleFrames++;
        if (!"title".equals(captureMode) || captureQueued || renderedTitleFrames < TITLE_FRAMES_BEFORE_CAPTURE) {
            return;
        }
        if (!ModList.get().isLoaded("sfm")) {
            fail("sfm_mod_not_loaded");
            return;
        }
        captureQueued = true;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Screenshot.grab(minecraft.gameDirectory, TITLE_SCREENSHOT_NAME, minecraft.getMainRenderTarget(),
                    message -> System.out.println("SFM_RELEASE_CLIENT_SCREENSHOT_MESSAGE run_id=" + runId
                            + " message=" + message));
            System.out.println("SFM_RELEASE_CLIENT_CAPTURE_QUEUED run_id=" + runId
                    + " rendered_title_frames=" + renderedTitleFrames);
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            fail("capture_exception");
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (!clientTickObserved) {
            clientTickObserved = true;
            System.out.println("SFM_RELEASE_CLIENT_TICK_OBSERVED run_id=" + runId);
        }
        if (resultWritten) {
            return;
        }
        if ("world".equals(captureMode)) {
            advanceWorldWitness();
        }
        if (!captureQueued || resultWritten) {
            return;
        }
        Path screenshot = gameDirectory.resolve("screenshots").resolve(screenshotName());
        try {
            if (!Files.isRegularFile(screenshot) || Files.size(screenshot) == 0) {
                return;
            }
            writeResult("passed", "none");
        } catch (IOException failure) {
            failure.printStackTrace(System.err);
            fail("screenshot_io_error");
        }
    }

    @SubscribeEvent
    public void onWorldRendered(RenderLevelStageEvent event) {
        if (!"world".equals(captureMode) || resultWritten ||
                event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!worldRenderObserved) {
            worldRenderObserved = true;
            System.out.println("SFM_RELEASE_CLIENT_WORLD_RENDER_OBSERVED run_id=" + runId);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!clientBlockSynced || !serverBlockPlaced || minecraft.screen != null ||
                !isAimedAtManager(minecraft)) {
            return;
        }
        renderedWorldFrames++;
        if (captureQueued || renderedWorldFrames < WORLD_FRAMES_BEFORE_CAPTURE) {
            return;
        }
        captureQueued = true;
        try {
            Screenshot.grab(minecraft.gameDirectory, WORLD_SCREENSHOT_NAME, minecraft.getMainRenderTarget(),
                    message -> System.out.println("SFM_RELEASE_CLIENT_SCREENSHOT_MESSAGE run_id=" + runId
                            + " message=" + message));
            System.out.println("SFM_RELEASE_CLIENT_CAPTURE_QUEUED run_id=" + runId
                    + " rendered_world_frames=" + renderedWorldFrames);
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            fail("capture_exception");
        }
    }

    private void advanceWorldWitness() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!worldCreationStarted) {
            if (renderedTitleFrames < 2 || !(minecraft.screen instanceof TitleScreen)) {
                return;
            }
            if (!ModList.get().isLoaded("sfm")) {
                fail("sfm_mod_not_loaded");
                return;
            }
            try {
                createScratchWorld(minecraft);
            } catch (Exception failure) {
                failure.printStackTrace(System.err);
                fail("world_creation_exception");
            }
            return;
        }
        if (worldSetupFailure != null) {
            fail(worldSetupFailure);
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isRunning() || minecraft.player == null ||
                minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (!worldSetupRequested) {
            worldSetupRequested = true;
            System.out.println("SFM_RELEASE_CLIENT_WORLD_READY run_id=" + runId + " world_id=" + worldId);
            server.execute(() -> placeManager(server));
            return;
        }
        BlockPos position = managerPosition;
        if (!serverBlockPlaced || position == null) {
            return;
        }
        if (!clientBlockSynced) {
            Block manager = BuiltInRegistries.BLOCK.getOptional(MANAGER_ID).orElse(null);
            if (manager == null || minecraft.level.getBlockState(position).getBlock() != manager) {
                return;
            }
            clientBlockSynced = true;
            System.out.println("SFM_RELEASE_CLIENT_BLOCK_SYNCED run_id=" + runId + " block=sfm:manager"
                    + " pos=" + position.getX() + "," + position.getY() + "," + position.getZ());
        }
        if (!rayHit) {
            aimClientAtManager(minecraft, position);
            if (isAimedAtManager(minecraft)) {
                rayHit = true;
                System.out.println("SFM_RELEASE_CLIENT_RAY_HIT_OBSERVED run_id=" + runId + " block=sfm:manager");
            }
        }
    }

    private void createScratchWorld(Minecraft minecraft) throws IOException {
        if (!minecraft.gameDirectory.toPath().toRealPath().equals(gameDirectory.toRealPath()) ||
                minecraft.level != null || minecraft.getSingleplayerServer() != null) {
            throw new IllegalStateException("Not at the isolated scratch title screen");
        }
        Path savesDirectory = gameDirectory.resolve("saves").normalize();
        Path save = savesDirectory.resolve(worldId).normalize();
        if (!save.getParent().equals(savesDirectory) || Files.exists(save)) {
            throw new IllegalStateException("Scratch world already exists or escapes game directory");
        }
        WorldOptions worldOptions = new WorldOptions(0L, false, false);
        LevelSettings levelSettings = new LevelSettings(
                "SFM release bridge " + runId, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, new GameRules(), WorldDataConfiguration.DEFAULT);
        // createFreshLevel may pump client ticks, so mark the one-shot before calling it.
        worldCreationStarted = true;
        System.out.println("SFM_RELEASE_CLIENT_WORLD_CREATE_REQUESTED run_id=" + runId
                + " world_id=" + worldId);
        minecraft.createWorldOpenFlows().createFreshLevel(worldId, levelSettings, worldOptions,
                registryAccess -> registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), minecraft.screen);
    }

    private void placeManager(IntegratedServer server) {
        try {
            if (server.getPlayerList().getPlayers().size() != 1) {
                throw new IllegalStateException("Expected exactly one scratch-world player");
            }
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Block manager = BuiltInRegistries.BLOCK.getOptional(MANAGER_ID).orElse(null);
            if (manager == null) {
                throw new IllegalStateException("Production sfm:manager is not registered");
            }
            BlockPos base = player.blockPosition();
            BlockPos position = base.offset(0, 0, -3);
            if (!server.overworld().setBlock(position, manager.defaultBlockState(), 3) ||
                    server.overworld().getBlockState(position).getBlock() != manager) {
                throw new IllegalStateException("Server did not place sfm:manager");
            }
            player.setYRot(180F);
            player.setXRot(20F);
            player.setYHeadRot(180F);
            player.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            managerPosition = position;
            serverBlockPlaced = true;
            System.out.println("SFM_RELEASE_CLIENT_SERVER_BLOCK_PLACED run_id=" + runId
                    + " block=sfm:manager pos=" + position.getX() + ","
                    + position.getY() + "," + position.getZ());
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            worldSetupFailure = "server_block_placement_failed";
        }
    }

    private boolean isAimedAtManager(Minecraft minecraft) {
        BlockPos position = managerPosition;
        return position != null && minecraft.hitResult instanceof BlockHitResult hit &&
                hit.getBlockPos().equals(position);
    }

    private void aimClientAtManager(Minecraft minecraft, BlockPos position) {
        double dx = position.getX() + 0.5 - minecraft.player.getX();
        double dy = position.getY() + 0.5 - minecraft.player.getEyeY();
        double dz = position.getZ() + 0.5 - minecraft.player.getZ();
        double horizontal = Math.hypot(dx, dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
        minecraft.player.setYHeadRot(yaw);
        if (!clientAimLogged) {
            clientAimLogged = true;
            System.out.println("SFM_RELEASE_CLIENT_AIM_REQUESTED run_id=" + runId
                    + " yaw=" + yaw + " pitch=" + pitch);
        }
    }

    private String screenshotName() {
        return "world".equals(captureMode) ? WORLD_SCREENSHOT_NAME : TITLE_SCREENSHOT_NAME;
    }

    private void fail(String reason) {
        if (resultWritten) {
            return;
        }
        try {
            writeResult("failed", reason);
        } catch (IOException failure) {
            failure.printStackTrace(System.err);
        }
    }

    private void writeResult(String status, String reason) throws IOException {
        resultWritten = true;
        String json;
        if ("world".equals(captureMode)) {
            json = "{\"schema\":\"sfm-release-client-world-proof/1\",\"run_id\":\"" + runId
                    + "\",\"status\":\"" + status + "\",\"reason\":\"" + reason
                    + "\",\"sfm_sha256\":\"" + expectedSfmSha256 + "\",\"screen\":\"world\""
                    + ",\"world_id\":\"" + worldId + "\",\"block_id\":\"sfm:manager\""
                    + ",\"server_block_placed\":" + serverBlockPlaced
                    + ",\"client_block_synced\":" + clientBlockSynced
                    + ",\"ray_hit\":" + rayHit
                    + ",\"rendered_world_frames\":" + renderedWorldFrames
                    + ",\"screenshot\":\"" + WORLD_SCREENSHOT_NAME + "\"}\n";
        } else {
            json = "{\"schema\":\"sfm-release-client-proof/1\",\"run_id\":\"" + runId
                    + "\",\"status\":\"" + status + "\",\"reason\":\"" + reason
                    + "\",\"sfm_sha256\":\"" + expectedSfmSha256 + "\",\"screen\":\"title\""
                    + ",\"rendered_title_frames\":" + renderedTitleFrames
                    + ",\"screenshot\":\"" + TITLE_SCREENSHOT_NAME + "\"}\n";
        }
        Path staging = controlDirectory.resolve("result.json.staging");
        Files.writeString(staging, json);
        Files.move(staging, controlDirectory.resolve("result.json"), StandardCopyOption.ATOMIC_MOVE);
        System.out.println("SFM_RELEASE_CLIENT_COMPLETE run_id=" + runId + " status=" + status
                + " reason=" + reason + " sfm_sha256=" + expectedSfmSha256);
        Minecraft.getInstance().stop();
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = new byte[64 * 1024];
            for (int count; (count = input.read(bytes)) >= 0;) {
                digest.update(bytes, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
