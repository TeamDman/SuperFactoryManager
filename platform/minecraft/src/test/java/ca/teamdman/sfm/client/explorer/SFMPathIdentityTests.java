package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMPathIdentityTests {
    @Test
    void canonicalValueIsRetainedWithoutChangingIdentityOrOrdering() {
        var segments = new ArrayList<>(List.of("C:", "space 雪", "a.java"));
        var path = new SFMPath(SFMPath.Kind.FILE, "file", "", segments, Optional.empty(), false);
        segments.set(2, "changed");
        String value = path.canonical();
        assertEquals("file:///C:/space%20%E9%9B%AA/a.java", value);
        assertSame(value, path.canonical());
        var parsed = SFMPath.parse(value);
        assertEquals(path, parsed);
        assertEquals(path.hashCode(), parsed.hashCode());
        assertEquals(0, path.compareTo(parsed));
        assertThrows(UnsupportedOperationException.class, () -> path.segments().add("x"));
        var values = List.of(path, SFMPath.parse("registry://minecraft/item/"),
                SFMPath.parse("selection://a@2"), SFMPath.parse("review://root/child/"));
        for (var left : values) for (var right : values) {
            assertEquals(Integer.signum(left.canonical().compareTo(right.canonical())),
                    Integer.signum(left.compareTo(right)));
        }
    }
}
