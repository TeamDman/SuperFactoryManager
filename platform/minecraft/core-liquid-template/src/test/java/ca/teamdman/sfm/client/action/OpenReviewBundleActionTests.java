package ca.teamdman.sfm.client.action;

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewException;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenReviewBundleActionTests {
    @AfterEach
    void resetRepository() {
        OpenReviewBundleAction.setRepositoryForAutomation(null);
    }

    @Test
    void typedActionReportsUsefulNoBundlesStateBeforeOpeningMinecraft() {
        OpenReviewBundleAction.setRepositoryForAutomation(new SFMRepositoryReviewRepository() {
            @Override public List<BundleSummary> listBundles() { return List.of(); }
            @Override public OpenBundle open(String ignored) {
                throw new SFMRepositoryReviewException(SFMRepositoryReviewException.Code.NO_BUNDLES,
                        "No review bundles in managed inbox test-inbox");
            }
        });
        CommandDispatcher<SFMClientActionSource> dispatcher = SFMClientActionDispatcherCompiler.compile(List.of(
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
                Map.entry(SFMResourceLocation.fromNamespaceAndPath("sfm", "review/open_bundle"), new OpenReviewBundleAction())
{% else %}
                Map.entry(new ResourceLocation("sfm", "review/open_bundle"), new OpenReviewBundleAction())
{% endcase %}
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(new Object(), () -> true));

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () ->
                dispatcher.execute("sfm action invoke sfm:review/open_bundle missing", source));
        assertTrue(exception.getRawMessage().getString().contains("No review bundles"));
    }
}
