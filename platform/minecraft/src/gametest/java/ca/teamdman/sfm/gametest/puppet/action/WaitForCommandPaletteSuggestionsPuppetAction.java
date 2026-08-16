package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

/** Waits for the exact input and asynchronous completion set a capture claims to show. */
public final class WaitForCommandPaletteSuggestionsPuppetAction implements SFMPuppetAction {
    private final String expectedInput;
    private final List<String> expectedSuggestions;
    private int ticks;
    private int stableTicks;

    public WaitForCommandPaletteSuggestionsPuppetAction(
            String expectedInput,
            List<String> expectedSuggestions
    ) {
        this.expectedInput = Objects.requireNonNull(expectedInput, "expectedInput");
        this.expectedSuggestions = List.copyOf(expectedSuggestions);
        if (this.expectedSuggestions.isEmpty()) {
            throw new IllegalArgumentException("At least one expected palette suggestion is required");
        }
        if (this.expectedSuggestions.stream().distinct().count() != this.expectedSuggestions.size()) {
            throw new IllegalArgumentException("Expected palette suggestions must be distinct");
        }
    }

    @Override
    public String description() {
        return "wait for command palette input and suggestions " + expectedSuggestions;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        ticks++;
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) {
            String actualInput = palette.inputForAutomation();
            List<String> actualSuggestions = palette.suggestionTextsForAutomation();
            boolean exactSuggestions = actualSuggestions.size() == expectedSuggestions.size()
                    && actualSuggestions.containsAll(expectedSuggestions);
            if (actualInput.equals(expectedInput) && exactSuggestions) {
                return ++stableTicks > SFMGamePuppetHelper.RENDER_SETTLE_TICKS;
            }
            stableTicks = 0;
            if (ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
                throw new IllegalStateException(
                        "Timed out waiting for palette input '" + expectedInput
                                + "' and suggestions " + expectedSuggestions
                                + "; found input '" + actualInput
                                + "' and suggestions " + actualSuggestions
                );
            }
            return false;
        }
        if (ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException(
                    "Timed out waiting for command palette while expecting suggestions " + expectedSuggestions
                            + "; current screen is " + runtime.currentScreenName()
            );
        }
        return false;
    }
}
