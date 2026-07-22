package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
import ca.teamdman.sfm.client.keybinding.SFMKeyStroke;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMKeyBindingDetailsScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public final class ShowDynamicKeyBindingPuppetAction implements SFMPuppetAction {
    public enum View { PALETTE, DETAILS, RECORDING, CONFLICT, INCOMPLETE, DETAILS_AFTER_REMOVAL }

    private static final ResourceLocation HELP = new ResourceLocation("sfm", "help");
    private final View view;

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
        if (view == View.PALETTE) {
            service.remove("puppet-help-1");
            service.remove("puppet-help-2");
            service.put(binding("puppet-help-1", SFMKeySequence.of(
                    SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)
            )));
            service.put(binding("puppet-help-2", SFMKeySequence.of(
                    SFMKeyStroke.of(GLFW.GLFW_KEY_K, SFMKeyModifier.CONTROL),
                    SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)
            )));
            SFMCommandPaletteScreen.open(
                    SFMCommandPaletteScreen.createOriginContext(),
                    "sfm action invoke sfm:h"
            );
        } else if (view == View.INCOMPLETE) {
            service.put(new SFMKeyBinding(
                    "puppet-incomplete",
                    "sfm:echo",
                    "sfm action invoke sfm:echo ",
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
        } else {
            Screen parent = Minecraft.getInstance().screen;
            if (view == View.DETAILS_AFTER_REMOVAL) service.remove("puppet-help-1");
            if (view == View.CONFLICT) {
                service.put(new SFMKeyBinding(
                        "puppet-conflict",
                        "sfm:echo",
                        "sfm action invoke sfm:echo conflict",
                        SFMKeySequence.of(SFMKeyStroke.of(GLFW.GLFW_KEY_H, SFMKeyModifier.CONTROL)),
                        true
                ));
            }
            SFMKeyBindingDetailsScreen details = new SFMKeyBindingDetailsScreen(parent, HELP);
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
                HELP.toString(),
                "sfm action invoke " + HELP,
                sequence,
                true
        );
    }
}
