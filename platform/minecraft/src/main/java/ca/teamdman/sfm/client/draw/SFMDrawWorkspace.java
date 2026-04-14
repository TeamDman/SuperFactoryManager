package ca.teamdman.sfm.client.draw;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public final class SFMDrawWorkspace {
    private SFMDrawWorkspace() {
    }

    public static boolean openDefaultCanvasScreen() {
        return openCanvasScreen(SFMDrawVirtualFileSystem.DEFAULT_CANVAS_NAME);
    }

    public static boolean openCanvasScreen(String requestedPath) {
        try {
            SFMDrawVirtualFileSystem.ensureUserHomeReady();
            SFMDrawVirtualPath canvasPath = SFMDrawVirtualFileSystem.resolveCanvasPath(requestedPath);
            SFMDrawCanvasDocument document = SFMDrawCanvasStorage.loadOrCreate(canvasPath);
            SFMScreenChangeHelpers.setOrPushScreen(new SfmDrawScreen(canvasPath, document));
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            SFM.LOGGER.error("Failed to open draw canvas {}", requestedPath, exception);
            notifyPlayer("draw.open: " + exception.getMessage());
            return false;
        }
    }

    public static boolean openCanvasInScreen(
            SfmDrawScreen screen,
            String requestedPath,
            @Nullable Integer commandElementId
    ) {
        try {
            SFMDrawVirtualFileSystem.ensureUserHomeReady();
            screen.saveBoundCanvas();
            SFMDrawVirtualPath canvasPath = SFMDrawVirtualFileSystem.resolveCanvasPath(requestedPath);
            SFMDrawCanvasDocument document = SFMDrawCanvasStorage.loadOrCreate(canvasPath);
            screen.bindCanvas(canvasPath, document);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            SFM.LOGGER.error("Failed to open draw canvas {}", requestedPath, exception);
            reportFailure(screen, commandElementId, "open: " + exception.getMessage());
            return false;
        }
    }

    public static boolean moveCanvas(
            SfmDrawScreen screen,
            String fromPath,
            String toPath,
            @Nullable Integer commandElementId
    ) {
        try {
            SFMDrawVirtualFileSystem.ensureUserHomeReady();
            SFMDrawVirtualPath sourcePath = SFMDrawVirtualFileSystem.resolveCanvasPath(fromPath);
            SFMDrawVirtualPath destinationPath = SFMDrawVirtualFileSystem.resolveCanvasPath(toPath);
            boolean movingBoundCanvas = screen.isBoundToVirtualPath(sourcePath.virtualPath());
            if (movingBoundCanvas) {
                screen.saveBoundCanvas();
            }
            SFMDrawVirtualFileSystem.move(sourcePath, destinationPath);
            if (movingBoundCanvas) {
                screen.rebindCanvasPath(destinationPath);
            }
            if (commandElementId != null) {
                screen.appendCommandOutput(
                        commandElementId,
                        List.of("move: " + sourcePath.virtualPath() + " -> " + destinationPath.virtualPath())
                );
            }
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            SFM.LOGGER.error("Failed to move draw canvas {} -> {}", fromPath, toPath, exception);
            reportFailure(screen, commandElementId, "move: " + exception.getMessage());
            return false;
        }
    }

    private static void reportFailure(
            SfmDrawScreen screen,
            @Nullable Integer commandElementId,
            String message
    ) {
        if (commandElementId != null) {
            screen.appendCommandOutput(commandElementId, List.of(message));
            return;
        }
        notifyPlayer(message);
    }

    private static void notifyPlayer(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}