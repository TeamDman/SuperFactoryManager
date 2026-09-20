package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.client.program.ClientProgramConsentReview;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningAcknowledgement;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Public-only review text. Exact source bytes remain in the acknowledgement, not in this display projection. */
public final class ClientProgramSigningReview {
    public enum View { SCOPE, SOURCE, DIFF, HISTORY }
    private ClientProgramSigningReview() {}

    public static List<String> lines(ClientManagerSigningAcknowledgement acknowledgement,
                                     ClientProgramSigningController.Target target, String previousSource,
                                     String fingerprint, View view) {
        var snapshot = acknowledgement.snapshot();
        var result = new ArrayList<String>();
        if (view == View.SCOPE) {
            result.add("Signing does not grant permission to execute.");
            result.add("Signer: " + (fingerprint == null ? "Choose a protected local key." : fingerprint));
            result.add("Locked fingerprint is unverified until unlock.");
            result.add("World: " + target.world().serverEndpoint() + " / " + target.world().worldId());
            result.add("Dimension: " + target.dimension());
            result.add("Manager: " + target.position().toShortString());
            result.add("Incarnation: " + snapshot.incarnation());
            result.add("Stored revision: " + snapshot.revision());
            result.add("Source SHA-256: " + acknowledgement.descriptor().sourceSha256());
            result.add("All-label binding SHA-256: " + snapshot.body().bindingSha256());
            result.add("Runtime: " + acknowledgement.descriptor().runtime());
            result.add("Client-verified resolved capabilities:");
            acknowledgement.descriptor().capabilities().forEach(capability -> result.add("  " + capability));
            result.add("Every acknowledged label binding:");
            snapshot.body().labels().forEach((label, positions) -> {
                result.add("  " + label + ":");
                positions.forEach(position -> result.add("    " + BlockPos.of(position).toShortString()));
            });
        } else if (view == View.SOURCE) {
            result.add("Exact acknowledged source; control characters are escaped for display.");
            String[] source = snapshot.body().source().split("\n", -1);
            for (int i = 0; i < source.length; i++) result.add((i + 1) + " | " + source[i]);
        } else if (view == View.DIFF) {
            result.add("- previous remembered/opened source; + exact acknowledged source");
            result.addAll(ClientProgramConsentReview.diff(previousSource == null ? "" : previousSource, snapshot.body().source()));
        } else {
            result.add("Independent public attestations; edits retain historical evidence.");
            snapshot.history().forEach(signature -> {
                result.add(signature.descriptor().equals(acknowledgement.descriptor()) ? "Active for this descriptor:" : "Historical:");
                result.add(signature.fingerprint());
                result.add("Source: " + signature.descriptor().sourceSha256());
                result.add("Capabilities: " + signature.descriptor().capabilities());
            });
            if (snapshot.history().isEmpty()) result.add("No attestations stored.");
        }
        return result.stream().map(ClientProgramSigningReview::visible).toList();
    }

    /** Prevent formatting/bidi controls from hiding what the user is reviewing. */
    static String visible(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t') result.append("\\t");
            else if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == '\u00a7'
                     || c == '\u2028' || c == '\u2029') {
                String hex = Integer.toHexString(c);
                result.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
            }
            else result.append(c);
        }
        return result.toString();
    }
}
