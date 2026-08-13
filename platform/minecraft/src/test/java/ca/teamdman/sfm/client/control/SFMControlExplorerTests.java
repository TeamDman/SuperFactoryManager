package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.client.control.generated.SfmControlExplorerIfNoMatch;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperation;
import ca.teamdman.sfm.client.control.generated.SfmControlExplorerOperationRequest;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMParseException;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMControlExplorerTests {
    private static final String SET_SELECTOR = "difference(all,union(id(explorer%20one),focused))";
    private static final String FILE_PATH = "file:///C:/folder%20with%20spaces/%CF%80.txt";
    private static final String REGISTRY_PATH = "registry://minecraft/item/minecraft/oak_log";

    @Test
    void listDescribeAndRootListPreserveSelectors() {
        assertOperation(
                parse(SfmControlExplorerOperation.LIST, SET_SELECTOR, "", "", SfmControlExplorerIfNoMatch.FAIL),
                SET_SELECTOR,
                SFMExplorerActionRequest.ListExplorers.class
        );
        assertOperation(
                parse(
                        SfmControlExplorerOperation.DESCRIBE,
                        "id(explorer%20one)",
                        "",
                        "",
                        SfmControlExplorerIfNoMatch.FAIL
                ),
                "id(explorer%20one)",
                SFMExplorerActionRequest.Describe.class
        );
        assertOperation(
                parse(
                        SfmControlExplorerOperation.ROOT_LIST,
                        "focused",
                        "",
                        "",
                        SfmControlExplorerIfNoMatch.FAIL
                ),
                "focused",
                SFMExplorerActionRequest.RootList.class
        );
    }

    @Test
    void rootOperationsPreserveCanonicalPathsAndPolicies() {
        SFMExplorerActionRequest add = parse(
                SfmControlExplorerOperation.ROOT_ADD,
                "difference(all,all)",
                FILE_PATH,
                "",
                SfmControlExplorerIfNoMatch.OPEN_NEW
        );
        SFMExplorerActionRequest.RootAdd addOperation = assertInstanceOf(
                SFMExplorerActionRequest.RootAdd.class,
                add.operation()
        );
        assertEquals("difference(all,all)", add.selector().canonical());
        assertEquals(FILE_PATH, addOperation.path().canonical());
        assertEquals(SFMExplorerActionRequest.IfNoMatch.OPEN_NEW, add.ifNoMatch());

        SFMExplorerActionRequest remove = parse(
                SfmControlExplorerOperation.ROOT_REMOVE,
                "id(explorer-2)",
                REGISTRY_PATH,
                "",
                SfmControlExplorerIfNoMatch.FAIL
        );
        SFMExplorerActionRequest.RootRemove removeOperation = assertInstanceOf(
                SFMExplorerActionRequest.RootRemove.class,
                remove.operation()
        );
        assertEquals("id(explorer-2)", remove.selector().canonical());
        assertEquals(REGISTRY_PATH, removeOperation.path().canonical());
        assertEquals(SFMExplorerActionRequest.IfNoMatch.FAIL, remove.ifNoMatch());
    }

    @Test
    void allNodeOperationsPreserveTheirCanonicalPath() {
        SFMExplorerActionRequest expand = parsePathOperation(SfmControlExplorerOperation.NODE_EXPAND);
        SFMExplorerActionRequest.NodeExpand expandOperation = assertInstanceOf(
                SFMExplorerActionRequest.NodeExpand.class,
                expand.operation()
        );
        assertEquals(REGISTRY_PATH, expandOperation.path().canonical());
        assertEquals(SFMExplorerRuntime.DEFAULT_PAGE_SIZE, expandOperation.pageSize());

        SFMExplorerActionRequest collapse = parsePathOperation(SfmControlExplorerOperation.NODE_COLLAPSE);
        assertEquals(
                REGISTRY_PATH,
                assertInstanceOf(SFMExplorerActionRequest.NodeCollapse.class, collapse.operation()).path().canonical()
        );

        SFMExplorerActionRequest toggle = parsePathOperation(SfmControlExplorerOperation.NODE_TOGGLE);
        SFMExplorerActionRequest.NodeToggle toggleOperation = assertInstanceOf(
                SFMExplorerActionRequest.NodeToggle.class,
                toggle.operation()
        );
        assertEquals(REGISTRY_PATH, toggleOperation.path().canonical());
        assertEquals(SFMExplorerRuntime.DEFAULT_PAGE_SIZE, toggleOperation.pageSize());

        SFMExplorerActionRequest refresh = parsePathOperation(SfmControlExplorerOperation.NODE_REFRESH);
        SFMExplorerActionRequest.NodeRefresh refreshOperation = assertInstanceOf(
                SFMExplorerActionRequest.NodeRefresh.class,
                refresh.operation()
        );
        assertEquals(REGISTRY_PATH, refreshOperation.path().canonical());
        assertEquals(SFMExplorerRuntime.DEFAULT_PAGE_SIZE, refreshOperation.pageSize());
    }

    @Test
    void resourceIdViewValuesMapToTypedValues() {
        assertEquals(
                SFMExplorerProjection.View.LIST,
                assertInstanceOf(
                        SFMExplorerActionRequest.ViewSet.class,
                        parseSetting(SfmControlExplorerOperation.VIEW_SET, "sfm:list").operation()
                ).view()
        );
        assertEquals(
                SFMExplorerProjection.View.SMALL_ICONS,
                assertInstanceOf(
                        SFMExplorerActionRequest.ViewSet.class,
                        parseSetting(SfmControlExplorerOperation.VIEW_SET, "sfm:small_icons").operation()
                ).view()
        );
    }

    @Test
    void resourceIdSortAndGroupValuesMapToTypedValues() {
        assertSort("sfm:name", SFMExplorerProjection.Sort.NAME);
        assertSort("sfm:extension", SFMExplorerProjection.Sort.EXTENSION);
        assertSort("sfm:icon", SFMExplorerProjection.Sort.ICON);
        assertGroup("sfm:hierarchy", SFMExplorerProjection.Group.HIERARCHY);
        assertGroup("sfm:none", SFMExplorerProjection.Group.NONE);
    }

    @Test
    void hoistValuesMapToTypedValues() {
        assertHoist("auto", SFMExplorerProjection.Hoist.AUTO);
        assertHoist("show-roots", SFMExplorerProjection.Hoist.SHOW_ROOTS);
    }

    @Test
    void openNewIsAcceptedOnlyForNonExactRootAdd() {
        SFMExplorerActionRequest accepted = parse(
                SfmControlExplorerOperation.ROOT_ADD,
                "focused",
                FILE_PATH,
                "",
                SfmControlExplorerIfNoMatch.OPEN_NEW
        );
        assertEquals(SFMExplorerActionRequest.IfNoMatch.OPEN_NEW, accepted.ifNoMatch());

        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        SfmControlExplorerOperation.ROOT_ADD,
                        "id(explorer-1)",
                        FILE_PATH,
                        "",
                        SfmControlExplorerIfNoMatch.OPEN_NEW
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        SfmControlExplorerOperation.DESCRIBE,
                        "focused",
                        "",
                        "",
                        SfmControlExplorerIfNoMatch.OPEN_NEW
                )
        );
    }

    @Test
    void malformedAndCrossDomainInputsFailClosed() {
        assertThrows(
                SFMParseException.class,
                () -> parse(
                        SfmControlExplorerOperation.LIST,
                        "union()",
                        "",
                        "",
                        SfmControlExplorerIfNoMatch.FAIL
                )
        );
        assertThrows(
                SFMParseException.class,
                () -> parse(
                        SfmControlExplorerOperation.LIST,
                        "name(review-selection)",
                        "",
                        "",
                        SfmControlExplorerIfNoMatch.FAIL
                )
        );
        assertThrows(
                SFMParseException.class,
                () -> parse(
                        SfmControlExplorerOperation.ROOT_ADD,
                        "focused",
                        "file:///C:/safe/../escape",
                        "",
                        SfmControlExplorerIfNoMatch.FAIL
                )
        );

        assertThrows(IllegalArgumentException.class, () -> parseSetting(
                SfmControlExplorerOperation.VIEW_SET,
                "list"
        ));
        assertThrows(IllegalArgumentException.class, () -> parseSetting(
                SfmControlExplorerOperation.SORT_SET,
                "sfm:unknown"
        ));
        assertThrows(IllegalArgumentException.class, () -> parseSetting(
                SfmControlExplorerOperation.GROUP_SET,
                "sfm:unknown"
        ));
        assertThrows(IllegalArgumentException.class, () -> parseSetting(
                SfmControlExplorerOperation.ROOT_HOIST_SET,
                "sfm:auto"
        ));
    }

    private static SFMExplorerActionRequest parsePathOperation(SfmControlExplorerOperation operation) {
        return parse(operation, SET_SELECTOR, REGISTRY_PATH, "", SfmControlExplorerIfNoMatch.FAIL);
    }

    private static SFMExplorerActionRequest parseSetting(
            SfmControlExplorerOperation operation,
            String setting
    ) {
        return parse(operation, SET_SELECTOR, "", setting, SfmControlExplorerIfNoMatch.FAIL);
    }

    private static SFMExplorerActionRequest parse(
            SfmControlExplorerOperation operation,
            String selector,
            String path,
            String setting,
            SfmControlExplorerIfNoMatch ifNoMatch
    ) {
        return SFMClientControlServer.parseExplorerActionRequest(new SfmControlExplorerOperationRequest(
                "test-token",
                "test-request",
                1,
                operation,
                selector,
                path,
                setting,
                ifNoMatch
        ));
    }

    private static void assertOperation(
            SFMExplorerActionRequest request,
            String selector,
            Class<? extends SFMExplorerActionRequest.Operation> operationType
    ) {
        assertEquals(selector, request.selector().canonical());
        assertInstanceOf(operationType, request.operation());
        assertEquals(SFMExplorerActionRequest.IfNoMatch.FAIL, request.ifNoMatch());
    }

    private static void assertSort(String setting, SFMExplorerProjection.Sort expected) {
        assertEquals(
                expected,
                assertInstanceOf(
                        SFMExplorerActionRequest.SortSet.class,
                        parseSetting(SfmControlExplorerOperation.SORT_SET, setting).operation()
                ).sort()
        );
    }

    private static void assertGroup(String setting, SFMExplorerProjection.Group expected) {
        assertEquals(
                expected,
                assertInstanceOf(
                        SFMExplorerActionRequest.GroupSet.class,
                        parseSetting(SfmControlExplorerOperation.GROUP_SET, setting).operation()
                ).group()
        );
    }

    private static void assertHoist(String setting, SFMExplorerProjection.Hoist expected) {
        assertEquals(
                expected,
                assertInstanceOf(
                        SFMExplorerActionRequest.HoistSet.class,
                        parseSetting(SfmControlExplorerOperation.ROOT_HOIST_SET, setting).operation()
                ).hoist()
        );
    }
}
