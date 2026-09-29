package ca.teamdman.sfm.releaseprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/** Test-only, file-driven witness for an unchanged 1.19.2 production SFM JAR. */
@Mod("sfmreleaseprobe")
public final class ReleaseClientBridge {
    private static final String CONTROL_PROPERTY = "sfm.releaseProbe.controlDirectory";
    private static final String SCREENSHOT_NAME = "sfm-release-client-title.png";
    // Let the title fade-in and its menu widgets settle before taking the witness.
    private static final int TITLE_FRAMES_BEFORE_CAPTURE = 90;

    private final Path controlDirectory;
    private final Path gameDirectory;
    private final String runId;
    private final String expectedSfmSha256;
    private int renderedTitleFrames;
    private boolean renderEventObserved;
    private boolean clientTickObserved;
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
            if (!runId.matches("[0-9a-fA-F-]{36}") || !expectedSfmSha256.matches("[0-9a-f]{64}")
                    || !"title".equals(request.getProperty("capture", ""))) {
                throw new IllegalArgumentException("Invalid release-client request");
            }
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
            MinecraftForge.EVENT_BUS.register(this);
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
        if (captureQueued || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        renderedTitleFrames++;
        if (renderedTitleFrames < TITLE_FRAMES_BEFORE_CAPTURE) {
            return;
        }
        if (!ModList.get().isLoaded("sfm")) {
            fail("sfm_mod_not_loaded");
            return;
        }
        captureQueued = true;
        try {
            Minecraft minecraft = Minecraft.m_91087_();
            Screenshot.m_92295_(minecraft.f_91069_, SCREENSHOT_NAME, minecraft.m_91385_(),
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
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !clientTickObserved) {
            clientTickObserved = true;
            System.out.println("SFM_RELEASE_CLIENT_TICK_OBSERVED run_id=" + runId);
        }
        if (event.phase != TickEvent.Phase.END || !captureQueued || resultWritten) {
            return;
        }
        Path screenshot = gameDirectory.resolve("screenshots").resolve(SCREENSHOT_NAME);
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
        String json = "{\"schema\":\"sfm-release-client-proof/1\",\"run_id\":\"" + runId
                + "\",\"status\":\"" + status + "\",\"reason\":\"" + reason
                + "\",\"sfm_sha256\":\"" + expectedSfmSha256 + "\",\"screen\":\"title\""
                + ",\"rendered_title_frames\":" + renderedTitleFrames
                + ",\"screenshot\":\"" + SCREENSHOT_NAME + "\"}\n";
        Path staging = controlDirectory.resolve("result.json.staging");
        Files.writeString(staging, json);
        Files.move(staging, controlDirectory.resolve("result.json"), StandardCopyOption.ATOMIC_MOVE);
        System.out.println("SFM_RELEASE_CLIENT_COMPLETE run_id=" + runId + " status=" + status
                + " reason=" + reason + " sfm_sha256=" + expectedSfmSha256);
        Minecraft.m_91087_().m_91395_();
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
