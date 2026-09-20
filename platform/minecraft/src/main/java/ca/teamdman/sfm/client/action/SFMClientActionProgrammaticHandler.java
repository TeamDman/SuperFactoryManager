package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.value.SFMValue;

@FunctionalInterface
public interface SFMClientActionProgrammaticHandler {
    /** Input has passed the descriptor, live-context, consent, scope and budget checks. */
    SFMValue invoke(SFMValue validatedInput, SFMClientActionProgrammaticContext context);
}
