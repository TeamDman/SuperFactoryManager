package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.action.*;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerNavigationChoices;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.network.chat.Component;

/** Explicit parent navigation may grant that filesystem root, unlike arbitrary location expressions. */
public final class SFMExplorerParentAction implements SFMClientAction<SFMExplorerSearchAction.Target> {
    @Override public Component title() { return ca.teamdman.sfm.client.search.SFMExplorerNavigationText.PARENT_TITLE.getComponent(); }
    @Override public Component description() { return ca.teamdman.sfm.client.search.SFMExplorerNavigationText.PARENT_DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMExplorerSearchAction.Target> requirement() {
        return new SFMExplorerSearchAction(SFMExplorerSearchAction.Kind.FOCUS).requirement();
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument("root", SFMCanonicalTokenArgument.token())
                .then(LiteralArgumentBuilder.<SFMClientActionSource>literal("--expected-revision")
                        .then(RequiredArgumentBuilder.<SFMClientActionSource,Long>argument("revision", LongArgumentType.longArg(0)).executes(this::invoke))));
    }
    @Override public int execute(SFMExplorerSearchAction.Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        try {
            if (!target.current()) throw new IllegalArgumentException("Explorer panel changed");
            var snapshot = target.panel().sessionSnapshot();
            var root = SFMPath.parse(SFMCanonicalTokenArgument.get(context, "root"));
            if (snapshot.revision() != LongArgumentType.getLong(context, "revision") || !snapshot.roots().contains(root))
                throw new IllegalArgumentException("Explorer roots changed; reopen the context menu");
            var parent = SFMExplorerNavigationChoices.parent(root).orElseThrow(() -> new IllegalArgumentException("This root has no supported parent"));
            var selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, snapshot.id().value());
            // First acquire the explicitly requested read root through the existing authority seam.
            // Never remove the original if acquisition fails. No disk write is involved.
            apply(selector, new SFMExplorerActionRequest.RootAdd(parent));
            apply(selector, new SFMExplorerActionRequest.RootRemove(root));
            return 1;
        } catch (IllegalArgumentException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create(); }
    }
    private static void apply(SFMEntitySelector selector, SFMExplorerActionRequest.Operation operation) {
        var result = SFMExplorerRuntime.get().execute(new SFMExplorerActionRequest(selector, operation, SFMExplorerActionRequest.IfNoMatch.FAIL));
        if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED) throw new IllegalArgumentException("Parent navigation failed: " + result.diagnostics());
    }
}
