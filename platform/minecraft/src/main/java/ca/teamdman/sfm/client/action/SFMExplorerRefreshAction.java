package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.action.*;
import ca.teamdman.sfm.client.search.SFMExplorerNavigationText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Refresh is panel-scoped even when the roots are hoisted out of the visible rows. */
public final class SFMExplorerRefreshAction implements SFMClientAction<SFMExplorerSearchAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "explorer/refresh");
    @Override public Component title() { return SFMExplorerNavigationText.REFRESH.getComponent(); }
    @Override public Component description() { return SFMExplorerNavigationText.DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMExplorerSearchAction.Target> requirement() {
        return new SFMExplorerSearchAction(SFMExplorerSearchAction.Kind.FOCUS).requirement();
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) { node.executes(this::invoke); }
    @Override public int execute(SFMExplorerSearchAction.Target target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        if (!target.current()) throw new SimpleCommandExceptionType(Component.literal("Explorer panel changed")).create();
        var snapshot = target.panel().sessionSnapshot();
        var selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, snapshot.id().value());
        for (var root : snapshot.roots()) {
            var result = SFMExplorerRuntime.get().execute(new SFMExplorerActionRequest(selector,
                    new SFMExplorerActionRequest.NodeRefresh(root, SFMExplorerRuntime.DEFAULT_PAGE_SIZE),
                    SFMExplorerActionRequest.IfNoMatch.FAIL));
            if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED)
                throw new SimpleCommandExceptionType(Component.literal("Explorer refresh failed: " + result.diagnostics())).create();
        }
        return 1;
    }
}
