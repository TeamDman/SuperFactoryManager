package ca.teamdman.sfm.client.explorer;

/** Read-only boundary that captures one immutable selector repository view. */
@FunctionalInterface
public interface SFMSelectorRepository<I> {
    SFMSelectorRepositorySnapshot<I> snapshot();

    static <I> SFMSelectorRepository<I> immutable(SFMSelectorRepositorySnapshot<I> snapshot) {
        return () -> snapshot;
    }
}
