package ca.teamdman.sfm.client.program;

/** Shared admission budget; production supplies only its actual render-tick epoch. */
public final class ClientFrameWorkBudget {
    private final int maximum;
    private long epoch = Long.MIN_VALUE;
    private int used;

    public ClientFrameWorkBudget(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Frame work budget must be positive");
        this.maximum = maximum;
    }

    public boolean tryAcquire(long actualRenderEpoch) {
        if (epoch != actualRenderEpoch) {
            epoch = actualRenderEpoch;
            used = 0;
        }
        if (used >= maximum) return false;
        used++;
        return true;
    }

    public void clear() {
        epoch = Long.MIN_VALUE;
        used = 0;
    }
}
