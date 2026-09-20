package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import static ca.teamdman.sfm.client.search.SFMExplorerSearchText.*;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Public named search controls shared by pointer controls, key bindings and the palette. */
public final class SFMExplorerSearchAction implements SFMClientAction<SFMExplorerSearchAction.Target> {
    public enum Kind {
        FOCUS("focus", "Focus Explorer search"), TOGGLE("toggle", "Toggle Explorer search option"),
        CLEAR("clear", "Clear Explorer search input"), MOVE("move", "Select Explorer find match"),
        SCOPE("scope", "Set Explorer find scope"), SELECT("select", "Select Explorer matches"),
        ROW("row", "Select an Explorer row");
        public final String path;
        public final String title;
        Kind(String suffix, String title) { path = suffix.equals("row") ? "explorer/selection/row" : "explorer/search/" + suffix; this.title = title; }
        public ResourceLocation id() { return new ResourceLocation("sfm", path); }
    }
    public static final List<String> FIELDS = List.of("find", "filter", "focused");
    public static final List<String> OPTIONS = List.of("case", "whole-word", "regex", "fuzzy", "dot-all", "highlight");
    public static final List<String> MOVES = List.of("next", "previous", "next-wrapping", "previous-wrapping");
    public record Target(SFMClientActionContext context, SFMScreenMultiplexer workspace,
                         SFMWorkspacePanelId id, SFMExplorerPanel panel) {
        boolean current() {
            return SFMClientActionContinuation.capture(context).isCurrent() && workspace.panelInstance(id) == panel;
        }
    }
    private final Kind kind;
    public SFMExplorerSearchAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return (switch (kind) {
        case FOCUS -> ACTION_FOCUS; case TOGGLE -> ACTION_TOGGLE; case CLEAR -> ACTION_CLEAR;
        case MOVE -> ACTION_MOVE; case SCOPE -> ACTION_SCOPE; case SELECT -> ACTION_SELECT; case ROW -> ACTION_ROW;
    }).getComponent(); }
    @Override public Component description() { return ACTION_DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<Target> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()
                    || !(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId()) instanceof SFMExplorerPanel panel))
                return SFMClientActionAvailability.unavailable(FOCUS_REQUIRED.getComponent());
            return SFMClientActionAvailability.available(new Target(context, workspace, context.originatingPanelId(), panel));
        };
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        List<String> first = switch (kind) {
            case FOCUS -> List.of("find", "filter", "body");
            case TOGGLE, CLEAR -> FIELDS;
            case MOVE -> MOVES;
            case SCOPE -> List.of("complete", "materialized");
            case SELECT -> List.of("add-next", "all");
            case ROW -> List.of("replace", "toggle", "range", "toggle-range", "cursor");
        };
        for (String value : first) {
            var child = LiteralArgumentBuilder.<SFMClientActionSource>literal(value);
            if (kind == Kind.TOGGLE) for (String option : OPTIONS)
                child.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(option)
                        .executes(this::invoke));
            else if (kind == Kind.ROW) child.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(this::invoke));
            else child.executes(this::invoke);
            node.then(child);
        }
    }
    @Override public int execute(Target target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        var nodes = context.getNodes();
        String value = nodes.get(nodes.size() - (kind == Kind.TOGGLE || kind == Kind.ROW ? 2 : 1)).getNode().getName();
        String option = kind == Kind.TOGGLE ? nodes.get(nodes.size() - 1).getNode().getName() : "";
        if (!target.current()) throw new SimpleCommandExceptionType(Component.literal("Explorer panel changed")).create();
        try {
            switch (kind) {
                case FOCUS -> target.panel().focusSearch(value);
                case TOGGLE -> target.panel().toggleSearchOption(value, option);
                case CLEAR -> target.panel().clearSearch(value);
                case MOVE -> target.panel().moveSearch(value.startsWith("previous") ? -1 : 1, value.endsWith("-wrapping"));
                case SCOPE -> target.panel().setFindMaterializedOnly(value.equals("materialized"));
                case SELECT -> target.panel().selectSearchMatches(value.equals("all"));
                case ROW -> target.panel().selectRow(value, ca.teamdman.sfm.client.explorer.SFMPath.parse(
                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path")));
            }
        } catch (IllegalArgumentException failure) {
            throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create();
        }
        return 1;
    }
}
