package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.OpenReviewBundleAction;
import ca.teamdman.sfm.client.review.repository.SFMManagedReviewBundleRepository;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Installs the canonical bundle in an isolated managed inbox and persistent session store. */
public final class PrepareRepositoryReviewFixturePuppetAction implements SFMPuppetAction {
    public static final String BUNDLE_NAME = "SFM d07bef66c to 8e9946d9f review bundle";
    public static final String BUNDLE_PROPERTY = "sfm.repositoryReviewPuppetBundle";

    public static String requestedBundle() {
        return System.getProperty(BUNDLE_PROPERTY, BUNDLE_NAME).strip();
    }

    @Override
    public String description() {
        return "prepare managed repository-review fixture";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        try {
            if (!requestedBundle().equals(BUNDLE_NAME)) {
                OpenReviewBundleAction.setRepositoryForAutomation(null);
                return true;
            }
            Path root = Files.createTempDirectory("sfm-repository-review-puppet-");
            Path inbox = Files.createDirectories(root.resolve("review-bundles"));
            Path stores = Files.createDirectories(root.resolve("review-sessions"));
            String json = Files.readString(fixturePath(), StandardCharsets.UTF_8)
                    .replace("Repository review bundle v1 conformance fixture", BUNDLE_NAME)
                    .replace("\"label\": \"Before\"", "\"label\": \"d07bef66c\"")
                    .replace("\"label\": \"After\"", "\"label\": \"8e9946d9f\"");
            Files.writeString(inbox.resolve("sfm-d07bef66c-8e9946d9f.json"), json, StandardCharsets.UTF_8);
            OpenReviewBundleAction.setRepositoryForAutomation(new SFMManagedReviewBundleRepository(inbox,
                    id -> new SFMReviewSessionV1Store(stores.resolve(SFMReviewSessionV1Kernel.sha256(
                            id.getBytes(StandardCharsets.UTF_8)) + ".json"))));
            return true;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare repository review fixture", exception);
        }
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/repository-review-bundle-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Canonical repository-review fixture not found");
    }
}
