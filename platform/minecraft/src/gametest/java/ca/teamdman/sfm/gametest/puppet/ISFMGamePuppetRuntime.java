package ca.teamdman.sfm.gametest.puppet;

import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public interface ISFMGamePuppetRuntime {
    boolean createFreshFlatWorld();

    boolean runGameTest(String testName);

    void positionOrbitCamera(BlockPos localTarget, double radius, double height, double angleRadians);

    void positionGameTestOrbitCamera(double angleRadians);

    void positionForBlockUse(BlockPos localTarget);

    void useBlock(BlockPos localTarget);

    boolean isScreen(Class<?> expectedType);

    boolean openCommandPalette();

    boolean isOverlay(Class<? extends Overlay> expectedType);

    boolean capture(String captureName, Component caption);

    void closeScreen();

    void openManagerProgramEditor();
}
