package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSettingRegistry;

import java.util.Locale;
import java.util.Objects;

/** Canonical command construction shared by every explorer-panel input path. */
public final class SFMExplorerPanelActions {
    private static final String PREFIX = "sfm action invoke ";

    private SFMExplorerPanelActions() {
    }

    public static String nodeExpand(SFMExplorerId explorerId, SFMPath path) {
        return node("expand", explorerId, path);
    }

    public static String nodeCollapse(SFMExplorerId explorerId, SFMPath path) {
        return node("collapse", explorerId, path);
    }

    public static String nodeToggle(SFMExplorerId explorerId, SFMPath path) {
        return node("toggle", explorerId, path);
    }

    public static String nodeRefresh(SFMExplorerId explorerId, SFMPath path) {
        return node("refresh", explorerId, path);
    }

    public static String rootAdd(SFMExplorerId explorerId, SFMPath path) {
        return command("sfm:explorer/root/add", explorerId, literal(path)) + " --if-no-match fail";
    }

    public static String rootRemove(SFMExplorerId explorerId, SFMPath path) {
        return command("sfm:explorer/root/remove", explorerId, literal(path));
    }

    public static String locationEdit(SFMExplorerId explorerId) {
        return PREFIX + "sfm:explorer/location/edit " + exact(explorerId) + " right";
    }

    public static String pathOpen(SFMPath path, SFMExplorerPreviewPlacement.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        String suffix = switch (mode) {
            case PREVIEW -> "preview";
            case FOCUS_PREVIEW -> "focus";
            case ADJACENT -> "adjacent";
        };
        return PREFIX + "sfm:path/open " + Objects.requireNonNull(path, "path").canonical() + " " + suffix;
    }

    public static String viewSet(SFMExplorerId explorerId, SFMExplorerProjection.View view) {
        return setting("sfm:explorer/view/set", explorerId, SFMExplorerSettingRegistry.id(view));
    }

    public static String sortSet(SFMExplorerId explorerId, SFMExplorerProjection.Sort sort) {
        return setting("sfm:explorer/sort/set", explorerId, "sfm:" + wireId(sort));
    }

    public static String groupSet(SFMExplorerId explorerId, SFMExplorerProjection.Group group) {
        return setting("sfm:explorer/group/set", explorerId, "sfm:" + wireId(group));
    }

    public static String hoistSet(SFMExplorerId explorerId, SFMExplorerProjection.Hoist hoist) {
        return setting("sfm:explorer/root/hoist/set", explorerId, wireId(hoist));
    }

    public static String pathDisplaySet(
            SFMExplorerId explorerId,
            SFMExplorerProjection.PathDisplay pathDisplay
    ) {
        return setting(
                "sfm:explorer/path-display/set",
                explorerId,
                SFMExplorerSettingRegistry.id(pathDisplay)
        );
    }

    public static String filterSet(SFMExplorerId explorerId, String query) {
        String normalized = Objects.requireNonNull(query, "query").strip();
        if (normalized.isEmpty()) return filterClear(explorerId);
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Explorer filter query must be one line");
        }
        return command("sfm:explorer/filter/set", explorerId, normalized);
    }

    public static String filterClear(SFMExplorerId explorerId) {
        return PREFIX + "sfm:explorer/filter/clear " + exact(explorerId);
    }

    private static String node(String operation, SFMExplorerId explorerId, SFMPath path) {
        return command("sfm:explorer/node/" + operation, explorerId, literal(path));
    }

    private static String setting(String actionId, SFMExplorerId explorerId, String value) {
        return PREFIX + actionId + " " + exact(explorerId) + " " + value;
    }

    private static String command(String actionId, SFMExplorerId explorerId, String argument) {
        return PREFIX + actionId + " " + exact(explorerId) + " " + argument;
    }

    private static String exact(SFMExplorerId explorerId) {
        Objects.requireNonNull(explorerId, "explorerId");
        return SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER,
                explorerId.value()
        ).canonical();
    }

    private static String literal(SFMPath path) {
        return new SFMPathExpression.Literal(Objects.requireNonNull(path, "path")).canonical();
    }

    private static String wireId(Enum<?> value) {
        return Objects.requireNonNull(value, "value")
                .name()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }
}
