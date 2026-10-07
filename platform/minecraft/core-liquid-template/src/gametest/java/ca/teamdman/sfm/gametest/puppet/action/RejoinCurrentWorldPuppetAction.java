package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.nio.file.Files;

/** Exercises a genuine integrated-server shutdown and load of the same puppet-owned save. */
public final class RejoinCurrentWorldPuppetAction implements SFMPuppetAction {
    private static final String EXPERIMENTAL_TITLE_KEY = "selectWorld.backupQuestion.experimental";
    private IntegratedServer previousServer;
    private int phase;
    private int rejoinTicks;
    private boolean approvedExperimentalWorld;

    @Override
    public String description() {
        return "leave and rejoin disposable puppet world";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (phase == 0) {
            previousServer = minecraft.getSingleplayerServer();
            if (previousServer == null || minecraft.level == null || minecraft.player == null) {
                throw new IllegalStateException("Cannot relog without a loaded puppet world");
            }
            SFM.LOGGER.info("SFM_GAME_PUPPET_RELOG_LEAVING world={}", runtime.puppetWorldId());
            // clearLevel pumps a client tick while showing the title screen.
            phase = 1;
            previousServer.halt(true);
            minecraft.clearLevel(new TitleScreen());
            return false;
        }
        if (phase == 1) {
            if (minecraft.getSingleplayerServer() != null || minecraft.level != null) {
                return false;
            }
            requireDisposableSave(minecraft, runtime.puppetWorldId());
            // WorldOpenFlows also pumps client ticks while loading the save.
            phase = 2;
            SFM.LOGGER.info("SFM_GAME_PUPPET_RELOG_LOADING world={}", runtime.puppetWorldId());
            minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), runtime.puppetWorldId());
            return false;
        }
        IntegratedServer rejoinedServer = minecraft.getSingleplayerServer();
        if (rejoinedServer == null || !rejoinedServer.isReady()
                || minecraft.level == null || minecraft.player == null
                || minecraft.screen != null) {
            if (++rejoinTicks > 20 * 180) {
                throw new IllegalStateException("Rejoin timed out with screen "
                        + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName()));
            }
            if (minecraft.screen instanceof ConfirmScreen confirmation) {
                approveExactDisposableWorldWarning(minecraft, runtime.puppetWorldId(), confirmation);
            }
            return false;
        }
        if (rejoinedServer == previousServer) {
            throw new IllegalStateException("Relog reused the prior integrated server instance");
        }
        SFM.LOGGER.info("SFM_GAME_PUPPET_RELOG_REJOINED world={} new_server=true",
                runtime.puppetWorldId());
        return true;
    }

    private void approveExactDisposableWorldWarning(
            Minecraft minecraft,
            String worldId,
            ConfirmScreen confirmation
    ) {
        if (approvedExperimentalWorld) return;
        requireDisposableSave(minecraft, worldId);
        if (!(confirmation.getTitle().getContents() instanceof TranslatableContents title)
                || !EXPERIMENTAL_TITLE_KEY.equals(title.getKey())) {
            throw new IllegalStateException("Unexpected confirmation while rejoining puppet save: "
                    + confirmation.getTitle().getString());
        }
        String message = confirmation.getNarrationMessage().getString();
        if (!message.contains(Component.translatable("selectWorld.backupWarning.experimental").getString())
                || !message.contains(Component.translatable(
                        "forge.selectWorld.backupWarning.experimental.additional").getString())) {
            throw new IllegalStateException("Unexpected experimental-world confirmation text: " + message);
        }
        Button proceed = confirmation.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(CommonComponents.GUI_PROCEED.getString()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Experimental-world Proceed button is absent"));
        if (!proceed.active || !proceed.visible) {
            throw new IllegalStateException("Experimental-world Proceed button is not usable");
        }
        approvedExperimentalWorld = true;
        SFM.LOGGER.info("SFM_GAME_PUPPET_RELOG_CONFIRMING world={} title={} message={}",
                worldId, confirmation.getTitle().getString(), message);
        double x = proceed.x + proceed.getWidth() / 2D;
        double y = proceed.y + proceed.getHeight() / 2D;
        confirmation.mouseClicked(x, y, 0);
        confirmation.mouseReleased(x, y, 0);
    }

    private static void requireDisposableSave(Minecraft minecraft, String worldId) {
        var gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        var savesDirectory = gameDirectory.resolve("saves");
        var save = savesDirectory.resolve(worldId).normalize();
        if (!worldId.startsWith("sfm_game_puppet_")
                || !gameDirectory.getFileName().toString().equals("runGameTestPreview")
                || !save.getParent().equals(savesDirectory)
                || !Files.isDirectory(save)) {
            throw new IllegalStateException("Refusing to relog a non-puppet-owned save " + worldId
                    + " under " + gameDirectory);
        }
    }
}
