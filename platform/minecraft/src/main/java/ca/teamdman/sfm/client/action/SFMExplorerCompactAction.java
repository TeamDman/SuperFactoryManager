package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.search.SFMExplorerCompactText;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Discoverable semantic preferences, deliberately scoped to one originating Explorer. */
public final class SFMExplorerCompactAction implements SFMClientAction<SFMExplorerSearchAction.Target>, SFMClientActionCompletion {
    public enum Kind {
        SET("set", SFMExplorerCompactText.SET), UNMERGE("unmerge", SFMExplorerCompactText.UNMERGE),
        RESET("reset", SFMExplorerCompactText.RESET), COPY("preset/copy", SFMExplorerCompactText.COPY),
        APPLY("preset/apply", SFMExplorerCompactText.APPLY);
        public final String path; final LocalizationEntry text;
        Kind(String suffix, LocalizationEntry text) { path = "explorer/compact/" + suffix; this.text = text; }
        public ResourceLocation id() { return new ResourceLocation("sfm", path); }
    }
    private final Kind kind;
    public SFMExplorerCompactAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return kind.text.getComponent(); }
    @Override public Component description() { return SFMExplorerCompactText.DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMExplorerSearchAction.Target> requirement() {
        return new SFMExplorerSearchAction(SFMExplorerSearchAction.Kind.FOCUS).requirement();
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        switch (kind) {
            case COPY -> node.executes(this::invoke);
            case SET -> node.then(RequiredArgumentBuilder.<SFMClientActionSource, Boolean>argument("enabled", BoolArgumentType.bool()).executes(this::invoke));
            case UNMERGE, RESET -> node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("path", SFMCanonicalTokenArgument.token()).executes(this::invoke));
            case APPLY -> node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("preset_json", StringArgumentType.greedyString()).executes(this::invoke));
        }
    }
    @Override public int execute(SFMExplorerSearchAction.Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.current()) throw new SimpleCommandExceptionType(Component.literal("Explorer panel changed")).create();
        String argument = switch (kind) {
            case COPY -> ""; case SET -> Boolean.toString(BoolArgumentType.getBool(context, "enabled"));
            case UNMERGE, RESET -> SFMCanonicalTokenArgument.get(context, "path");
            case APPLY -> StringArgumentType.getString(context, "preset_json");
        };
        try { target.panel().compactPaths(kind, argument); }
        catch (IllegalArgumentException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create(); }
        var feedback = (kind == Kind.COPY ? SFMExplorerCompactText.COPIED : SFMExplorerCompactText.APPLIED).getComponent();
        target.workspace().showWorkspaceToast("sfm:explorer-compact:" + target.id(), feedback, false);
        return 1;
    }
    @Override public Optional<List<SFMPaletteCandidate>> argumentCandidates(String command, int argumentStart, int cursor,
            SFMClientActionContext context) { return Optional.empty(); }
    @Override public boolean acceptsContinuation(String command, SFMClientActionContext context) {
        return kind == Kind.APPLY && command.strip().equals("sfm action invoke " + kind.id());
    }
}
