package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.common.value.SFMTouchValue;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.Direction;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** One forward-only cursor. No replay, retry, item de-duplication, or packet-selected target. */
public final class TouchDisplayTerminalInputQueue {
    @FunctionalInterface public interface Reader { Optional<SFMClientInbox.Page> page(Optional<SFMClientInbox.Cursor> cursor); }
    public record Touch(double u, double v) {}
    private static final Set<String> FIELDS = Set.of("schema", "dimension", "x", "y", "z", "face", "u", "v", "action", "contentRevision", "state");
    private final TouchDisplayTerminalBinding binding;
    private final Reader reader;
    private SFMClientInbox.Cursor cursor;
    private boolean closed;
    private long attempted, rejected;
    private String status = "awaiting_inbox";

    public TouchDisplayTerminalInputQueue(TouchDisplayTerminalBinding binding, Reader reader) {
        this.binding = binding; this.reader = reader;
    }

    /** At most one incoming entry and one effect attempt per call. */
    public void pump(Direction face, Predicate<Touch> send) {
        if (closed) return;
        var observed = reader.page(Optional.ofNullable(cursor));
        if (observed.isEmpty()) { status = "awaiting_inbox"; return; }
        var page = observed.orElseThrow();
        if (cursor == null) {
            cursor = new SFMClientInbox.Cursor(page.nextCursor().session(), page.nextCursor().stream(), page.newestSequence());
            status = "ready"; // Explicit enabling starts after existing retained events.
            return;
        }
        if (page.continuity() != SFMClientInbox.Continuity.CONTIGUOUS) {
            closed = true; status = "input_continuity_lost"; return;
        }
        cursor = page.nextCursor(); // Every observed entry is consumed once, even when the effect is rejected.
        if (page.entries().isEmpty()) return;
        var touch = touch(binding, face, page.entries().get(0).value());
        if (touch.isEmpty()) { rejected++; status = "invalid_touch"; return; }
        attempted++;
        if (send.test(touch.orElseThrow())) status = "input_attempted";
        else { rejected++; status = "input_rejected"; }
    }

    public static Optional<Touch> touch(TouchDisplayTerminalBinding binding, Direction face, SFMValue value) {
        if (!(value instanceof SFMValue.ObjectValue object) || !object.fields().keySet().equals(FIELDS)) return Optional.empty();
        var f = object.fields();
        if (!SFMValue.of(SFMTouchValue.SCHEMA).equals(f.get("schema")) || !SFMValue.of("press").equals(f.get("action"))
                || !SFMValue.of(binding.owner().dimension().toString()).equals(f.get("dimension"))
                || !SFMValue.of(binding.display().getX()).equals(f.get("x")) || !SFMValue.of(binding.display().getY()).equals(f.get("y"))
                || !SFMValue.of(binding.display().getZ()).equals(f.get("z")) || !SFMValue.of(face.getName()).equals(f.get("face"))
                || !(f.get("contentRevision") instanceof SFMValue.LongValue revision) || revision.value() < 0) return Optional.empty();
        double u = coordinate(f.get("u")), v = coordinate(f.get("v"));
        return Double.isFinite(u) && Double.isFinite(v) && u >= 0 && u <= 1 && v >= 0 && v <= 1
                ? Optional.of(new Touch(u == 0 ? 0 : u, v == 0 ? 0 : v)) : Optional.empty();
    }

    private static double coordinate(SFMValue value) {
        return value instanceof SFMValue.DoubleValue number ? number.value()
                : value instanceof SFMValue.LongValue number ? number.value() : Double.NaN;
    }
    public long attempted() { return attempted; }
    public long rejected() { return rejected; }
    public String status() { return status; }
    public boolean closed() { return closed; }
}
