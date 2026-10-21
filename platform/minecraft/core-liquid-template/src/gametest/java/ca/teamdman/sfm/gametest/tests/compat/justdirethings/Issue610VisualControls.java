package ca.teamdman.sfm.gametest.tests.compat.justdirethings;

/** Pure scheduling only: callers press real buttons and report observed results. */
public final class Issue610VisualControls {
    public static final int SELECTION_TICKS = 60;
    public static final int ACTION_GAP_TICKS = 40;
    public static final int ACTION_TIMEOUT_TICKS = 100;

    public enum Mode { SELECTING, MANUAL, AUTOMATIC }
    public enum Phase { FIRST_RUN, DRAIN, SECOND_RUN, DONE }
    public enum Action { NONE, PRESS_MANAGER, PRESS_DRAIN }

    private final long startedTick;
    private long lastTick;
    private long earliestActionTick;
    private long pendingSince;
    private Mode mode = Mode.SELECTING;
    private Phase phase = Phase.FIRST_RUN;
    private Action pending = Action.NONE;
    private String failure;

    public Issue610VisualControls() { this(0); }

    public Issue610VisualControls(long startedTick) {
        if (startedTick < 0) throw new IllegalArgumentException("Negative fixture start tick");
        this.startedTick = startedTick;
        lastTick = startedTick;
        earliestActionTick = startedTick + SELECTION_TICKS;
    }

    public Mode mode() { return mode; }
    public Phase phase() { return phase; }
    public Action pendingAction() { return pending; }

    /**
     * Sample once per server tick, using actual marker count and observed drain.
     * A lever sample at the 60-tick boundary selects manual before automation.
     * Manual mode emits no synthetic actions and has no acknowledgement timeout.
     * Completion/reset belongs to the fixture; a fresh instance starts a new run.
     */
    public Action tick(long tick, boolean leverPowered, boolean runButtonPowered,
                       boolean drainButtonPowered, int completedRuns, boolean drained) {
        if (failure != null) throw new IllegalStateException(failure);
        if (tick < lastTick || completedRuns < 0) {
            throw new IllegalArgumentException("Fixture observations must use increasing ticks and non-negative counts");
        }
        lastTick = tick;
        if (mode == Mode.SELECTING) {
            if (leverPowered && tick - startedTick <= SELECTION_TICKS) mode = Mode.MANUAL;
            else if (tick - startedTick >= SELECTION_TICKS) mode = Mode.AUTOMATIC;
        }
        if (pending != Action.NONE && tick - pendingSince > ACTION_TIMEOUT_TICKS) {
            timedOut();
        }

        boolean acknowledged = switch (phase) {
            case FIRST_RUN -> completedRuns >= 1;
            case DRAIN -> drained;
            case SECOND_RUN -> completedRuns >= 2;
            case DONE -> false;
        };
        if (acknowledged) {
            phase = switch (phase) {
                case FIRST_RUN -> Phase.DRAIN;
                case DRAIN -> Phase.SECOND_RUN;
                case SECOND_RUN, DONE -> Phase.DONE;
            };
            pending = Action.NONE;
            earliestActionTick = tick + ACTION_GAP_TICKS;
        }
        if (phase == Phase.DONE || mode != Mode.AUTOMATIC) return Action.NONE;
        if (pending != Action.NONE) {
            if (tick - pendingSince >= ACTION_TIMEOUT_TICKS) {
                timedOut();
            }
            return Action.NONE;
        }
        if (tick < earliestActionTick || runButtonPowered || drainButtonPowered) return Action.NONE;
        pending = phase == Phase.DRAIN ? Action.PRESS_DRAIN : Action.PRESS_MANAGER;
        pendingSince = tick;
        return pending;
    }

    private void timedOut() {
        failure = "No observed acknowledgement for " + pending + " during " + phase
                + " within " + ACTION_TIMEOUT_TICKS + " ticks; no automatic retry";
        throw new IllegalStateException(failure);
    }
}
