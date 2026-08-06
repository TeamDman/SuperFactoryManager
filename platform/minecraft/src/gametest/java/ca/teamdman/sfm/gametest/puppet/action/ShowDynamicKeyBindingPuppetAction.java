package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
import ca.teamdman.sfm.client.keybinding.SFMKeyStroke;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMCommandDraftScreen;
import ca.teamdman.sfm.client.screen.SFMKeyBindingDetailsScreen;
import ca.teamdman.sfm.client.screen.SFMKeyBindingScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public final class ShowDynamicKeyBindingPuppetAction implements SFMPuppetAction {
    public enum View {
        SETUP, PALETTE, DETAILS, RECORDING, CONFLICT,
        ACTIVATE_FIRST, ACTIVATE_SECOND, INCOMPLETE, CONFIRMATION, DETAILS_AFTER_REMOVAL
    }

    private static final ResourceLocation ACTION = new ResourceLocation("sfm", "keybindings/manage");
    private final View view;
    private boolean requested;
    private int ticks;

    public ShowDynamicKeyBindingPuppetAction(View view) {
        this.view = view;
    }

    @Override
    public String description() {
        return "show dynamic binding " + view;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMKeyBindingService service = SFMKeyBindingService.INSTANCE;
        if (view != View.RECORDING) service.setDispatchSuspended(false);
        if (view == View.SETUP || view == View.PALETTE) {
            service.removeEphemeral("puppet-help-1");
            service.removeEphemeral("puppet-help-2");
            service.removeEphemeral("puppet-incomplete");
            service.removeEphemeral("puppet-conflict");
            service.putEphemeral(binding("puppet-help-1", SFMKeySequence.of(
                    SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)
            )));
            service.putEphemeral(binding("puppet-help-2", SFMKeySequence.of(
                    SFMKeyStroke.of(GLFW.GLFW_KEY_K, SFMKeyModifier.CONTROL),
                    SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)
            )));
            if (view == View.PALETTE) {
                SFMCommandPaletteScreen.open(
                        SFMCommandPaletteScreen.createOriginContext(),
                        "sfm action invoke sfm:key"
                );
            }
        } else if (view == View.ACTIVATE_FIRST || view == View.ACTIVATE_SECOND) {
            if (!requested) {
                requested = true;
                Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                if (view == View.ACTIVATE_FIRST) {
                    runtime.pressScreenKey(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_CONTROL);
                } else {
                    runtime.pressScreenKey(GLFW.GLFW_KEY_K, GLFW.GLFW_MOD_CONTROL);
                    runtime.pressScreenKey(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_CONTROL);
                }
                return false;
            }
            if (Minecraft.getInstance().screen instanceof SFMKeyBindingScreen) return true;
            if (++ticks > 100) throw new IllegalStateException("Shortcut did not invoke manager through contextual executor");
            return false;
        } else if (view == View.INCOMPLETE) {
            service.putEphemeral(new SFMKeyBinding(
                    "puppet-incomplete",
                    "sfm:echo",
                    "sfm action invoke sfm:echo ",
                    SFMKeyboardUsageSituations.GLOBAL,
                    SFMKeySequence.of(SFMKeyStroke.of(GLFW.GLFW_KEY_I, SFMKeyModifier.CONTROL)),
                    true
            ));
            service.accept(new SFMKeyInputEvent(
                    1000,
                    service.currentTick(),
                    GLFW.GLFW_KEY_I,
                    SFMKeyInputEvent.Type.PRESS,
                    Set.of(SFMKeyModifier.CONTROL)
            ));
            if (!(Minecraft.getInstance().screen instanceof SFMCommandDraftScreen)) {
                throw new IllegalStateException("Incomplete shortcut did not open typed command prompt");
            }
        } else if (view == View.CONFIRMATION) {
            if (!(Minecraft.getInstance().screen instanceof SFMCommandDraftScreen draft)) {
                throw new IllegalStateException("Expected typed command prompt before confirmation");
            }
            draft.supplyMissingArgumentForAutomation("hello from shortcut");
            if (!draft.commandForAutomation().equals("sfm action invoke sfm:echo hello from shortcut")) {
                throw new IllegalStateException("Typed prompt built an unexpected command");
            }
        } else {
            Screen parent = Minecraft.getInstance().screen;
            if (view == View.DETAILS_AFTER_REMOVAL) service.removeEphemeral("puppet-help-1");
            if (view == View.CONFLICT) {
                service.putEphemeral(new SFMKeyBinding(
                        "puppet-conflict",
                        "sfm:echo",
                        "sfm action invoke sfm:echo conflict",
                        SFMKeyboardUsageSituations.GLOBAL,
                        SFMKeySequence.of(SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)),
                        true
                ));
            }
            SFMKeyBindingDetailsScreen details = new SFMKeyBindingDetailsScreen(parent, ACTION);
            ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers.setOrPushScreen(
                    details
            );
            if (view == View.RECORDING) details.beginRecordingForAutomation();
        }
        return true;
    }

    private static SFMKeyBinding binding(String id, SFMKeySequence sequence) {
        return new SFMKeyBinding(
                id,
                ACTION.toString(),
                "sfm action invoke " + ACTION,
                SFMKeyboardUsageSituations.GLOBAL,
                sequence,
                true
        );
    }

}
