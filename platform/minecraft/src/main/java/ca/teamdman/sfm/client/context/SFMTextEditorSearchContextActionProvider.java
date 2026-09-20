package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.search.SFMExplorerSearchText;
import ca.teamdman.sfm.client.search.SFMTextEditorSearchText;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;

/** Flat, inspectable actions on the captured document; no opaque widget dispatch. */
public final class SFMTextEditorSearchContextActionProvider implements SFMContextActionProvider {
    @Override public List<Offer> offers(Request request) {
        if (!new ca.teamdman.sfm.client.action.SFMTextEditorSearchAction(
                ca.teamdman.sfm.client.action.SFMTextEditorSearchAction.Kind.SELECT)
                .requirement().resolve(request.actionContext()).isAvailable()) return List.of();
        if (request.focusedContribution().map(SFMContextContribution::projection)
                .filter(SFMContextDocumentProjection.class::isInstance).isEmpty()) return List.of();
        var document = request.focusedContribution().map(SFMContextContribution::projection)
                .map(SFMContextDocumentProjection.class::cast).orElseThrow();
        var answer = new ArrayList<Offer>();
        ca.teamdman.sfm.client.action.SFMDocumentSelectionCopyAction.captureChoice(document)
                .ifPresent(choice -> answer.add(new Offer(0, choice)));
        var select = new ResourceLocation("sfm", "document/search/select");
        answer.add(new Offer(1, SFMActionChoice.invoke(select, "add-next", SFMExplorerSearchText.value(SFMTextEditorSearchText.ADD_NEXT))));
        answer.add(new Offer(2, SFMActionChoice.invoke(select, "all", SFMExplorerSearchText.value(SFMTextEditorSearchText.ALL))));
        answer.add(new Offer(3, SFMActionChoice.continuation(new ResourceLocation("sfm", "document/search/query"), "",
                SFMExplorerSearchText.value(SFMTextEditorSearchText.QUERY))));
        answer.add(new Offer(4, SFMActionChoice.continuation(new ResourceLocation("sfm", "document/search/toggle"), "",
                SFMExplorerSearchText.value(SFMTextEditorSearchText.TOGGLE))));
        return List.copyOf(answer);
    }
}
