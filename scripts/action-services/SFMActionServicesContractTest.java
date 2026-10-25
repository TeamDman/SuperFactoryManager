package ca.teamdman.sfm.client.control;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Headless tests against real policy and scheduling classes; opens no transport. */
public final class SFMActionServicesContractTest {
    private static int checks;

    public static void main(String[] args) {
        var off = SFMActionServiceSettings.fromEnvironment(Map.of());
        check(!off.files() && !off.vox(), "both transports default off");
        var files = SFMActionServiceSettings.fromEnvironment(Map.of("SFM_ACTION_FILES", "true"));
        check(files.files() && !files.vox(), "files do not imply Vox");
        var both = SFMActionServiceSettings.fromEnvironment(Map.of("SFM_ACTION_FILES", "1", "SFM_ACTION_VOX", " TRUE "));
        check(both.files() && both.vox(), "independent flags can enable both");
        reject(() -> SFMActionServiceSettings.fromEnvironment(Map.of("SFM_ACTION_VOX", "yes")), "invalid environment fails closed");

        SFMActionArbiter arbiter = new SFMActionArbiter();
        long idle = arbiter.revision();
        Object first = new Object();
        arbiter.observePuppet(first, "first");
        String id = arbiter.puppetId();
        long revision = arbiter.revision();
        reject(() -> arbiter.requireMutation("file", null, 10), "running puppet rejects mutation");
        reject(() -> arbiter.pause("file", id, 10, 100, false), "in-flight puppet work cannot acknowledge handoff");
        check(arbiter.owner(10).isEmpty() && arbiter.mayTickPuppet(10), "rejected handoff leaves runner ownership unchanged");
        arbiter.pause("file", id, 10, 100, true);
        check(!arbiter.mayTickPuppet(11), "lease suspends puppet");
        arbiter.requireMutation("file", id, 11);
        reject(() -> arbiter.pause("vox", id, 12, 100, true), "other transport cannot steal lease");
        reject(() -> arbiter.requireMutation("vox", id, 12), "other transport cannot mutate");
        reject(() -> arbiter.resume("vox", id, 12), "other transport cannot resume owner");
        check(arbiter.mayTickPuppet(110), "expired controller cannot strand puppet");
        arbiter.pause("vox", id, 111, 100, true);
        arbiter.release("file");
        check(!arbiter.mayTickPuppet(112), "disabling unrelated transport preserves lease");
        arbiter.release("vox");
        check(arbiter.mayTickPuppet(112), "disabling owner releases lease");
        arbiter.observePuppet(new Object(), "second");
        reject(() -> arbiter.requireRevision(revision), "queued action rejects puppet switch");
        reject(() -> arbiter.pause("file", id, 113, 100, true), "exact old ID cannot pause replacement");
        arbiter.observePuppet(null, "");
        reject(() -> arbiter.requireRevision(idle), "idle-puppet-idle cannot reuse old admission");
        check(arbiter.owner(114).isEmpty(), "yield clears owner");

        SFMActionWorkTracker work = new SFMActionWorkTracker();
        ArrayDeque<Runnable> server = new ArrayDeque<>();
        work.execute(server::add, () -> check(!work.isIdle(), "running owned callback prevents handoff"));
        check(!work.isIdle(), "queued owned callback prevents handoff");
        server.remove().run();
        check(work.isIdle(), "drained callback allows cooperative handoff");
        try { work.execute(task -> { throw new java.util.concurrent.RejectedExecutionException(); }, () -> {}); }
        catch (java.util.concurrent.RejectedExecutionException expected) { }
        check(work.isIdle(), "executor rejection cannot strand handoff");
        reject(() -> work.execute(Runnable::run, () -> { throw new IllegalStateException(); }), "inline callback failure propagates");
        check(work.isIdle(), "inline failure releases tracked work exactly once");
        try { work.execute(task -> { throw new AssertionError("executor failed before dispatch"); }, () -> {}); }
        catch (AssertionError expected) { }
        check(work.isIdle(), "pre-dispatch executor error releases tracked work");

        ArrayDeque<Runnable> game = new ArrayDeque<>();
        ArrayDeque<Runnable> completion = new ArrayDeque<>();
        SFMClientThreadGate gate = new SFMClientThreadGate(game::add, completion::add, 2);
        AtomicInteger effects = new AtomicInteger();
        var file = gate.submit(effects::incrementAndGet);
        var vox = gate.submit(effects::incrementAndGet);
        var full = gate.submit(effects::incrementAndGet);
        reject(full::join, "one bounded queue applies to both transports");
        file.cancel(false);
        game.remove().run();
        game.remove().run();
        check(effects.get() == 1, "cancelled file request skips; Vox still executes");
        check(!vox.isDone(), "completion does not execute inline on game thread");
        completion.remove().run();
        check(vox.join() == 1, "remaining transport receives result");
        long admitted = arbiter.revision();
        var stale = gate.submit(() -> { arbiter.requireRevision(admitted); return effects.incrementAndGet(); });
        arbiter.observePuppet(new Object(), "third");
        arbiter.observePuppet(null, "");
        game.remove().run();
        completion.remove().run();
        reject(stale::join, "queued mutation rejects yield even after complete run");
        check(effects.get() == 1, "stale queued mutation has no effect");
        var shutdown = gate.submit(effects::incrementAndGet);
        gate.close();
        game.remove().run();
        completion.remove().run();
        reject(shutdown::join, "shutdown rejects admitted queued work");
        check(effects.get() == 1, "shutdown does not drain mutations");
        reject(() -> gate.submit(effects::incrementAndGet).join(), "closed dispatcher cannot admit work");
        System.out.println("PASS: " + checks + " policy/scheduling assertions; no game, socket or GPU");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void reject(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (IllegalStateException | IllegalArgumentException | CompletionException expected) { return; }
        throw new AssertionError(message);
    }
}
