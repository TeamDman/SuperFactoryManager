package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;
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

    String currentScreenName();

    boolean openCommandPalette();

    void executeCommandPalette(String command);

    void pressFileExplorerKey(int keyCode);

    void setFileExplorerSnapshot(SFMFileExplorerSnapshot snapshot);

    void openFileExplorer(SFMFileExplorerSource source);

    boolean isFileExplorerOpen();

    void deliverFileExplorerDropFixture();

    void clickFileExplorerRow(int visibleRowIndex);

    void assertFileExplorerWorkspace(
            int panelCount,
            String expectedRootName,
            String expectedViewerPath,
            String expectedViewerText,
            boolean rememberOrRequireViewerIdentity
    );

    boolean isOverlay(Class<? extends Overlay> expectedType);

    boolean capture(String captureName, Component caption);

    void closeScreen();

    void closeScreenNaturally();

    boolean clickWorkspacePanel(int panelIndex);

    void openFalsifiedInventoryTimeline();

    void seekFalsifiedInventoryTimeline(int timestep);

    void seekFalsifiedInventoryKeyframePosition(double position);

    void seekFalsifiedInventoryElapsedTicks(double ticks);

    void jumpFalsifiedInventoryKeyframe(int direction);

    void dragFalsifiedInventoryTimeline(int fromTimestep, int toTimestep);

    void openManagerProgramEditor();

    void openColorInput(boolean toSide);

    void setColorInputHueSaturation(double hue, double saturation);

    void setColorInputValue(double value);

    void adjustColorInputChannel(int channel, int direction, int clicks);

    void selectColorInputRecent(int index);

    void resetColorInput();

    void setColorInputHex(String hex, boolean rgbaOrder);

    void confirmColorInput();
}
