package ca.teamdman.sfm;

import ca.teamdman.sfm.common.util.SFMDirections;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NullableDirectionEnumMapTests {
    @Test
    public void test() {
        var map = new SFMDirections.NullableDirectionEnumMap<String>();
        for (Direction direction : SFMDirections.DIRECTIONS_WITH_NULL) {
            map.put(direction, "test");
        }
        for (Direction direction : SFMDirections.DIRECTIONS_WITH_NULL) {
            assertEquals("test", map.get(direction));
        }
        map.forEach((direction, s) -> assertEquals("test", s));
        List<Object> keys = new ArrayList<>();
        map.forEach((direction, s) -> keys.add(direction));
        assertEquals(SFMDirections.DIRECTIONS_WITH_NULL.length, keys.size());
        map = new SFMDirections.NullableDirectionEnumMap<>();
        map.put(null, "hi");
        assertEquals("hi", map.get(null));
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            assertNull(map.get(direction));
        }
    }
}
