package ca.teamdman.sfml.ast;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FunctionRegistry {
    private static final Map<String, FunctionHandler> HANDLERS = new HashMap<>();

    static {
        // get_label_count(labelAccess)
        register("get_label_count", args -> {
            List<FunctionArg> arg = args.args();
            if (arg.size() != 1 || arg.get(0).kind() != FunctionArg.Kind.LABEL_ACCESS) {
                throw new IllegalArgumentException("get_label_count expects exactly 1 argument: labelAccess");
            }
            return new NumFuncGetLabelCount(arg.get(0).asLabelAccess());
        });

        // get_thing_count(labelAccess[, resourceIds])
        register("get_thing_count", args -> {
            List<FunctionArg> arg = args.args();
            if (arg.isEmpty()) {
                throw new IllegalArgumentException("get_thing_count expects 1 or 2 arguments: labelAccess[, resourceIds]");
            }
            if (arg.get(0).kind() != FunctionArg.Kind.LABEL_ACCESS) {
                throw new IllegalArgumentException("get_thing_count first argument must be labelAccess");
            }
            LabelAccess la = arg.get(0).asLabelAccess();
            ResourceIdSet ids = ResourceIdSet.MATCH_ALL;
            if (arg.size() >= 2) {
                if (arg.get(1).kind() != FunctionArg.Kind.RESOURCE_IDS) {
                    throw new IllegalArgumentException("get_thing_count second argument must be resource ids if provided");
                }
                ids = arg.get(1).asResourceIds();
            }
            if (arg.size() > 2) {
                throw new IllegalArgumentException("get_thing_count accepts at most 2 arguments");
            }
            return new NumFuncGetThingCount(la, ids);
        });
    }

    private FunctionRegistry() {
    }

    public static void register(String name, FunctionHandler handler) {
        HANDLERS.put(name.toLowerCase(Locale.ROOT), handler);
    }

    public static FunctionHandler get(String name) {
        return HANDLERS.get(name.toLowerCase(Locale.ROOT));
    }
}
