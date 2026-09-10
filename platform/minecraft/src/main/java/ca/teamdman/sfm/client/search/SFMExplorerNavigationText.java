package ca.teamdman.sfm.client.search;

import ca.teamdman.sfm.common.localization.*;

public final class SFMExplorerNavigationText {
    @SFMLocalizationDatagen public static final LocalizationEntry REFRESH = new LocalizationEntry("gui.sfm.explorer.navigation.refresh", "Refresh this Explorer");
    @SFMLocalizationDatagen public static final LocalizationEntry REFRESH_NODE = new LocalizationEntry("gui.sfm.explorer.navigation.refresh_node", "Refresh these children");
    @SFMLocalizationDatagen public static final LocalizationEntry ADD_ROOT = new LocalizationEntry("gui.sfm.explorer.navigation.add_root", "Add directory as root");
    @SFMLocalizationDatagen public static final LocalizationEntry PARENT_SET = new LocalizationEntry("gui.sfm.explorer.navigation.parent_set", "Change location to parent of root · %s");
    @SFMLocalizationDatagen public static final LocalizationEntry PARENT_ADD = new LocalizationEntry("gui.sfm.explorer.navigation.parent_add", "Add parent of root as root · %s");
    @SFMLocalizationDatagen public static final LocalizationEntry DESCRIPTION = new LocalizationEntry("gui.sfm.explorer.navigation.description", "Reload the current roots asynchronously without changing source files or selection");
    @SFMLocalizationDatagen public static final LocalizationEntry PARENT_TITLE = new LocalizationEntry("gui.sfm.explorer.navigation.parent_title", "Change location to parent of root");
    @SFMLocalizationDatagen public static final LocalizationEntry PARENT_DESCRIPTION = new LocalizationEntry("gui.sfm.explorer.navigation.parent_description", "Replace one named root with its parent, retaining other roots");
    private SFMExplorerNavigationText() {}
}
