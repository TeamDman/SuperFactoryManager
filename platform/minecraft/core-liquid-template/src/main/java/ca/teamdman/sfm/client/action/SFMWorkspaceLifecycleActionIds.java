package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
{% if features.workspace_panel_entry_controls %}
import ca.teamdman.sfm.client.screen.SFMActionChoice;
{% endif %}
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Bootstrap-free lifecycle action ids and constrained exact-entry choices. */
public final class SFMWorkspaceLifecycleActionIds {
    public static final ResourceLocation PANEL_ENTRY_FOCUS = id("panel/entry/focus");
    public static final ResourceLocation PANEL_ENTRY_CLOSE = id("panel/entry/close");
    public static final ResourceLocation PANEL_ENTRY_MOVE_LEFT = id("panel/entry/move/left");
    public static final ResourceLocation PANEL_ENTRY_MOVE_RIGHT = id("panel/entry/move/right");
    public static final ResourceLocation PANEL_ENTRY_MOVE_ABOVE = id("panel/entry/move/above");
    public static final ResourceLocation PANEL_ENTRY_MOVE_BELOW = id("panel/entry/move/below");
    public static final ResourceLocation PANEL_ENTRY_MOVE_TO = id("panel/entry/move/to");
    public static final ResourceLocation PANE_CLOSE = id("pane/close");
    public static final ResourceLocation PANE_CLOSE_CONFIRM = id("pane/close/confirm");

    private SFMWorkspaceLifecycleActionIds() {
    }

{% if features.workspace_panel_entry_controls %}
    public static List<SFMActionChoice> panelEntryChoices(
            SFMPanelEntryInteractionSessionService.Session session
    ) {
        String argument = session.commandArgument();
        String target = session.stableId();
        return List.of(
                SFMActionChoice.invoke(PANEL_ENTRY_FOCUS, argument, "Focus " + target),
                SFMActionChoice.invoke(PANEL_ENTRY_CLOSE, argument, "Close " + target),
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_LEFT, argument, "Move " + target + " left"),
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_RIGHT, argument, "Move " + target + " right"),
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_ABOVE, argument, "Move " + target + " above"),
{% if features.workspace_lifecycle %}
{% if features.typed_command_palette %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below"),
                SFMActionChoice.invoke(PANE_CLOSE, "", "Close pane containing " + target)
{% else %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below")
{% endif %}
{% else %}
                SFMActionChoice.invoke(PANEL_ENTRY_MOVE_BELOW, argument, "Move " + target + " below")
{% endif %}
        );
    }

{% endif %}
    private static ResourceLocation id(String path) {
        return new ResourceLocation(SFM.MOD_ID, path);
    }
}
