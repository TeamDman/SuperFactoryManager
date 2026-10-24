import ca.teamdman.sfm.client.search.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;

/** Tests the adapter contract with an explicit fake provider, never Nucleo parity. */
public class NucleoAdapterProbe {
    static final class Budget implements SFMExplorerNucleoAdapter.Budget {
        long remaining; final java.util.function.BooleanSupplier cancelled;
        Budget(long maximum) { this(maximum, () -> false); }
        Budget(long maximum, java.util.function.BooleanSupplier cancelled) { remaining = maximum; this.cancelled = cancelled; }
        public void spend(long work) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            if (work > remaining) throw new SFMExplorerNucleoAdapter.LimitExceeded("budget");
            remaining -= work;
        }
    }
    static int checks;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        var calls = new AtomicInteger();
        var adapter = new SFMExplorerNucleoAdapter("😀", true, (query, matchCase) -> {
            check(query.equals("😀") && matchCase, "case/query forwarded unchanged");
            return text -> { calls.incrementAndGet(); return text.equals("absent") ? null :
                    new SFMExplorerNucleoAdapter.NativeMatch(text.length(), List.of(new SFMExplorerNucleoAdapter.Span(1, 3))); };
        });
        var hit = adapter.match("a😀b", new Budget(100));
        check(hit.matches() && hit.utf16Spans().equals(List.of(new SFMExplorerNucleoAdapter.Span(1, 3))), "exact original UTF16 spans");
        check(adapter.match("x😀long", new Budget(100)).matches(), "result carries no native ranking for tree reordering");
        check(!adapter.match("absent", new Budget(100)).matches(), "null is absent");
        int before = calls.get();
        try { adapter.match("a😀b", new Budget(100, () -> true)); throw new AssertionError("cancel ignored"); }
        catch (CancellationException expected) { check(calls.get() == before, "cancel before provider work"); }
        try { adapter.match("a😀b", new Budget(1)); throw new AssertionError("budget ignored"); }
        catch (SFMExplorerNucleoAdapter.LimitExceeded expected) { check(calls.get() == before, "budget before provider work"); }
        try { adapter.match("x".repeat(SFMExplorerNucleoAdapter.MAX_CANDIDATE_CODEPOINTS * 2 + 1), new Budget(Long.MAX_VALUE)); throw new AssertionError("length ignored"); }
        catch (SFMExplorerNucleoAdapter.LimitExceeded expected) { check(calls.get() == before, "oversized candidate rejected before provider work"); }
        try { new SFMExplorerNucleoAdapter("x".repeat(SFMExplorerNucleoAdapter.MAX_QUERY_CODEPOINTS * 2 + 1), false,
                (q,c) -> { throw new AssertionError("oversized query reached provider"); }); throw new AssertionError("query length ignored"); }
        catch (SFMExplorerNucleoAdapter.LimitExceeded expected) { checks++; }
        var cancelled = new AtomicBoolean();
        var late = new SFMExplorerNucleoAdapter("q", false, (q,c) -> text -> { cancelled.set(true); return null; });
        try { late.match("q", new Budget(100, cancelled::get)); throw new AssertionError("late cancel ignored"); }
        catch (CancellationException expected) { checks++; }
        for (var bad : List.of(List.of(new SFMExplorerNucleoAdapter.Span(2,3)), List.of(new SFMExplorerNucleoAdapter.Span(1,3), new SFMExplorerNucleoAdapter.Span(1,3)))) {
            var invalid = new SFMExplorerNucleoAdapter("q", false, (q,c) -> text -> new SFMExplorerNucleoAdapter.NativeMatch(1, bad));
            try { invalid.match("a😀b", new Budget(100)); throw new AssertionError("invalid spans accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        try { new SFMExplorerNucleoAdapter("q", false, null); throw new AssertionError("missing provider silently substituted"); }
        catch (IllegalStateException expected) { checks++; }
        System.out.println("PASS " + checks + " adapter contract assertions; fake provider only, no Nucleo parity or live wiring claim");
    }
}
