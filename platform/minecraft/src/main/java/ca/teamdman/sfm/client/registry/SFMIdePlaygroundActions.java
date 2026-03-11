package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.ide.action.IdePlaygroundActionDefinition;
import ca.teamdman.sfm.client.ide.action.IdePlaygroundActionIds;
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

import java.util.List;

public final class SFMIdePlaygroundActions {
    public static final ResourceKey<Registry<IdePlaygroundActionDefinition>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("ide_playground_action");

    private static final SFMDeferredRegister<IdePlaygroundActionDefinition> REGISTERER =
            new SFMDeferredRegisterBuilder<IdePlaygroundActionDefinition>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> TOGGLE_SHELL_PANEL = register(
            IdePlaygroundActionIds.TOGGLE_SHELL_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_SHELL,
                    IdePlaygroundScreen::toggleShellPanel,
                    List.of("panel.toggle_shell", "/sfm ide toggle_shell"),
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_LEFT_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> TOGGLE_LAYOUT_PANEL = register(
            IdePlaygroundActionIds.TOGGLE_LAYOUT_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_LAYOUT,
                    IdePlaygroundScreen::toggleLayoutPanel,
                    List.of("panel.toggle_layout", "/sfm ide toggle_layout"),
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_RIGHT_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> TOGGLE_TERMINAL_PANEL = register(
            IdePlaygroundActionIds.TOGGLE_TERMINAL_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_TOGGLE_TERMINAL,
                    IdePlaygroundScreen::toggleTerminalPanel,
                    List.of("panel.toggle_terminal", "/sfm ide toggle_terminal"),
                    () -> SFMKeyMappings.getCanonicalKeybindingString(SFMKeyMappings.IDE_TOGGLE_BOTTOM_PANEL_KEY)
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> FOCUS_SHELL_PANEL = register(
            IdePlaygroundActionIds.FOCUS_SHELL_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_SHELL,
                    IdePlaygroundScreen::focusShellPanel,
                    List.of("panel.focus_shell", "/sfm ide focus_shell"),
                    null
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> FOCUS_WORKSPACE_PANEL = register(
            IdePlaygroundActionIds.FOCUS_WORKSPACE_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_WORKSPACE,
                    IdePlaygroundScreen::focusWorkspacePanel,
                    List.of("panel.focus_workspace", "/sfm ide focus_workspace"),
                    null
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> FOCUS_LAYOUT_PANEL = register(
            IdePlaygroundActionIds.FOCUS_LAYOUT_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_LAYOUT,
                    IdePlaygroundScreen::focusLayoutPanel,
                    List.of("panel.focus_layout", "/sfm ide focus_layout"),
                    null
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> FOCUS_TERMINAL_PANEL = register(
            IdePlaygroundActionIds.FOCUS_TERMINAL_PANEL,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_FOCUS_TERMINAL,
                    IdePlaygroundScreen::focusTerminalPanel,
                    List.of("panel.focus_terminal", "/sfm ide focus_terminal"),
                    null
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> SELECT_FOCUSED_TARGET = register(
            IdePlaygroundActionIds.SELECT_FOCUSED_TARGET,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_SELECT_FOCUSED,
                    IdePlaygroundScreen::selectFocusedTarget,
                    List.of("selection.select_focused", "/sfm ide select_focused"),
                    null
            )
    );

    public static final SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> CLEAR_SELECTED_TARGETS = register(
            IdePlaygroundActionIds.CLEAR_SELECTED_TARGETS,
            new IdePlaygroundActionDefinition(
                    IdeLocalizationKeys.IDE_PLAYGROUND_ACTION_CLEAR_SELECTION,
                    IdePlaygroundScreen::clearSelectedTargets,
                    List.of("selection.clear", "/sfm ide clear_selection"),
                    null
            )
    );

    private SFMIdePlaygroundActions() {
    }

    private static SFMRegistryObject<IdePlaygroundActionDefinition, IdePlaygroundActionDefinition> register(
            String id,
            IdePlaygroundActionDefinition definition
    ) {
        return REGISTERER.register(path(id), () -> definition);
    }

    private static String path(String id) {
        int separator = id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id;
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }

    public static SFMRegistryWrapper<IdePlaygroundActionDefinition> registry() {
        return REGISTERER.registry();
    }
}