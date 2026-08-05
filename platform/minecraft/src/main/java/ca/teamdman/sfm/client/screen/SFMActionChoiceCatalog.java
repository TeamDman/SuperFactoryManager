package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/** Pure filtering seam shared by the bounded chooser and focused tests. */
public final class SFMActionChoiceCatalog {
    private SFMActionChoiceCatalog() {
    }

    public static List<SFMActionChoice> available(
            List<SFMActionChoice> candidates,
            Function<ResourceLocation, SFMClientAction<?>> actionLookup,
            SFMClientActionCommandTree tree,
            SFMClientActionContext context
    ) {
        LinkedHashSet<String> commands = new LinkedHashSet<>();
        List<SFMActionChoice> answer = new ArrayList<>();
        SFMClientActionSource source = new SFMClientActionSource(context);
        for (SFMActionChoice candidate : candidates) {
            if (!commands.add(candidate.command())) continue;
            SFMClientAction<?> action = actionLookup.apply(candidate.actionId());
            if (action == null || !action.requirement().resolve(context).isAvailable()) continue;
            if (!SFMClientActionExecutor.isExecutable(tree.parse(candidate.command(), source))) continue;
            answer.add(candidate);
        }
        return List.copyOf(answer);
    }
}
