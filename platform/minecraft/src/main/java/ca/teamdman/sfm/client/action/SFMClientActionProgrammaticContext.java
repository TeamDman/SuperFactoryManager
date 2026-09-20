package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.ClientProgramIdentity;

import java.util.Objects;
import java.util.Optional;

/** Host-created invocation metadata; it is never an argument supplied by an SFML program. */
public record SFMClientActionProgrammaticContext(
        SFMClientActionAuthorizationService.PrincipalKind principal,
        Optional<ClientProgramIdentity> caller
) {
    public SFMClientActionProgrammaticContext {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(caller);
        if ((principal == SFMClientActionAuthorizationService.PrincipalKind.CLIENT_PROGRAM) != caller.isPresent()) {
            throw new IllegalArgumentException("Program principal requires its exact identity");
        }
    }
}
