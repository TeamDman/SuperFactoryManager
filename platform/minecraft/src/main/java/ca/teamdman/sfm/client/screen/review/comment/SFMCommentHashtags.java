package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** UI projection of the kernel parser; comment text remains the sole hashtag authority. */
public final class SFMCommentHashtags {
    private SFMCommentHashtags() {}

    public static Set<String> derive(String text) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(SFMReviewSessionV1Kernel.derivedHashtags(text)));
    }
}
