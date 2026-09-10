package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewStorageActionTests {
    @Test void contextualOpenAndCopyPreserveExactQuotedPathAndRevision() {
        Path path = Path.of("D:/review space/雪.sfm-review.json");
        String revision = "review-document:sha256:abc123";
        for (var choice : SFMReviewStorageAction.choices(path, Optional.of(revision))) {
            var dispatcher = new CommandDispatcher<SFMClientActionSource>();
            var kind = choice.actionId().equals(SFMReviewStorageAction.Kind.OPEN.id())
                    ? SFMReviewStorageAction.Kind.OPEN : SFMReviewStorageAction.Kind.COPY;
            LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal(kind.id().toString());
            new SFMReviewStorageAction(kind).configureCommandNode(node);
            dispatcher.register(node);
            String input = choice.command().substring("sfm action invoke ".length());
            var parsed = dispatcher.parse(input, new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null)));
            assertTrue(SFMClientActionExecutor.isExecutable(parsed));
            var context = parsed.getContext().build(input);
            assertEquals(path.toString(), StringArgumentType.getString(context, "review_file"));
            assertEquals(revision, StringArgumentType.getString(context, "document_revision"));
            assertFalse(choice.continuation());
        }
        var choices = SFMReviewStorageAction.choices(path, Optional.empty());
        assertEquals(2, choices.size());
        assertNotEquals(choices.get(0).actionId(), choices.get(1).actionId());
        assertTrue(choices.get(0).displayText().startsWith("Open"));
        assertTrue(choices.get(1).displayText().startsWith("Copy"));
    }
}
