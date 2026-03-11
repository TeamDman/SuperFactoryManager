package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ide.action.IdeActionDefinition;
import ca.teamdman.sfm.client.screen.IdePlaygroundScreen;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMIdePlaygroundActions {
    public static final ResourceKey<Registry<IdeActionDefinition>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("ide_playground_action");

    private static final SFMDeferredRegister<IdeActionDefinition> REGISTERER =
            new SFMDeferredRegisterBuilder<IdeActionDefinition>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> TOGGLE_SHELL_PANEL = REGISTERER.register(
            "panel.toggle_shell",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_SHELL,
                    IdePlaygroundScreen::toggleShellPanel,
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_LEFT_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> TOGGLE_LAYOUT_PANEL = REGISTERER.register(
            "panel.toggle_layout",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_LAYOUT,
                    IdePlaygroundScreen::toggleLayoutPanel,
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_RIGHT_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> TOGGLE_TERMINAL_PANEL = REGISTERER.register(
            "panel.toggle_terminal",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_TERMINAL,
                    IdePlaygroundScreen::toggleTerminalPanel,
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_BOTTOM_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> FOCUS_SHELL_PANEL = REGISTERER.register(
            "panel.focus_shell",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_SHELL,
                    IdePlaygroundScreen::focusShellPanel,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> FOCUS_WORKSPACE_PANEL = REGISTERER.register(
            "panel.focus_workspace",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_WORKSPACE,
                    IdePlaygroundScreen::focusWorkspacePanel,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> FOCUS_LAYOUT_PANEL = REGISTERER.register(
            "panel.focus_layout",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_LAYOUT,
                    IdePlaygroundScreen::focusLayoutPanel,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> FOCUS_TERMINAL_PANEL = REGISTERER.register(
            "panel.focus_terminal",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_TERMINAL,
                    IdePlaygroundScreen::focusTerminalPanel,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> SELECT_FOCUSED_TARGET = REGISTERER.register(
            "selection.select_focused",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_SELECT_FOCUSED,
                    IdePlaygroundScreen::selectFocusedTarget,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> CLEAR_SELECTED_TARGETS = REGISTERER.register(
            "selection.clear",
            ()-> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_CLEAR_SELECTION,
                    IdePlaygroundScreen::clearSelectedTargets,
                    null
            )
    );

    public static final SFMRegistryObject<IdeActionDefinition, IdeActionDefinition> HELP = REGISTERER.register(
            "help",
            () -> new IdeActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_HELP,
                    IdePlaygroundScreen::showTerminalHelp,
                    null
            )
    );

    private SFMIdePlaygroundActions() {

    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    public static SFMRegistryWrapper<IdeActionDefinition> registry() {

        return REGISTERER.registry();
    }
}