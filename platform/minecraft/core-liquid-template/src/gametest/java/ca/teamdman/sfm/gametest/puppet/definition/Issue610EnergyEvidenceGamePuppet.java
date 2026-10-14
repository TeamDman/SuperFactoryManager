package ca.teamdman.sfm.gametest.puppet.definition;
import ca.teamdman.sfm.gametest.puppet.*;
/** Dedicated evidence: run with --width1280 --height720 --variant1280x720@2.
 * The harness owns viewport restoration; exact quantities come from server/client state. */
@SFMGamePuppet
public final class Issue610EnergyEvidenceGamePuppet {
    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.issue610EnergyEvidence();
    }
}
