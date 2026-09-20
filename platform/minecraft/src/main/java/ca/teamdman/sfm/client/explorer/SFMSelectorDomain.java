package ca.teamdman.sfm.client.explorer;

import java.util.Optional;

/**
 * Typed boundary between a selector AST domain and one repository identity
 * type. Implementations expose data only; resolution has no UI side effects.
 */
public interface SFMSelectorDomain<I> {
    SFMEntitySelector.Domain domain();

    SFMSelectorRepository<I> repository();

    String stableText(I id);

    boolean supportsFocus();

    boolean supportsNames();

    boolean supportsResolution();

    Optional<SFMSelectorResolution.Diagnostic> unsupportedDiagnostic();
}
