package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** One registered action adapter for the authoritative typed explorer engine. */
public final class SFMExplorerAction implements SFMClientAction<SFMClientActionContext> {
    public enum Operation {
        NODE_EXPAND,
        NODE_COLLAPSE,
        NODE_TOGGLE,
        NODE_REFRESH,
        ROOT_ADD,
        ROOT_REMOVE,
        VIEW_SET,
        SORT_SET,
        GROUP_SET,
        HOIST_SET
    }

    private final Operation operation;

    public SFMExplorerAction(Operation operation) {
        this.operation = java.util.Objects.requireNonNull(operation, "operation");
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case NODE_EXPAND -> "Expand explorer node";
            case NODE_COLLAPSE -> "Collapse explorer node";
            case NODE_TOGGLE -> "Toggle explorer node";
            case NODE_REFRESH -> "Refresh explorer node";
            case ROOT_ADD -> "Add explorer root";
            case ROOT_REMOVE -> "Remove explorer root";
            case VIEW_SET -> "Set explorer view";
            case SORT_SET -> "Set explorer sort";
            case GROUP_SET -> "Set explorer grouping";
            case HOIST_SET -> "Set explorer root hoisting";
        });
    }

    @Override
    public Component description() {
        return Component.literal("Apply one explicit selector-targeted explorer operation");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
                );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "explorer_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    builder.suggest("all");
                    SFMExplorerRuntime.get().repository().stateSnapshot().explorers().keySet().forEach(id ->
                            builder.suggest(SFMEntitySelector.exact(
                                    SFMEntitySelector.Domain.EXPLORER,
                                    id.value()
                            ).canonical())
                    );
                    return builder.buildFuture();
                });
        if (takesPath()) {
            RequiredArgumentBuilder<SFMClientActionSource, String> path = RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument(
                            "path_expression",
                            SFMCanonicalTokenArgument.token()
                    )
                    .suggests((context, builder) -> {
                        builder.suggest("registry://minecraft/item/");
                        return builder.buildFuture();
                    });
            if (operation == Operation.ROOT_ADD) {
                path.executes(context -> invokeOperation(
                        context,
                        SFMExplorerActionRequest.IfNoMatch.FAIL
                ));
                path.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("--if-no-match")
                        .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("fail")
                                .executes(context -> invokeOperation(
                                        context,
                                        SFMExplorerActionRequest.IfNoMatch.FAIL
                                )))
                        .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("open-new")
                                .executes(context -> invokeOperation(
                                        context,
                                        SFMExplorerActionRequest.IfNoMatch.OPEN_NEW
                                ))));
            } else {
                path.executes(context -> invokeOperation(
                        context,
                        SFMExplorerActionRequest.IfNoMatch.FAIL
                ));
            }
            selector.then(path);
        } else {
            RequiredArgumentBuilder<SFMClientActionSource, String> setting = RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("setting", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        for (String value : settingSuggestions()) builder.suggest(value);
                        return builder.buildFuture();
                    })
                    .executes(context -> invokeOperation(
                            context,
                            SFMExplorerActionRequest.IfNoMatch.FAIL
                    ));
            selector.then(setting);
        }
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an explorer selector and the required operation argument"
        )).create();
    }

    private int invokeOperation(
            CommandContext<SFMClientActionSource> context,
            SFMExplorerActionRequest.IfNoMatch ifNoMatch
    ) throws CommandSyntaxException {
        try {
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EXPLORER,
                    SFMCanonicalTokenArgument.get(context, "explorer_selector")
            );
            SFMExplorerActionRequest request = new SFMExplorerActionRequest(
                    selector,
                    typedOperation(context),
                    ifNoMatch
            );
            SFMExplorerActionResult result = SFMExplorerRuntime.get().executeAndOpen(
                    request,
                    context.getSource().context()
            );
            if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED) {
                String detail = result.diagnostics().isEmpty()
                        ? result.status().name().toLowerCase(Locale.ROOT)
                        : result.diagnostics().get(0);
                throw new SimpleCommandExceptionType(Component.literal(
                        "Explorer operation failed: " + detail
                )).create();
            }
            for (SFMExplorerActionResult.TargetResult target : result.targets()) {
                context.getSource().sendFeedback(Component.literal(
                        target.explorerId().value() + ": "
                                + target.outcome().name().toLowerCase(Locale.ROOT).replace('_', '-')
                ));
            }
            return (int) Math.max(1, result.targets().size());
        } catch (CommandSyntaxException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()
            )).create();
        }
    }

    private SFMExplorerActionRequest.Operation typedOperation(CommandContext<SFMClientActionSource> context) {
        if (takesPath()) {
            SFMPath path = concretePath(SFMCanonicalTokenArgument.get(context, "path_expression"));
            return switch (operation) {
                case NODE_EXPAND -> new SFMExplorerActionRequest.NodeExpand(
                        path,
                        SFMExplorerRuntime.DEFAULT_PAGE_SIZE
                );
                case NODE_COLLAPSE -> new SFMExplorerActionRequest.NodeCollapse(path);
                case NODE_TOGGLE -> new SFMExplorerActionRequest.NodeToggle(
                        path,
                        SFMExplorerRuntime.DEFAULT_PAGE_SIZE
                );
                case NODE_REFRESH -> new SFMExplorerActionRequest.NodeRefresh(
                        path,
                        SFMExplorerRuntime.DEFAULT_PAGE_SIZE
                );
                case ROOT_ADD -> new SFMExplorerActionRequest.RootAdd(path);
                case ROOT_REMOVE -> new SFMExplorerActionRequest.RootRemove(path);
                default -> throw new AssertionError("Path operation expected");
            };
        }
        String setting = StringArgumentType.getString(context, "setting");
        return switch (operation) {
            case VIEW_SET -> new SFMExplorerActionRequest.ViewSet(parseView(setting));
            case SORT_SET -> new SFMExplorerActionRequest.SortSet(parseSort(setting));
            case GROUP_SET -> new SFMExplorerActionRequest.GroupSet(parseGroup(setting));
            case HOIST_SET -> new SFMExplorerActionRequest.HoistSet(parseHoist(setting));
            default -> throw new AssertionError("Setting operation expected");
        };
    }

    private boolean takesPath() {
        return switch (operation) {
            case NODE_EXPAND, NODE_COLLAPSE, NODE_TOGGLE, NODE_REFRESH, ROOT_ADD, ROOT_REMOVE -> true;
            case VIEW_SET, SORT_SET, GROUP_SET, HOIST_SET -> false;
        };
    }

    private List<String> settingSuggestions() {
        return switch (operation) {
            case VIEW_SET -> List.of("sfm:list", "sfm:small_icons");
            case SORT_SET -> List.of("sfm:name", "sfm:extension", "sfm:icon");
            case GROUP_SET -> List.of("sfm:hierarchy", "sfm:none");
            case HOIST_SET -> List.of("auto", "show-roots");
            default -> List.of();
        };
    }

    private static SFMPath concretePath(String text) {
        SFMPathExpression expression = SFMPathExpression.parse(text);
        if (!expression.canonical().equals(text)) {
            throw new IllegalArgumentException(
                    "Explorer path expression must use its canonical spelling: " + expression.canonical()
            );
        }
        if (!(expression instanceof SFMPathExpression.Literal literal)) {
            throw new IllegalArgumentException("This explorer operation requires one concrete path");
        }
        return literal.path();
    }

    private static SFMExplorerProjection.View parseView(String value) {
        return switch (value) {
            case "sfm:list" -> SFMExplorerProjection.View.LIST;
            case "sfm:small_icons" -> SFMExplorerProjection.View.SMALL_ICONS;
            default -> throw new IllegalArgumentException("Unknown explorer view: " + value);
        };
    }

    private static SFMExplorerProjection.Sort parseSort(String value) {
        return switch (value) {
            case "sfm:name" -> SFMExplorerProjection.Sort.NAME;
            case "sfm:extension" -> SFMExplorerProjection.Sort.EXTENSION;
            case "sfm:icon" -> SFMExplorerProjection.Sort.ICON;
            default -> throw new IllegalArgumentException("Unknown explorer sort: " + value);
        };
    }

    private static SFMExplorerProjection.Group parseGroup(String value) {
        return switch (value) {
            case "sfm:hierarchy" -> SFMExplorerProjection.Group.HIERARCHY;
            case "sfm:none" -> SFMExplorerProjection.Group.NONE;
            default -> throw new IllegalArgumentException("Unknown explorer group: " + value);
        };
    }

    private static SFMExplorerProjection.Hoist parseHoist(String value) {
        return switch (value) {
            case "auto" -> SFMExplorerProjection.Hoist.AUTO;
            case "show-roots" -> SFMExplorerProjection.Hoist.SHOW_ROOTS;
            default -> throw new IllegalArgumentException("Unknown explorer root-hoist mode: " + value);
        };
    }
}
