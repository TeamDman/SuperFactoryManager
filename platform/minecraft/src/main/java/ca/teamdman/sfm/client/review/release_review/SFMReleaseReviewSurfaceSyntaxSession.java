package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightService;
import ca.teamdman.sfm.client.syntax.SFMTextEditorSyntaxSession;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Independent per-source requests publish as they finish, guarded by editor lifecycle. */
public final class SFMReleaseReviewSurfaceSyntaxSession implements AutoCloseable {
    private final List<SFMTextEditorSyntaxSession> sessions = new ArrayList<>();

    public SFMReleaseReviewSurfaceSyntaxSession(String origin, SFMReleaseReviewV1 review,
            SFMReleaseReviewSurfaceV1.Surface surface, SFMSyntaxHighlightService service,
            Executor publicationExecutor, Consumer<List<FormattingSpan>> publish, Consumer<Throwable> failure,
            Consumer<String> status) {
        var sources = SFMReleaseReviewSurfacePresentation.sources(review, surface);
        var slices = SFMReleaseReviewSurfacePresentation.slices(surface, sources);
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_SURFACE_SYNTAX_STARTED origin={} sources={} slices={}",
                origin, sources.size(), slices.size());
        var publications = new HashMap<String, List<FormattingSpan>>();
        var pending = new java.util.LinkedHashMap<String, String>();
        Runnable publishStatus = () -> status.accept(String.join("; ", pending.values()));
        int index = 0;
        for (var source : sources.values()) {
            String localLanguage = SFMTextDocumentLanguage.fromFileName(source.path()).id();
            if (ca.teamdman.sfm.client.syntax.SFMLocalLexicalStyles.supports(localLanguage)) {
                publications.put(source.id(), SFMReleaseReviewSurfacePresentation.projectSyntax(slices, source.id(),
                        ca.teamdman.sfm.client.syntax.SFMLocalLexicalStyles.highlight(localLanguage, source.text())));
            }
            var language = SFMTextDocumentLanguage.fromFileName(source.path()).remoteWorkerLanguage();
            if (slices.stream().noneMatch(slice -> slice.revisionId().equals(source.id()))) continue;
            if (language.isEmpty()) {
                if (!publications.containsKey(source.id())) pending.put(source.id(),
                        localLanguage + " syntax highlighting: no source provider");
                continue;
            }
            pending.put(source.id(), localLanguage + " syntax highlighting: loading…");
            var session = new SFMTextEditorSyntaxSession(origin + "/diff-source-" + index++, service,
                    publicationExecutor, publication -> {
                if (publication.result().outcome() != SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED) {
                    pending.put(source.id(), localLanguage + " syntax highlighting: " + publication.result().outcome().wireName());
                    publishStatus.run();
                    return;
                }
                var spans = publication.result().spans().stream().map(span -> new FormattingSpan(
                        Math.toIntExact(span.startByte()), Math.toIntExact(span.endByte()),
                        SFMDrawCanvasRemoteSyntaxStyles.parseFormattingNames(span.chatFormatting()))).toList();
                publications.put(source.id(), SFMReleaseReviewSurfacePresentation.projectSyntax(slices, source.id(), spans));
                ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_SURFACE_SYNTAX_PUBLISHED origin={} source={} spans={} projected={}",
                        origin, source.id(), spans.size(), publications.get(source.id()).size());
                publish.accept(publications.values().stream().flatMap(List::stream)
                        .sorted(java.util.Comparator.comparingInt(FormattingSpan::startByte)).toList());
                pending.remove(source.id());
                publishStatus.run();
            }, error -> {
                pending.put(source.id(), localLanguage + " syntax highlighting unavailable: " + error.getClass().getSimpleName());
                publishStatus.run();
                failure.accept(error);
            });
            sessions.add(session);
            session.request(1, language.orElseThrow(), source.text());
        }
        if (!publications.isEmpty()) publish.accept(publications.values().stream().flatMap(List::stream)
                .sorted(java.util.Comparator.comparingInt(FormattingSpan::startByte)).toList());
        publishStatus.run();
    }

    @Override
    public void close() {
        sessions.forEach(SFMTextEditorSyntaxSession::close);
        sessions.clear();
    }
}
