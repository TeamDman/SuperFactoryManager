package ca.teamdman.sfm.common.localization;

import ca.teamdman.sfm.SFM;

import java.util.ArrayList;
import java.util.List;

public final class SFMTutorialLocalizationKeys {
    public static final LocalizationEntry COMMAND_TUTORIAL_ONLY_PLAYER = new LocalizationEntry(
            "sfm.command.tutorial.only_player",
            "This command can only be used by a player."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_REQUIRES_EMPTY_INVENTORY = new LocalizationEntry(
            "sfm.command.tutorial.requires_empty_inventory",
            "Tutorial requires an empty inventory. Store your items before entering."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_DIMENSION_UNAVAILABLE = new LocalizationEntry(
            "sfm.command.tutorial.dimension_unavailable",
            "Tutorial dimension is unavailable. Ensure data pack resources are loaded."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_CREATED = new LocalizationEntry(
            "sfm.command.tutorial.lobby_created",
            "Created tutorial lobby #%d."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_LEFT = new LocalizationEntry(
            "sfm.command.tutorial.lobby_left",
            "Left tutorial lobby #%d."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_NONE_FOR_PLAYER = new LocalizationEntry(
            "sfm.command.tutorial.lobby_none_for_player",
            "You are not in a tutorial lobby."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_NONE_FOR_PLAYER_NAME = new LocalizationEntry(
            "sfm.command.tutorial.lobby_none_for_player_name",
            "Player %s is not in a tutorial lobby."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_LIST_EMPTY = new LocalizationEntry(
            "sfm.command.tutorial.lobby_list.empty",
            "No tutorial lobbies are active."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_LIST_HEADER = new LocalizationEntry(
            "sfm.command.tutorial.lobby_list.header",
            "Active tutorial lobbies:"
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_LIST_ENTRY = new LocalizationEntry(
            "sfm.command.tutorial.lobby_list.entry",
            "Lobby #%s: %s"
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_LOBBY_NOT_FOUND = new LocalizationEntry(
            "sfm.command.tutorial.lobby_not_found",
            "Tutorial lobby #%d was not found."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_CHAMBER_NOT_FOUND = new LocalizationEntry(
            "sfm.command.tutorial.chamber_not_found",
            "Tutorial chamber \"%s\" was not found."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_CHAMBER_ADVANCED = new LocalizationEntry(
            "sfm.command.tutorial.chamber_advanced",
            "Lobby #%d advanced to chamber \"%s\"."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_CHAMBER_RESTARTED = new LocalizationEntry(
            "sfm.command.tutorial.chamber_restarted",
            "Lobby #%d restarted chamber \"%s\"."
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_CHAMBER_ENTERED = new LocalizationEntry(
            "sfm.command.tutorial.chamber_entered",
            "You have entered chamber %s"
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_PLAYER_LEFT_LOBBY = new LocalizationEntry(
            "sfm.command.tutorial.player_left_lobby",
            "%s has left tutorial lobby #%d."
    );
    public static final LocalizationEntry TUTORIAL_CHAMBER_MOVE_1_STACK_SIGN_PLACE_IN_MANAGER = new LocalizationEntry(
            "sfm.tutorial.chamber.move_1_stack_direct.sign.place_in_manager",
            "place this in the manager"
    );
    public static final LocalizationEntry TUTORIAL_CHAMBER_MOVE_1_STACK_SIGN_RESET = new LocalizationEntry(
            "sfm.tutorial.chamber.move_1_stack_direct.sign.reset",
            "reset"
    );
    public static final LocalizationEntry COMMAND_TUTORIAL_CHAMBER_COMPLETE = new LocalizationEntry(
            "sfm.command.tutorial.chamber_complete",
            "Lobby #%d completed the final chamber."
    );

    private SFMTutorialLocalizationKeys() {
    }

    public static List<LocalizationEntry> getEntries() {
        var rtn = new ArrayList<LocalizationEntry>();
        for (var field : SFMTutorialLocalizationKeys.class.getFields()) {
            if (field.getType() == LocalizationEntry.class) {
                try {
                    rtn.add((LocalizationEntry) field.get(null));
                } catch (IllegalAccessException e) {
                    SFM.LOGGER.error("Failed reading tutorial localization entry field", e);
                }
            }
        }
        return rtn;
    }
}