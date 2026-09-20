package ca.teamdman.sfm.client.symbol;

/** Completion handoff owned by the jump controller. */
interface SFMNavigationCompletionGate {
    SFMNavigationCompletionGate DIRECT = new SFMNavigationCompletionGate() {
        @Override
        public void accepted(SFMNavigationRequestWitness witness) {
        }

        @Override
        public void dispatchCompletion(SFMNavigationRequestWitness witness, Runnable completion) {
            completion.run();
        }

        @Override
        public void rejected(
                SFMNavigationRequestWitness witness,
                SFMNavigationRequestWitness.RejectionReason reason
        ) {
        }
    };

    void accepted(SFMNavigationRequestWitness witness);

    void dispatchCompletion(SFMNavigationRequestWitness witness, Runnable completion);

    void rejected(
            SFMNavigationRequestWitness witness,
            SFMNavigationRequestWitness.RejectionReason reason
    );
}
