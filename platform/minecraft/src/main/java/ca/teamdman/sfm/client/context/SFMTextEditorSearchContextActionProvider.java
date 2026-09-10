package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.search.SFMExplorerSearchText;
import ca.teamdman.sfm.client.search.SFMTextEditorSearchText;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Flat, inspectable actions on the captured document; no opaque widget dispatch. */
public final class SFMTextEditorSearchContextActionProvider implements SFMContextActionProvider {
    @Override public List<Offer> offers(Request request) {
        if (!new ca.teamdman.sfm.client.action.SFMTextEditorSearchAction(
                ca.teamdman.sfm.client.action.SFMTextEditorSearchAction.Kind.SELECT)
                .requirement().resolve(request.actionContext()).isAvailable()) return List.of();
        if (request.focusedContribution().map(SFMContextContribution::projection)
                .filter(SFMContextDocumentProjection.class::isInstance).isEmpty()) return List.of();
        var select = new ResourceLocation("sfm", "document/search/select");
        return List.of(
                new Offer(0, SFMActionChoice.invoke(select, "add-next", SFMExplorerSearchText.value(SFMTextEditorSearchText.ADD_NEXT))),
                new Offer(1, SFMActionChoice.invoke(select, "all", SFMExplorerSearchText.value(SFMTextEditorSearchText.ALL))),
                new Offer(2, SFMActionChoice.continuation(new ResourceLocation("sfm", "document/search/query"), "",
                        SFMExplorerSearchText.value(SFMTextEditorSearchText.QUERY))),
                new Offer(3, SFMActionChoice.continuation(new ResourceLocation("sfm", "document/search/toggle"), "",
                        SFMExplorerSearchText.value(SFMTextEditorSearchText.TOGGLE))));
    }
}
