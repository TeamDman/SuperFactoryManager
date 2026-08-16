package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;

import java.util.Objects;

/** Fully explicit, UI-independent request for one explorer operation. */
public record SFMExplorerActionRequest(
        SFMEntitySelector selector,
        Operation operation,
        IfNoMatch ifNoMatch
) {
    public enum IfNoMatch {
        FAIL,
        OPEN_NEW
    }

    public sealed interface Operation permits
            ListExplorers,
            Describe,
            NodeExpand,
            NodeCollapse,
            NodeToggle,
            NodeRefresh,
            RootList,
            RootAdd,
            RootRemove,
            LocationSet,
            ViewSet,
            SortSet,
            GroupSet,
            HoistSet,
            PathDisplaySet,
            FilterSet,
            FilterClear {
        String id();

        default boolean mutatesSession() {
            return !(this instanceof ListExplorers || this instanceof Describe || this instanceof RootList
                    || this instanceof NodeRefresh);
        }
    }

    public record ListExplorers() implements Operation {
        @Override
        public String id() { return "list"; }
    }

    public record Describe() implements Operation {
        @Override
        public String id() { return "describe"; }
    }

    public record NodeExpand(SFMPath path, int pageSize) implements Operation {
        public NodeExpand { requirePathAndPage(path, pageSize); }
        @Override
        public String id() { return "node.expand"; }
    }

    public record NodeCollapse(SFMPath path) implements Operation {
        public NodeCollapse { Objects.requireNonNull(path, "path"); }
        @Override
        public String id() { return "node.collapse"; }
    }

    public record NodeToggle(SFMPath path, int pageSize) implements Operation {
        public NodeToggle { requirePathAndPage(path, pageSize); }
        @Override
        public String id() { return "node.toggle"; }
    }

    public record NodeRefresh(SFMPath path, int pageSize) implements Operation {
        public NodeRefresh { requirePathAndPage(path, pageSize); }
        @Override
        public String id() { return "node.refresh"; }
    }

    public record RootList() implements Operation {
        @Override
        public String id() { return "root.list"; }
    }

    public record RootAdd(SFMPath path) implements Operation {
        public RootAdd { Objects.requireNonNull(path, "path"); }
        @Override
        public String id() { return "root.add"; }
    }

    public record RootRemove(SFMPath path) implements Operation {
        public RootRemove { Objects.requireNonNull(path, "path"); }
        @Override
        public String id() { return "root.remove"; }
    }

    public record LocationSet(
            SFMPathExpression expression,
            long expectedRevision
    ) implements Operation {
        public LocationSet {
            Objects.requireNonNull(expression, "expression");
            if (expectedRevision < 0) {
                throw new IllegalArgumentException("Expected explorer revision must not be negative");
            }
        }

        @Override
        public String id() { return "location.set"; }
    }

    public record ViewSet(SFMExplorerProjection.View view) implements Operation {
        public ViewSet { Objects.requireNonNull(view, "view"); }
        @Override
        public String id() { return "view.set"; }
    }

    public record SortSet(SFMExplorerProjection.Sort sort) implements Operation {
        public SortSet { Objects.requireNonNull(sort, "sort"); }
        @Override
        public String id() { return "sort.set"; }
    }

    public record GroupSet(SFMExplorerProjection.Group group) implements Operation {
        public GroupSet { Objects.requireNonNull(group, "group"); }
        @Override
        public String id() { return "group.set"; }
    }

    public record HoistSet(SFMExplorerProjection.Hoist hoist) implements Operation {
        public HoistSet { Objects.requireNonNull(hoist, "hoist"); }
        @Override
        public String id() { return "root.hoist.set"; }
    }

    public record PathDisplaySet(SFMExplorerProjection.PathDisplay pathDisplay) implements Operation {
        public PathDisplaySet { Objects.requireNonNull(pathDisplay, "pathDisplay"); }
        @Override
        public String id() { return "path-display.set"; }
    }

    public record FilterSet(String query) implements Operation {
        public FilterSet {
            query = Objects.requireNonNull(query, "query").strip();
            if (query.isEmpty()) throw new IllegalArgumentException("Explorer filter query must not be blank");
            if (query.length() > 1024) throw new IllegalArgumentException("Explorer filter query is too long");
            if (query.indexOf('\n') >= 0 || query.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Explorer filter query must be one line");
            }
        }
        @Override
        public String id() { return "filter.set"; }
    }

    public record FilterClear() implements Operation {
        @Override
        public String id() { return "filter.clear"; }
    }

    public SFMExplorerActionRequest {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(ifNoMatch, "ifNoMatch");
        if (selector.domain() != SFMEntitySelector.Domain.EXPLORER) {
            throw new IllegalArgumentException("Explorer actions require an explorer selector");
        }
        if (ifNoMatch == IfNoMatch.OPEN_NEW && selector.isExactIdentity()) {
            throw new IllegalArgumentException("An exact explorer id cannot be combined with OPEN_NEW");
        }
        if (ifNoMatch == IfNoMatch.OPEN_NEW && !(operation instanceof RootAdd)) {
            throw new IllegalArgumentException("OPEN_NEW is only meaningful for root.add");
        }
    }

    private static void requirePathAndPage(SFMPath path, int pageSize) {
        Objects.requireNonNull(path, "path");
        if (pageSize <= 0) throw new IllegalArgumentException("Page size must be positive");
    }
}
