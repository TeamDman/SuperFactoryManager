package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.search.SFMTextEditorSearchText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Named editor operations, shared by keyboard and contextual palette choices. */
public final class SFMTextEditorSearchAction implements SFMClientAction<SFMTextEditorPanel>, SFMClientActionCompletion {
    public enum Kind { QUERY, TOGGLE, SELECT }
    private final Kind kind;
    public SFMTextEditorSearchAction(Kind kind) { this.kind = kind; }
    @Override public java.util.Optional<List<SFMPaletteCandidate>> argumentCandidates(
            String command, int argumentStart, int cursor, SFMClientActionContext context) {
        return java.util.Optional.empty(); // Existing Brigadier query/option nodes own construction.
    }
    @Override public boolean acceptsContinuation(String command, SFMClientActionContext context) {
        return kind != Kind.SELECT && command.strip().equals("sfm action invoke sfm:document/search/"
                + kind.name().toLowerCase(java.util.Locale.ROOT));
    }
    @Override public Component title() { return (switch (kind) {
        case QUERY -> SFMTextEditorSearchText.QUERY;
        case TOGGLE -> SFMTextEditorSearchText.TOGGLE;
        case SELECT -> SFMTextEditorSearchText.SELECT;
    }).getComponent(); }
    @Override public Component description() { return SFMTextEditorSearchText.DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMTextEditorPanel> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()
                    || !(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId()) instanceof SFMTextEditorPanel panel)
                    || !panel.supportsMatchSelection())
                return SFMClientActionAvailability.unavailable(SFMTextEditorSearchText.REQUIRED.getComponent());
            return SFMClientActionAvailability.available(panel);
        };
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (kind == Kind.QUERY) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("query", StringArgumentType.string())
                    .executes(this::invoke));
        } else {
            for (String value : kind == Kind.SELECT ? List.of("add-next", "all")
                    : List.of("case", "whole-word", "regex", "fuzzy", "dot-all"))
                node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(value).executes(this::invoke));
        }
    }
    @Override public int execute(SFMTextEditorPanel panel, CommandContext<SFMClientActionSource> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        try {
        switch (kind) {
            case QUERY -> panel.setMatchQuery(StringArgumentType.getString(context, "query"));
            case TOGGLE -> panel.toggleMatchOption(context.getNodes().get(context.getNodes().size() - 1).getNode().getName());
            case SELECT -> panel.selectMatches(context.getNodes().get(context.getNodes().size() - 1).getNode().getName().equals("all"),
                    () -> { var available = requirement().resolve(source.context());
                        return available.isAvailable() && available.target() == panel; }, message -> {
                        source.sendFeedback(message);
                        // Dynamic keybindings deliberately discard their generic feedback sink.
                        // Keep bounded/stale/empty-seed results visible there as well as in palettes.
                        if (source.context().originatingHostIsCurrent().getAsBoolean()
                                && source.context().originatingHost() instanceof SFMScreenMultiplexer workspace)
                            workspace.showWorkspaceToast("sfm:document-find:" + source.context().originatingPanelId(),
                                    message, false);
                    });
        }
        } catch (IllegalArgumentException invalid) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal(ca.teamdman.sfm.client.search.SFMExplorerSearchText.value(SFMTextEditorSearchText.FAILED,
                            invalid.getMessage()))).create();
        }
        return 1;
    }
}
